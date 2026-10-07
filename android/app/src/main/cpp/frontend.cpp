// JNI bridge between the Android app and the libretro cores (Snes9x for
// Super Nintendo, mGBA for Game Boy / Game Boy Advance).
//
// Each core is its own shared library. They all export the same retro_*
// functions, so they are opened with dlopen and called through the Core
// table below. Only one core is loaded at a time.

#include <dlfcn.h>
#include <jni.h>

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "libretro.h"

#define LOG_TAG "Snes9xBridge"

namespace {

// The retro_* functions of the loaded core.
struct Core {
    void *handle = nullptr;
    std::string name;

    void (*set_environment)(retro_environment_t);
    void (*set_video_refresh)(retro_video_refresh_t);
    void (*set_audio_sample)(retro_audio_sample_t);
    void (*set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*set_input_poll)(retro_input_poll_t);
    void (*set_input_state)(retro_input_state_t);
    void (*init)(void);
    void (*deinit)(void);
    void (*get_system_av_info)(struct retro_system_av_info *);
    void (*set_controller_port_device)(unsigned, unsigned);
    void (*reset)(void);
    void (*run)(void);
    size_t (*serialize_size)(void);
    bool (*serialize)(void *, size_t);
    bool (*unserialize)(const void *, size_t);
    bool (*load_game)(const struct retro_game_info *);
    void (*unload_game)(void);
    void *(*get_memory_data)(unsigned);
    size_t (*get_memory_size)(unsigned);
};

Core g_core;

// Largest frame the core can produce (512x478 hi-res interlaced, or the
// 602 pixel wide NTSC filter output), in RGB565.
constexpr int kMaxWidth = 604;
constexpr int kMaxHeight = 480;

std::mutex g_lock;

std::string g_system_dir;
std::string g_save_dir;

// Core option defaults, parsed from RETRO_ENVIRONMENT_SET_VARIABLES.
std::map<std::string, std::string> g_options;

// Last video frame, packed tightly (pitch == width * 2).
std::vector<uint16_t> g_frame(kMaxWidth * kMaxHeight);
int g_frame_width = 0;
int g_frame_height = 0;
bool g_frame_ready = false;

// Interleaved stereo samples produced during the last retro_run().
std::vector<int16_t> g_audio;

// Bitmask of RETRO_DEVICE_ID_JOYPAD_* buttons pressed on player 1.
volatile int g_buttons = 0;

bool g_game_loaded = false;

void log_message(int prio, const char *fmt, va_list args)
{
#ifdef __ANDROID__
    __android_log_vprint(prio, LOG_TAG, fmt, args);
#else
    (void)prio;
    vfprintf(stderr, fmt, args);
#endif
}

void core_log(enum retro_log_level level, const char *fmt, ...)
{
    int prio;
#ifdef __ANDROID__
    switch (level)
    {
        case RETRO_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO:  prio = ANDROID_LOG_INFO;  break;
        case RETRO_LOG_WARN:  prio = ANDROID_LOG_WARN;  break;
        default:              prio = ANDROID_LOG_ERROR; break;
    }
#else
    prio = level;
#endif
    va_list args;
    va_start(args, fmt);
    log_message(prio, fmt, args);
    va_end(args);
}

// Values look like "Description; default|other|other". Keep the default.
void register_variables(const struct retro_variable *vars)
{
    for (; vars && vars->key; vars++)
    {
        if (!vars->value)
            continue;
        const char *start = strchr(vars->value, ';');
        start = start ? start + 1 : vars->value;
        while (*start == ' ')
            start++;
        const char *end = strchr(start, '|');
        g_options[vars->key] = end ? std::string(start, end - start) : std::string(start);
    }
}

bool environment(unsigned cmd, void *data)
{
    switch (cmd)
    {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            static_cast<struct retro_log_callback *>(data)->log = core_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = g_system_dir.c_str();
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = g_save_dir.c_str();
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            return *static_cast<const enum retro_pixel_format *>(data) == RETRO_PIXEL_FORMAT_RGB565;

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            // Version 0 makes the core fall back to SET_VARIABLES, which is
            // the simplest format to parse.
            *static_cast<unsigned *>(data) = 0;
            return true;

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            register_variables(static_cast<const struct retro_variable *>(data));
            return true;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
        {
            auto *var = static_cast<struct retro_variable *>(data);
            auto it = g_options.find(var->key);
            if (it == g_options.end())
                return false;
            var->value = it->second.c_str();
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool *>(data) = false;
            return true;

        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            return true;

        default:
            return false;
    }
}

void video_refresh(const void *data, unsigned width, unsigned height, size_t pitch)
{
    // data == NULL means "duplicate the previous frame".
    if (!data || width == 0 || height == 0)
        return;
    if (width > (unsigned)kMaxWidth)
        width = kMaxWidth;
    if (height > (unsigned)kMaxHeight)
        height = kMaxHeight;

    const uint8_t *src = static_cast<const uint8_t *>(data);
    uint16_t *dst = g_frame.data();
    for (unsigned y = 0; y < height; y++)
        memcpy(dst + y * width, src + y * pitch, width * sizeof(uint16_t));

    g_frame_width = width;
    g_frame_height = height;
    g_frame_ready = true;
}

void audio_sample(int16_t left, int16_t right)
{
    g_audio.push_back(left);
    g_audio.push_back(right);
}

size_t audio_sample_batch(const int16_t *data, size_t frames)
{
    g_audio.insert(g_audio.end(), data, data + frames * 2);
    return frames;
}

void input_poll()
{
}

int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id)
{
    if (port != 0 || index != 0 || device != RETRO_DEVICE_JOYPAD)
        return 0;
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK)
        return (int16_t)g_buttons;
    return (g_buttons >> id) & 1;
}

