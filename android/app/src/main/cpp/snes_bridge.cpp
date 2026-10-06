// JNI bridge between the Android app and the Snes9x libretro core.
//
// The core is linked statically into this library, so instead of loading it
// with dlopen we just call the retro_* functions directly and implement the
// handful of frontend callbacks the core needs.

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

bool g_initialized = false;
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

extern "C" {

JNIEXPORT void JNICALL
Java_com_snes9x_mobile_NativeBridge_init(JNIEnv *env, jclass, jstring system_dir, jstring save_dir)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_initialized)
        return;

    g_system_dir = jstring_to_string(env, system_dir);
    g_save_dir = jstring_to_string(env, save_dir);

    retro_set_environment(environment);
    retro_set_video_refresh(video_refresh);
    retro_set_audio_sample(audio_sample);
    retro_set_audio_sample_batch(audio_sample_batch);
    retro_set_input_poll(input_poll);
    retro_set_input_state(input_state);
    retro_init();

    g_audio.reserve(4096);
    g_initialized = true;
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_mobile_NativeBridge_loadGame(JNIEnv *env, jclass, jbyteArray rom, jstring name)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_initialized)
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
        retro_unload_game();

    g_game_loaded = retro_load_game(&info);
    if (g_game_loaded)
        retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);

    g_frame_ready = false;
    g_audio.clear();
    return g_game_loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_com_snes9x_mobile_NativeBridge_getFps(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    retro_get_system_av_info(&av);
    return av.timing.fps;
}

JNIEXPORT jint JNICALL
Java_com_snes9x_mobile_NativeBridge_getSampleRate(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    retro_get_system_av_info(&av);
    return (jint)av.timing.sample_rate;
}

JNIEXPORT jfloat JNICALL
Java_com_snes9x_mobile_NativeBridge_getAspectRatio(JNIEnv *, jclass)
{
    struct retro_system_av_info av = {};
    retro_get_system_av_info(&av);
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
    retro_run();

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
        retro_reset();
}

JNIEXPORT jbyteArray JNICALL
Java_com_snes9x_mobile_NativeBridge_saveState(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded)
        return nullptr;

    size_t size = retro_serialize_size();
    if (size == 0)
        return nullptr;
    std::vector<uint8_t> buffer(size);
    if (!retro_serialize(buffer.data(), size))
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
    return retro_unserialize(buffer.data(), buffer.size()) ? JNI_TRUE : JNI_FALSE;
}

// Battery-backed cartridge RAM (the in-game save), or null if the cartridge
// has none.
JNIEXPORT jbyteArray JNICALL
Java_com_snes9x_mobile_NativeBridge_getSaveRam(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_game_loaded)
        return nullptr;

    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
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

    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || !data)
        return;

    jsize length = env->GetArrayLength(sram);
    if ((size_t)length < size)
        size = length;
    env->GetByteArrayRegion(sram, 0, (jsize)size, static_cast<jbyte *>(data));
}

} // extern "C"
