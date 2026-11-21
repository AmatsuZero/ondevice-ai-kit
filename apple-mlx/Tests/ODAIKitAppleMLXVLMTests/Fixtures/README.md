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

Later `MLXVLMVideoTokenizerTests` separately opts in to the existing verified local
model directory and executes the actual fixed tokenizer/template. It never loads
weights; the original ByteTokenizer characterization above remains unchanged.

## Negative profile additions (2026-10-23)

`MLXVLMVideoProfileTests` executes all four through the real snapshot and checks
rejection, owned-copy cleanup and unchanged input bytes. These are narrow negative
fixtures, not arbitrary-container/HDR support or natural-quality evidence.

All three FFmpeg fixtures use `testsrc2=size=160x90`, `-an -c:v libx264
-pix_fmt yuv420p -bf 0 -movflags +write_colr`:

| File | Additional arguments | Bytes | SHA256 |
| --- | --- | --- | --- |
| video-pq.mp4 | rate=24, -t 2, -x264-params colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc, -use_editlist 0 | 31996 | 4ce99328452e47a35e504e92b783cc34f224dbb3f581d7a29d709fa034e7d5f7 |
| video-offset.mp4 | rate=24, -t 2, -x264-params colorprim=bt709:transfer=bt709:colormatrix=bt709, -use_editlist 1 -output_ts_offset 0.25 | 32044 | 776bca3955bed8fcfdafac49f9d1d9a1e319d298b1c041ebc86f80abd4d60d5e |
| video-two-frames.mp4 | rate=2, -t 1, -x264-params colorprim=bt709:transfer=bt709:colormatrix=bt709, -use_editlist 0 | 6020 | 05e00525b3fb54536de40a38ce542fed23430873d5e4248fe9f50fa93f579c64 |
| video-external-reference.mp4 | Structural mutation of video-sdr.mp4 described below | 32025 | 5ea8da1cb930244b918e990658387e444dec3d2af642bc6e6b0de25111daa10a |

The offset file's actual stream start is 0.25s. The initial attempt with
`-use_editlist 0` ignored the requested offset and produced the original fixture;
that attempt is preserved in the local ledger and is not the committed offset file.
The two-frame input is deliberately rejected by the current eight-unique-sample
profile; these tests do not add shorter-clip support or duplicate frames.
PQ is signalled by actual H.264 VUI/BT.2020 metadata, not only the filename.

For the external-reference fixture, recurse through moov/trak/mdia/minf/dinf into
dref; replace its sole self-contained `url ` entry (version/flags=0x00000001)
with version/flags=0x00000000 followed by NUL-terminated relative URL
`synthetic-external-media.mp4`, updating each enclosing box size. Source moov
follows mdat, so original sample bytes/offsets stay unchanged. No external file
is provided or private path named. This proves rejection of this structural
profile, not successful external playback or absence of all possible external I/O.
Exact generator, commands, ffprobe JSON and both offset attempts are retained in
`.superpowers/sdd/2026-10-23-apple-mlxvlm-video-profile/`.

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