std::string jstring_to_string(JNIEnv *env, jstring str)
{
    if (!str)
        return std::string();
    const char *chars = env->GetStringUTFChars(str, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(str, chars);
    return result;
}

} // namespace

// Opens a core library, first by name (the app's library folder is on the
// search path from Android 7) and then by full path.
static void *open_library(const std::string &dir, const std::string &file)
{
    void *handle = dlopen(file.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle)
        handle = dlopen((dir + "/" + file).c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle)
        core_log(RETRO_LOG_ERROR, "dlopen %s: %s\n", file.c_str(), dlerror());
    return handle;
}

template <typename T>
static bool find_symbol(void *handle, const char *name, T &out)
{
    out = reinterpret_cast<T>(dlsym(handle, name));
    if (!out)
        core_log(RETRO_LOG_ERROR, "missing %s\n", name);
    return out != nullptr;
}

static void close_core()
{
    if (!g_core.handle)
        return;
    if (g_game_loaded)
        g_core.unload_game();
    g_game_loaded = false;
    g_core.deinit();
    dlclose(g_core.handle);
    g_core = Core();
    g_options.clear();
}

static bool open_core(const std::string &dir, const std::string &file)
{
    void *handle = open_library(dir, file);
    if (!handle)
        return false;

    Core core;
    core.handle = handle;
    core.name = file;
    bool ok = find_symbol(handle, "retro_set_environment", core.set_environment)
        && find_symbol(handle, "retro_set_video_refresh", core.set_video_refresh)
        && find_symbol(handle, "retro_set_audio_sample", core.set_audio_sample)
        && find_symbol(handle, "retro_set_audio_sample_batch", core.set_audio_sample_batch)
        && find_symbol(handle, "retro_set_input_poll", core.set_input_poll)
        && find_symbol(handle, "retro_set_input_state", core.set_input_state)
        && find_symbol(handle, "retro_init", core.init)
        && find_symbol(handle, "retro_deinit", core.deinit)
        && find_symbol(handle, "retro_get_system_av_info", core.get_system_av_info)
        && find_symbol(handle, "retro_set_controller_port_device", core.set_controller_port_device)
        && find_symbol(handle, "retro_reset", core.reset)
        && find_symbol(handle, "retro_run", core.run)
        && find_symbol(handle, "retro_serialize_size", core.serialize_size)
        && find_symbol(handle, "retro_serialize", core.serialize)
        && find_symbol(handle, "retro_unserialize", core.unserialize)
        && find_symbol(handle, "retro_load_game", core.load_game)
        && find_symbol(handle, "retro_unload_game", core.unload_game)
        && find_symbol(handle, "retro_get_memory_data", core.get_memory_data)
        && find_symbol(handle, "retro_get_memory_size", core.get_memory_size);
    if (!ok)
    {
        dlclose(handle);
        return false;
    }

    g_core = core;
    g_core.set_environment(environment);
    g_core.set_video_refresh(video_refresh);
    g_core.set_audio_sample(audio_sample);
    g_core.set_audio_sample_batch(audio_sample_batch);
    g_core.set_input_poll(input_poll);
    g_core.set_input_state(input_state);
    g_core.init();
    return true;
}

extern "C" {

// Makes `core` (a library file name such as "libsnes9x_libretro.so") the
// active core, switching from the current one if needed.
JNIEXPORT jboolean JNICALL
Java_com_snes9x_mobile_NativeBridge_init(JNIEnv *env, jclass, jstring library_dir, jstring core,
                                         jstring system_dir, jstring save_dir)
{
    std::lock_guard<std::mutex> guard(g_lock);
    std::string file = jstring_to_string(env, core);
    if (g_core.handle && g_core.name == file)
        return JNI_TRUE;

    close_core();
    g_system_dir = jstring_to_string(env, system_dir);
    g_save_dir = jstring_to_string(env, save_dir);
    g_audio.reserve(8192);
    return open_core(jstring_to_string(env, library_dir), file) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_mobile_NativeBridge_loadGame(JNIEnv *env, jclass, jbyteArray rom, jstring name)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_core.handle)
        return JNI_FALSE;

    // The core reads the save directory and ROM name from the path; the data
    // itself comes from memory.
    std::string path = g_save_dir + "/" + jstring_to_string(env, name);

    jsize size = env->GetArrayLength(rom);
    // The core peeks at the header at 0xFFC0 before checking the ROM size,
    // so keep at least 64KB of (zeroed) memory behind small files.
    std::vector<uint8_t> buffer(size < 0x10000 ? 0x10000 : size);
    env->GetByteArrayRegion(rom, 0, size, reinterpret_cast<jbyte *>(buffer.data()));

    struct retro_game_info info = {};
    info.path = path.c_str();
    info.data = buffer.data();
    info.size = size;

    if (g_game_loaded)
        g_core.unload_game();

    g_game_loaded = g_core.load_game(&info);
    if (g_game_loaded)
        g_core.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);

    g_frame_ready = false;
    g_audio.clear();
    return g_game_loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_com_snes9x_mobile_NativeBridge_getFps(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    if (!g_core.handle)
        return 0;
    g_core.get_system_av_info(&av);
    return av.timing.fps;
}

