cmake_minimum_required(VERSION 3.18)
project(snes9x_android C CXX)

# Root of the Snes9x source tree.
set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../..)
get_filename_component(CORE_DIR ${CORE_DIR} ABSOLUTE)

# Same source list as libretro/Makefile.common.
set(CORE_SOURCES
    ${CORE_DIR}/filter/snes_ntsc.c
    ${CORE_DIR}/apu/apu.cpp
    ${CORE_DIR}/apu/bapu/dsp/sdsp.cpp
    ${CORE_DIR}/apu/bapu/smp/smp.cpp
    ${CORE_DIR}/apu/bapu/smp/smp_state.cpp
    ${CORE_DIR}/bsx.cpp
    ${CORE_DIR}/c4.cpp
    ${CORE_DIR}/c4emu.cpp
    ${CORE_DIR}/cheats.cpp
    ${CORE_DIR}/cheats2.cpp
    ${CORE_DIR}/clip.cpp
    ${CORE_DIR}/conffile.cpp
    ${CORE_DIR}/controls.cpp
    ${CORE_DIR}/cpu.cpp
    ${CORE_DIR}/cpuexec.cpp
    ${CORE_DIR}/cpuops.cpp
    ${CORE_DIR}/crosshairs.cpp
    ${CORE_DIR}/dma.cpp
    ${CORE_DIR}/dsp.cpp
    ${CORE_DIR}/dsp1.cpp
    ${CORE_DIR}/dsp2.cpp
    ${CORE_DIR}/dsp3.cpp
    ${CORE_DIR}/dsp4.cpp
    ${CORE_DIR}/fxinst.cpp
    ${CORE_DIR}/fxemu.cpp
    ${CORE_DIR}/gfx.cpp
    ${CORE_DIR}/globals.cpp
    ${CORE_DIR}/memmap.cpp
    ${CORE_DIR}/obc1.cpp
    ${CORE_DIR}/msu1.cpp
    ${CORE_DIR}/ppu.cpp
    ${CORE_DIR}/stream.cpp
    ${CORE_DIR}/sa1.cpp
    ${CORE_DIR}/sa1cpu.cpp
    ${CORE_DIR}/screenshot.cpp
    ${CORE_DIR}/sdd1.cpp
    ${CORE_DIR}/sdd1emu.cpp
    ${CORE_DIR}/seta.cpp
    ${CORE_DIR}/seta010.cpp
    ${CORE_DIR}/seta011.cpp
    ${CORE_DIR}/seta018.cpp
    ${CORE_DIR}/snapshot.cpp
    ${CORE_DIR}/snes9x.cpp
    ${CORE_DIR}/spc7110.cpp
    ${CORE_DIR}/srtc.cpp
    ${CORE_DIR}/tile.cpp
    ${CORE_DIR}/tileimpl-n1x1.cpp
    ${CORE_DIR}/tileimpl-n2x1.cpp
    ${CORE_DIR}/tileimpl-h2x1.cpp
    ${CORE_DIR}/sha256.cpp
    ${CORE_DIR}/bml.cpp
    ${CORE_DIR}/movie.cpp
    ${CORE_DIR}/fscompat.cpp
    ${CORE_DIR}/libretro/libretro.cpp
)

# Newer phones use 16 KB memory pages and can't load native libraries built
# for 4 KB pages. This applies to every library below, mGBA included.
if(ANDROID)
    add_link_options(-Wl,-z,max-page-size=16384)
endif()

# --- Snes9x (Super Nintendo) -------------------------------------------------

add_library(snes9x_libretro SHARED ${CORE_SOURCES})

target_include_directories(snes9x_libretro PRIVATE
    ${CORE_DIR}/libretro
    ${CORE_DIR}/libretro/libretro-common/include
    ${CORE_DIR}
    ${CORE_DIR}/apu
    ${CORE_DIR}/apu/bapu
)

target_compile_definitions(snes9x_libretro PRIVATE
    __LIBRETRO__
    HAVE_STRINGS_H
    RIGHTSHIFT_IS_SAR
)

set_target_properties(snes9x_libretro PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)

# The emulator is unplayable without optimizations, so build it optimized
# even in debug builds.
target_compile_options(snes9x_libretro PRIVATE -O2 -fno-strict-aliasing -w)

# Export only the retro_* functions.
target_link_options(snes9x_libretro PRIVATE
    -Wl,--version-script=${CORE_DIR}/libretro/link.T)

if(ANDROID)
    target_compile_definitions(snes9x_libretro PRIVATE ANDROID)
endif()

# --- mGBA (Game Boy, Game Boy Color, Game Boy Advance) -----------------------

set(MGBA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/mgba)
set(BUILD_LIBRETRO ON CACHE BOOL "" FORCE)
foreach(option BUILD_QT BUILD_SDL BUILD_GL BUILD_GLES2 BUILD_GLES3 USE_EPOXY
        BUILD_SUITE BUILD_CINEMA BUILD_HEADLESS BUILD_PYTHON BUILD_UPDATER
        BUILD_EXAMPLE BUILD_PERF BUILD_TEST BUILD_STATIC BUILD_SHARED
        USE_DEBUGGERS USE_GDB_STUB USE_EDITLINE USE_FFMPEG USE_ZLIB USE_MINIZIP
        USE_PNG USE_LIBZIP USE_SQLITE3 USE_ELF USE_LUA USE_JSON_C USE_FREETYPE
        USE_LZMA USE_DISCORD_RPC ENABLE_SCRIPTING)
    set(${option} OFF CACHE BOOL "" FORCE)
endforeach()
set(SKIP_LIBRARY ON CACHE BOOL "" FORCE)
add_subdirectory(${MGBA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/mgba EXCLUDE_FROM_ALL)
# mGBA names it "mgba_libretro.so"; Android only packages files named lib*.so.
set_target_properties(mgba_libretro PROPERTIES PREFIX "lib")
target_compile_options(mgba_libretro PRIVATE -O2 -w)

# --- Frontend (JNI bridge) ----------------------------------------------------

add_library(frontend SHARED frontend.cpp)
target_include_directories(frontend PRIVATE ${CORE_DIR}/libretro)
set_target_properties(frontend PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_compile_options(frontend PRIVATE -O2)
# The cores are loaded at runtime, but list them as dependencies so they are
# always built and packaged.
add_dependencies(frontend snes9x_libretro mgba_libretro)
target_link_libraries(frontend ${CMAKE_DL_LIBS} z)

if(ANDROID)
    target_link_libraries(frontend log)
else()
    # Host build, only used to check that everything compiles and works.
    find_package(JNI REQUIRED)
    target_include_directories(frontend PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_options(frontend PRIVATE -Wl,--no-undefined)
endif()
