# PAD fixed-model local raw-score smoke

Scope: latest explicit user approval of the pending official anti-spoof-mn3
download request and reuse of the existing approved NASA image. Use the existing
OpenCV 4.12.0 static iOS runtime. No new sample, dependency, calibration, model
redistribution or production liveness decision.

Chosen approach: an independent iPhone probe before SDK integration. Reuse of
Identity inference would conflate embedding with PAD; a new inference dependency
is unnecessary before testing the existing DNN runtime. The probe owns no public
SDK interface and must not produce LIVE/SPOOF, threshold decisions or proof.

1. Verify official model byte count and pinned checksum; record SHA-256 binding.
   Correct the historical SHA-512 label: the pinned 96-hex checksum is SHA-384.
2. RED then GREEN host contracts for RGB/NCHW normalization with asymmetric
   channels, stride and final-pixel coverage. Official accuracy config specifies
   128x128 CUBIC resize; mean/std are per RGB channel. For this import smoke use
   the whole approved image with explicit experimental preprocessing revision
   `whole-frame-cubic128-rgb-nchw-v1`; this is not a calibrated face crop.
3. Generate a separate iOS App with the existing static OpenCV binary, no bundled
   model/image. Verify copied input hashes before native decode; run fixed ONNX
   CPU inference twice; require finite 1x2 output and repeatability. Report output
   layer type and raw scores without interpreting their values.
4. Bind device report to immutable App/source/input inventories; preserve nine
   protected files and production MLX lock; leave ordinary Demo available with
   no validation arguments. Update handoff and remaining functional gaps.

Stop condition: exact-model target-runtime import/inference has fresh evidence
or a reproducible native compatibility blocker. Do not expand to calibration,
long resource testing or public adapter until this prerequisite is understood.

Review focus: input identity, channel order, tensor layout, output shape,
experimental crop declaration and absence of a liveness verdict/proof.
