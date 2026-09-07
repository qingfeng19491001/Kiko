# the minimum version of CMake.
cmake_minimum_required(VERSION 3.4.1)
project(StockChatEntry)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

if (DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif ()

include_directories(${NATIVERENDER_ROOT_PATH}
        ${NATIVERENDER_ROOT_PATH}/include)

# Kuikly 渲染器 (.so) — 由 @kuikly-open/render 的 ohpm 拉取
add_library(kuikly_render SHARED IMPORTED)
set_target_properties(kuikly_render
        PROPERTIES
        IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../oh_modules/@kuikly-open/render/libs/${OHOS_ARCH}/libkuikly.so)

# 业务代码 (.so) — 由 :shared:linkDebugSharedOhosArm64 产出，脚本拷贝到 entry/libs/${OHOS_ARCH}/
add_library(kuikly_shared SHARED IMPORTED)
set_target_properties(kuikly_shared
        PROPERTIES
        IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/${OHOS_ARCH}/libshared.so)

add_library(entry SHARED napi_init.cpp)
target_link_libraries(entry PUBLIC libace_napi.z.so libhilog_ndk.z.so kuikly_shared kuikly_render)
