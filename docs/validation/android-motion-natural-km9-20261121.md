# KM9：低清实拍视频的播放超分盲评

这轮要回答的是：在同一台 KM9 上正常播放低清实拍视频，观看者偏好原画还是 Spatial？沿用[上一轮的盲评方法](android-motion-pairs-km9-20261121.md)，只把合成图板换成公开实拍。三段低清输入由本次裁切或缩放、重编码得到，不是客户业务录像。

| 顺序 | 片段与看点 | 低清输入与来源 |
|---|---|---|
| 1 | 电影人物：脸部、头发和衣服边缘 | 《Tears of Steel》51–57 秒，居中裁切至 16:9 后压为 640×360，24 转 30 fps。(CC) Blender Foundation \| [mango.blender.org](https://mango.blender.org/sharing/)，[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) |
| 2 | 瀑布：水流与岩石交界 | [Kawaida Waterfall](https://commons.wikimedia.org/wiki/File:Front_view_video_of_Kawaida_Waterfall,_Cianda,_Kiambu_County.webm) 第 2–8 秒，从竖幅原片截出 480×270 横向细节，不放大。Lebu Ayiga，[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| 3 | 空中飞鸟：小目标的轮廓和运动 | [Common swifts](https://commons.wikimedia.org/wiki/File:Common_swifts._Hubala_residetials_in_Tomaszow_Mazowiecki,_Lodz_Voivodeship,_Poland,_CC0.ogv) 第 0–6 秒，全幅缩至 640×360。WrS.tm.pl，自摄并献入公有领域（PD-self） |

**在手机上看：** 每段先后播放 A、B，均到片尾后提交 A、B、一样或分不出。可重播；三段尽量保持相同的观看距离与方向。每段重新随机分配模式，同一段的两侧使用相同文件。均去掉原声并换为静音 AAC，只评画面。

**已经核实：** 三段文件完整解码均为 6 秒、180 帧，KM9 的 MediaExtractor 均读到 30 fps、BT.709、有限范围、SDR。电影和飞鸟原片未标明色彩矩阵，本次转码按 BT.709 解释。自动测试在独立会话各播放 A、B 一次；原画侧各确认显示 179 帧原画，Spatial 侧分别确认 169/178、172/179、168/179 帧为增强（分母是该侧确认显示的帧数）。其余已确认帧回退原画；未确认的帧不能据此判为掉帧。这些数字只验证增强在播放中实际出现，不是主观画质结果。

[来源、裁切与转码参数、文件 SHA-256 和自动实播计数](android-motion-natural-km9-20261121.json)。自动测试票已清除，上一轮素材和评分独立保留。公开记录不含人工投票或备注。

同样三段素材的[LiteRT ESRGAN 真机整帧耗时与同帧画质](android-litert-motion-km9-20261121.md)另列；这项测量没有使用主观选票。
