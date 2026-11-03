# Optional Android Identity prototype

Build with `-PenableOpenCvIdentity=true`. It can be combined with
`-PenableLiteRtSr=true -PenableLiteRtLm=true`; the default Demo links none of
these vendor runtimes. OpenCV is pinned to `org.opencv:opencv:4.12.0`.

Open **身份与活体 → 导入身份模型** and select a flat ZIP containing exactly:

| Filename | Bytes | SHA-256 |
| --- | ---: | --- |
| `face_detection_yunet_2023mar.onnx` | 232589 | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| `face_recognition_sface_2021dec.onnx` | 38696353 | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` |

Both files come from OpenCV Zoo revision
`47534e27c9851bb1128ccc0102f1145e27f23f98`. Sources and license evidence are
in [the validation record](../../../../docs/validation/android-opencv-identity-demo.md).
No weights or example portraits are bundled in the repository or APK.

After installation, **同意并启用本机注册** creates a local prototype grant
lasting 30 minutes. Select an upright photo, then **注册**. Only use your own
or authorized images. A successful enrollment persists an encrypted template
through IdentityStore/Android Keystore, returns an opaque reference, and stores
no raw image. **删除本次注册并停用** revokes/deletes the session candidate and
waits for template purge; ordinary Activity destruction also attempts cleanup.
Abrupt process death and cleanup failure recovery UI remain unverified.

The prototype quality rule checks only valid five-landmark geometry. Its binary
score is not detector confidence, a calibrated image-quality metric, or identity
accuracy. Multiple faces are rejected. Input is bounded to 10 MiB encoded,
16..4096 per dimension and at most 4,194,304 decoded pixels; long edge is reduced
to 640 for YuNet. Upstream alignment produces 112×112 BGR crops, SFace internally
swaps RGB, and the 128 float features are L2-normalized.

**验证** deliberately returns `THRESHOLD_UNVERIFIED`. Production 1:N index/query
support and calibrated gallery policies remain incomplete, and no PAD backend
is supplied. Tutorial thresholds are not adopted. Native inference is blocking:
cancel suppresses publication and waits for the current native call; fast native
abort, sustained memory/thermal/latency, minimum-system and actual picker UI
acceptance remain separate work.

OpenCV Java network objects are GC-finalized. SDK close drains admitted calls
and releases the ModelStore lease, but does not promise immediate native memory
deallocation. Sanitized diagnostic callbacks contain only stage/category,
never raw exception text, paths, vectors, or images.
