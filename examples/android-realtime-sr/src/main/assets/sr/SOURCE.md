# CuNNy 1×8 BOX — Android shader provenance

The three `cunny*.comp` compute shaders derive from funnyplanter's CuNNy 1×8 BOX (LGPL-3.0-or-later). The original copyright and license notice are at the top of each shader; the corresponding license text is in `LICENSE`.

The fixed iOS comparison is UHPlayerSDK commit `7d40dfe`, `Examples/SuperResolution/CuNNy.metal` (upstream CuNNy revision `906031bb00c15dd6a6bbbaa21c0eb0b724ca8437`). The exact reference file is kept outside APK assets at `examples/android-realtime-sr/reference/CuNNy.metal`, SHA-256 `d1ec82236af13bf2dbd23f9488ad69d3bdc3a3a1a8c471c4ca3d84ba710d9fda`.

Android changes: GLES 3.1 compute, BT.709 luma extraction from RGB, packed RGBA16F intermediate textures, explicit out-of-bounds guards and a 2× output. Layer 0 uses integer texel reads. The residual base uses four clamped integer reads and explicit pixel-centre bilinear weights; normalized hardware filtering exceeded the one-level error budget in a full-size emulator run. Coefficient order, spatial offsets, edge clamping and subpixel channel layout follow the fixed reference. Regenerate from the repository root with:

```sh
rtk proxy python3 tools/probes/generate_cunny_gles.py --source examples/android-realtime-sr/reference/CuNNy.metal --out examples/android-realtime-sr/src/main/assets/sr
```

This is experimental source and local validation only. Commercial distribution of an APK/AAR or incorporation into the production player is outside this validation.

`spatial.comp` is a separate, single-pass 2× bilinear luma upscaler with a four-neighbor sharpen filter (gain 0.5, limited to the local range). It uses no CuNNy coefficients. Both backends return a luma texture; the player retains source chroma in its window pass.
