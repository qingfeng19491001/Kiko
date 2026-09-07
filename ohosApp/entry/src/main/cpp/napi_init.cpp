#include "napi/native_api.h"
#include "libshared_api.h"

// 鸿蒙端不需要 KLine 桥接（stockchat 的 KLine 在 OHOS 不做 native 落点）
// 仅暴露 initKuikly 给 ArkTS 端初始化 KMP 运行时

static auto SharedApi() {
    return libshared_symbols();
}

static napi_value InitKuikly(napi_env env, napi_callback_info) {
    napi_value result;
    napi_create_int32(env, SharedApi()->kotlin.root.initKuikly(), &result);
    return result;
}

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"initKuikly", nullptr, InitKuikly, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}
EXTERN_C_END

static napi_module demoModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "entry",
    .nm_priv = nullptr,
    .reserved = {0},
};

extern "C" __attribute__((constructor)) void RegisterEntryModule(void) {
    napi_module_register(&demoModule);
}
