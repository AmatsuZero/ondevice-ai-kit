# W3 NDK 离线正文恢复 overlay

原134行声明、76正文/58缺失历史文件与原失败记录保持不变。本目录以精确Git blob复用既有r27d正文，新增r30的18/58/59三行覆盖；合并79行有正文、55行缺正文。此处不复制三份canonical补丁正文，路径及摘要见recovery-overlay.json。

parent-acceptance.json记录523项离线检查；runtime-patch-classification.json是hy3对完整正文的分类，hy3-delivery-receipts.json保留两个终态及限定复核/精确修正。原始prompt/raw及验收脚本保留在.omx/artifacts/w3-ndk-offline-recovery-20261116T0710/，不含新构建或设备证据。

只验证所保留目录/正文对象及原请求收据对应关系；没有commit对象/签名验证、可信构建、历史apply、最终native组成或发布接受。W3/R4/R5/P1–P4仍open，完整目标不缩减。
