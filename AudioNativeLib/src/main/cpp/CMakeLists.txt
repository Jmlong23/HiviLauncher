cmake_minimum_required(VERSION 3.4.1)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library( # Sets the name of the library.
        hivinative

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        SerialPort.c
        alsaRecorder.c
        driver.cpp

        himusic.cpp
)



# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

#find_library( # Sets the name of the path variable.
#        tinyalsa-lib
#
#        # Specifies the name of the NDK library that
#        # you want CMake to locate.
#        tinyalsa)



add_library(
        crossdevice-lib
        SHARED
        IMPORTED)

set_target_properties(
        crossdevice-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libcrossdevice.so)


add_library(
        HisightSink-lib
        SHARED
        IMPORTED)

set_target_properties(
        HisightSink-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libHisightSink.so)


# Link TinyALSA shared library
add_library(tinyalsa SHARED IMPORTED)
set_target_properties(
        tinyalsa PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libtinyalsa.so
)

add_library(
        dmsdpsec-lib
        SHARED
        IMPORTED)

set_target_properties(
        dmsdpsec-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libdmsdpsec.so)


add_library(
        crypto-lib
        SHARED
        IMPORTED)

set_target_properties(
        crypto-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libcrypto.so)


add_library(
        c++-lib
        SHARED
        IMPORTED)

set_target_properties(
        c++-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libc++.so)


add_library(
        mdnssd-lib
        SHARED
        IMPORTED)

set_target_properties(
        mdnssd-lib
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libmdnssd.so)

add_library( # Sets the name of the library.
        dmsdpaudiohandler

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        dmsdp_audio_handler.cpp
)

target_link_libraries(dmsdpaudiohandler PRIVATE
        ${log-lib}
        OpenSLES)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        hivinative

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})


target_link_libraries( # Specifies the target library.
        hivinative

        # Links the target library to the third_library.
        dmsdpaudiohandler
        tinyalsa
        c++-lib
        mdnssd-lib
        crypto-lib
        dmsdpsec-lib
        HisightSink-lib
        crossdevice-lib)