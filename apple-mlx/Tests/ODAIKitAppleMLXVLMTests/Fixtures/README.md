# Synthetic video admission fixtures

Created locally with FFmpeg; no captured or private media, no model weights.
All files are small self-contained ISO BMFF containers. H.264 fixtures use
libx264 with `-x264-params colorprim=bt709:transfer=bt709:colormatrix=bt709`,
`-pix_fmt yuv420p -bf 0 -movflags +write_colr -use_editlist 0 -an`.
The explicit encoder params are necessary: this host FFmpeg did not retain
transfer/primaries from the general color options alone. Tests keep strict
AVFoundation transfer-function validation.

| File | lavfi input / duration | Purpose |
| --- | --- | --- |
| video-sdr.mp4 | testsrc2=size=160x90:rate=24 / 2s | 8 uniform actual samples, no duration endpoint |
| video-29fps.mp4 | testsrc2=size=160x90:rate=29 / 2s | 8 decoded samples become 7 in pinned upstream resampling |
| video-long.mp4 | color=c=red:size=160x90:rate=24 / 6s | duration rejection |
| video-hevc.mp4 | color=c=red:size=160x90:rate=24 / 2s | libx265 with equivalent x265 color params, codec rejection |
| video-wide.mp4 | color=c=red:size=1922x16:rate=24 / 1s | dimension rejection |
| video-pixels.mp4 | color=c=red:size=1920x1200:rate=24 / 1s | total-pixel rejection |
| video-multi.mp4 | map video-sdr video stream twice, copy codecs | track-count rejection |

Example command for the first row:

```sh
ffmpeg -nostdin -f lavfi -i testsrc2=size=160x90:rate=24 -t 2 -an \
  -c:v libx264 -x264-params colorprim=bt709:transfer=bt709:colormatrix=bt709 \
  -pix_fmt yuv420p -bf 0 -movflags +write_colr -use_editlist 0 video-sdr.mp4
```

`preprocessor_config.json` is a byte-identical copy from the already verified
fixed SmolVLM2 model revision fa57db46815177fbdfd65cc85a2b3416a8332268.
The processor tests use the real fixed MLXVLM implementation and a synthetic
byte tokenizer, validating frame/prompt mechanics only. No model inference,
real model tokenization or natural-video quality is claimed.

Recorded file inventory:

```json
[
  {
    "path": "preprocessor_config.json",
    "bytes": 599,
    "sha256": "149e315d9410368e5491455bb06e0f763426e9e56cca731c13b24404a29b6374"
  },
  {
    "path": "video-29fps.mp4",
    "bytes": 34049,
    "sha256": "9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51"
  },
  {
    "path": "video-hevc.mp4",
    "bytes": 4433,
    "sha256": "e9c0cd8589b988d7789d5b7648d5bf797444ec0c30210c7912a1bdd096520434"
  },
  {
    "path": "video-long.mp4",
    "bytes": 4815,
    "sha256": "8c85688fe3321e5c81be2cd234b2b992ea14b39beec921a3fef7edf1d1fb9bc8"
  },
  {
    "path": "video-multi.mp4",
    "bytes": 64107,
    "sha256": "d642ca0cff88a5d0fcfeeef62bfff0a73439888ebd74abecb06f9390d3e0b1ea"
  },
  {
    "path": "video-pixels.mp4",
    "bytes": 3893,
    "sha256": "cb471af5bdd1d986a1fad774ed603329d392325bda534ca8be114d17f1364936"
  },
  {
    "path": "video-sdr.mp4",
    "bytes": 31996,
    "sha256": "eee80443c0e6a03a5ead2b7ebe43eae9567a23a1fbc6284320af26feab11a57b"
  },
  {
    "path": "video-wide.mp4",
    "bytes": 2056,
    "sha256": "3b78ebfa4ea4d77bc8a3b9825fdacb97cc6e893dd51088eba5f1b0f1cbfcc199"
  }
]
```