JNIEXPORT jint JNICALL
Java_com_snes9x_mobile_NativeBridge_getSampleRate(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    if (!g_core.handle)
        return 0;
    g_core.get_system_av_info(&av);
    return (jint)av.timing.sample_rate;
}

JNIEXPORT jfloat JNICALL
Java_com_snes9x_mobile_NativeBridge_getAspectRatio(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    if (!g_core.handle)
        return 0;
    g_core.get_system_av_info(&av);
    // 0 means "use the frame size", as the libretro API defines it.
    if (av.geometry.aspect_ratio <= 0 && av.geometry.base_height > 0)
        return (jfloat)av.geometry.base_width / av.geometry.base_height;
    return av.geometry.aspect_ratio;
}

JNIEXPORT void JNICALL
Java_com_snes9x_mobile_NativeBridge_setButtons(JNIEnv *, jclass, jint mask)
{
    g_buttons = mask;
}

// Runs one frame. The frame (RGB565) goes into `video`, a direct buffer of at
// least kMaxWidth * kMaxHeight * 2 bytes, and the audio samples into `audio`.
// Returns the number of int16 samples written, or -1 if no game is loaded.
// The frame size is written to size[0] and size[1] (0 if no new frame).
JNIEXPORT jint JNICALL
Java_com_snes9x_mobile_NativeBridge_runFrame(JNIEnv *env, jclass, jobject video, jshortArray audio, jintArray size)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded)
        return -1;

    g_audio.clear();
    g_frame_ready = false;
    g_core.run();

    jint dims[2] = { 0, 0 };
    if (g_frame_ready)
    {
        void *dst = env->GetDirectBufferAddress(video);
        jlong capacity = env->GetDirectBufferCapacity(video);
        jlong bytes = (jlong)g_frame_width * g_frame_height * 2;
        if (dst && capacity >= bytes)
        {
            memcpy(dst, g_frame.data(), bytes);
            dims[0] = g_frame_width;
            dims[1] = g_frame_height;
        }
    }
    env->SetIntArrayRegion(size, 0, 2, dims);

    jsize count = (jsize)g_audio.size();
    jsize capacity = env->GetArrayLength(audio);
    if (count > capacity)
        count = capacity;
    if (count > 0)
        env->SetShortArrayRegion(audio, 0, count, g_audio.data());
    return count;
}

JNIEXPORT void JNICALL
Java_com_snes9x_mobile_NativeBridge_reset(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_game_loaded)
        g_core.reset();
}

JNIEXPORT jbyteArray JNICALL
Java_com_snes9x_mobile_NativeBridge_saveState(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded)
        return nullptr;

    size_t size = g_core.serialize_size();
    if (size == 0)
        return nullptr;
    std::vector<uint8_t> buffer(size);
    if (!g_core.serialize(buffer.data(), size))
        return nullptr;

    jbyteArray result = env->NewByteArray((jsize)size);
    env->SetByteArrayRegion(result, 0, (jsize)size, reinterpret_cast<const jbyte *>(buffer.data()));
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_mobile_NativeBridge_loadState(JNIEnv *env, jclass, jbyteArray state)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded || !state)
        return JNI_FALSE;

    jsize size = env->GetArrayLength(state);
    std::vector<uint8_t> buffer(size);
    env->GetByteArrayRegion(state, 0, size, reinterpret_cast<jbyte *>(buffer.data()));
    return g_core.unserialize(buffer.data(), buffer.size()) ? JNI_TRUE : JNI_FALSE;
}

// Battery-backed cartridge RAM (the in-game save), or null if the cartridge
// has none.
JNIEXPORT jbyteArray JNICALL
Java_com_snes9x_mobile_NativeBridge_getSaveRam(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded)
        return nullptr;

    size_t size = g_core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = g_core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || !data)
        return nullptr;

    jbyteArray result = env->NewByteArray((jsize)size);
    env->SetByteArrayRegion(result, 0, (jsize)size, static_cast<const jbyte *>(data));
    return result;
}

JNIEXPORT void JNICALL
Java_com_snes9x_mobile_NativeBridge_setSaveRam(JNIEnv *env, jclass, jbyteArray sram)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded || !sram)
        return;

    size_t size = g_core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = g_core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || !data)
        return;

    jsize length = env->GetArrayLength(sram);
    if ((size_t)length < size)
        size = length;
    env->GetByteArrayRegion(sram, 0, (jsize)size, static_cast<jbyte *>(data));
}

} // extern "C"
