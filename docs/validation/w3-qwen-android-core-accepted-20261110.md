# Android Qwen load-only core 限定验收

免费hy3交付的最终core已按原字节集成，SHA256
`0bac095d4cd8151257900827bfc3e013a7b2d0b2bebd92b674b06e39c98f999c`。
真实Kotlin编译通过，17组host故障检查、63断言通过，六份默认模型size/SHA逐项与冻结manifest一致。
这是文件校验/计数/清理逻辑的验收，真实JNI、Android构建/装机/模型加载均未运行。

覆盖正常构造和一次释放、两次准入gate、构造前持久化失败、构造/释放异常、构造后持久化失败、
退前台、双异常首错保留、最终写失败、计时异常仍释放/保留真实返回、构造异常不被计时掩盖，
以及真实磁盘hash/size/缺失/目录、非法路径与各层symlink拒绝。
对缺失行为的测试负向控制在实现前编译成功、14组全部失败，未作为真实候选实现使用。

保留原失败：首稿`NoSuchFileException(String)`错误匹配Kotlin File构造而编译失败；
首修正编译通过但15组成功、2个clock用例错误报告completed。
原因是`s.copy(duration=elapsed())`先取旧receiver，elapsed记录的error字段随后被旧copy覆盖。
外部助手将时间计算拆为局部值后再从当前状态copy，原17组检查全部通过，没有弱化断言。
原稿/原编译输出/实际失败状态及各次helper原件均在本机证据目录保留。

[验收收据](w3-qwen-android-core-accepted-20261110.json)、
[父故障检查](../../probes/android-qwen-load/check_core.py)。
下一步先完成wrapper的实际配置/故障验收，再构建/lint与实包核验；不把core通过当手机通过。
