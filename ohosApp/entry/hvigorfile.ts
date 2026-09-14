import { hapTasks } from '@ohos/hvigor-ohos-plugin';

// 不启用 kuikly-ohos-compile-plugin：其自动编译任务名与 KMP 实际任务不符，
// 且强依赖未入库的 ohosApp/local.properties，会导致全新克隆构建失败。
// libshared.so 与 assets 均走手动链路（见 README「运行客户端 · 鸿蒙」）。
export default {
    system: hapTasks,  /* Built-in plugin of Hvigor. It cannot be modified. */
    plugins:[]         /* Custom plugin to extend the functionality of Hvigor. */
}