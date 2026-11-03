# Real-device Files input validation

Use only the separately approved local portrait and pinned YuNet/SFace inputs. No Photos writes, camera, microphone, new biometric data, network uploads or model downloads. Ordinary Demo is built by `scripts/generate-opencv-identity-app.rb` with validation disabled.

`generate-files-source.rb` creates an isolated iOS15 source app `io.ondeviceai.fixture.identityfiles` (display name `IdentityInput`). Its own Documents directory is visible in **On My iPhone** through `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace`. It has no SDK/model dependency or packaged portrait. Never add these flags to the real Demo, whose private encrypted templates and model directory must stay private.

```sh
rtk proxy env ODAI_FILES_SOURCE_DIR=artifacts/verification/identity-files-ui-local/source-app ruby probes/ios-opencv-identity/generate-files-source.rb
```

Build/install with existing authorized development signing. Transfer only `approved-astronaut.png` and a `models/` directory containing exactly the two previously approved ONNX files into this source app's Documents via `devicectl device copy to`. Confirm their pinned hashes first. This is local sandbox transfer, not asset embedding or Photos installation.

On the physical iPhone, through the ordinary Demo:

1. Enable local enrollment using its explicit consent button.
2. Open **从文件选择图片**, cancel, and observe **图片选择已取消**.
3. Reopen, use **浏览 → 我的iPhone → IdentityInput**, choose only `approved-astronaut.png`.
4. Open **身份与活体**. Observe **身份输入已选择**; scroll within shared controls and tap **注册**. Require a one-template count and `ENROLLED · opencv.identity.apple`.
5. Tap **验证**. Require `THRESHOLD_UNVERIFIED`; do not introduce a guessed policy.
6. Tap **删除本次所有注册并停用**. Require the full-delete/disabled status.
7. Tap **导入身份模型**, open only **IdentityInput/models**, then use system **打开**. Require installed/consent-ready status.
8. Relaunch the ordinary Demo without validation arguments. Require model-ready status, no active session and no selected image.

Capture only app/system-control observations and the known fixture directory. Do not open other recent/user files or retain screenshots of personal file listings. Hash the approved source readback and bind UI snapshots to the ordinary app binary/source identity. These checks prove this local Files provider and automated device UI sequence; they do not prove iCloud/external-provider behavior, human touch, recognition accuracy, PAD or full release acceptance. The fixture source may remain installed with only these approved local inputs; it is not a production dependency.
