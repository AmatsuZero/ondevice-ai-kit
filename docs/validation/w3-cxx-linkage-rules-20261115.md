# W3 C++ linkage rules 增量审查

结论：接受受限增量；它把 CMake 3.31.6 与固定 NDK 源文本绑定到日志，给出条件化 STL 默认规则。不是 W3 完成，P1-P4/R4/R5/W3 仍开放，关闭仍受覆盖度/可信度审查。证据根：[README](../../probes/sherpa-runtime-materials/cxx-linkage-rules-20261115/README.md)；前置：[provenance](w3-ndk-runtime-provenance-20261115.md)、[closure scope](w3-closure-scope-20261115.md)。

观察：CMake annotated v3.31.6 tag -> 859ca5c4d7396a011a462878179bc173e1283731，blob hashes 重算；NDK r27d 99de7583356a3ae1af2686e0659b646bdacb88c7、r30 c0a0a3e6712eeb645090766058cda091da0fdf70 的 tree/child 核验。父级观察：30 条官方留存源/元数据路径去重 22 文件；最终 6 tree/18 retained-child edge（此前 16，未含 exports.cmake）。5 份日志绑定 3.31.6（ORT 经 CMakeFiles/3.31.6 与版本输出，Sherpa 显式）。两个原 CI ZIP 仅 20/1 非目录成员，无 cache/link.txt/response/compile_commands/build.ninja；4 ORT + 12 Sherpa 链接进度行非完整命令，未恢复新历史链接调用/cache/artifact。

| 组件 | 日志/命令观察 | 条件化源推导 | 边界 |
|---|---|---|---|
| Sherpa | NDK 30.0.16248370、android-21、`BUILD_SHARED_LIBS=ON`；arm64/v7a/x86_64 显式 `SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF`，x86 未显式设置；打印命令无 `ANDROID_USE_LEGACY_TOOLCHAIN_FILE`，也无显式 `ANDROID_STL`/`CMAKE_ANDROID_STL_TYPE`；CXX flags 未见 `-stdlib=libc++` | 固定 r30 `android.toolchain` 第47-57行默认 legacy=true，include `android-legacy.toolchain.cmake` 后 return；不套用 CMake Common 非 legacy 分支。若无 `ANDROID_STL` 覆盖，legacy 默认 `c++_static` 并向 `ANDROID_LINKER_FLAGS` 加 `-static-libstdc++` | `SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY` 只在 NOT `BUILD_SHARED_LIBS` 且 Linux 影响 GNU libstdc++/libgcc，不是 Android STL 选择；历史 cache/覆盖与实际最终链接不可用 |
| ORT | NDK 27.3.13750724、android-27、显式 legacy=false、`onnxruntime_BUILD_SHARED_LIB=ON`、LTO=ON；打印命令/日志无显式 `ANDROID_STL`/`CMAKE_ANDROID_STL_TYPE`；四个 ABI 的 `CMAKE_CXX_FLAGS` 含 `-stdlib=libc++` | CMake 3.31.6 Common：无 truthy `CMAKE_ANDROID_STL_TYPE` 且无 legacy GNU STL 目录时默认 `c++_static`；unified-toolchain CXX 分支追加 `-stdlib=libc++` 与 `-static-libstdc++` | `-stdlib=libc++` 是编译配置，不单独证明最终链接；无 cache/link.txt/response，亦不证明 archive 抽取或最终留存 |

精确假设：日志属于所比对构建；无 cache、命令行、target property 或兼容变量设置其它 STL；源修订与 unified/legacy 路径对应；清单外覆盖未知。所查 hooks/flags 无 STL 选择覆盖，Determine post 仅复制 exports 兼容变量，不等于全部覆盖已解或完整最终链接。NDK legacy 规则仅在 NOT `ANDROID_STL` 时默认 `c++_static` 并加 `-static-libstdc++`。

本增量解决版本漂移、hook/flags 初筛、CMake/NDK 默认分支文本定位及 Sherpa legacy、ORT legacy=false 的语义边界。仍缺历史覆盖、完整链接命令、ELF/archive 抽取与最终留存；tree/blob 不等于签名、可信构建或完整留存，DT_NEEDED 无 `libc++_shared` 也不排除静态 C++/隐藏/内联代码。关闭合同接受组合证据、无需唯一密码学或机器码证明，但须审查覆盖/可信度。本增量只新增少量官方公共源文本，未做 CMake 求值/编译/构建、ELF 重扫、设备调用、模型或运行时下载、生产 notice 编辑；原 63 个 notice assets 未变。父投影早期 `KeyError: logPath` 为字段映射问题，已按实际日志行修正，不是编译器失败，也未启动 build/helper/device。