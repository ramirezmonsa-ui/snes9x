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

add_library(snes9x SHARED ${CORE_SOURCES} snes_bridge.cpp)

target_include_directories(snes9x PRIVATE
    ${CORE_DIR}/libretro
    ${CORE_DIR}/libretro/libretro-common/include
    ${CORE_DIR}
    ${CORE_DIR}/apu
    ${CORE_DIR}/apu/bapu
)

target_compile_definitions(snes9x PRIVATE
    __LIBRETRO__
    HAVE_STRINGS_H
    RIGHTSHIFT_IS_SAR
)

set_target_properties(snes9x PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)

# The emulator is unplayable without optimizations, so build it optimized
# even in debug builds.
target_compile_options(snes9x PRIVATE -O2 -fno-strict-aliasing -w)

if(ANDROID)
    target_compile_definitions(snes9x PRIVATE ANDROID)
    target_link_libraries(snes9x log)
else()
    # Host build, only used to check that everything compiles and links.
    find_package(JNI REQUIRED)
    target_include_directories(snes9x PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_options(snes9x PRIVATE -Wl,--no-undefined)
endif()
