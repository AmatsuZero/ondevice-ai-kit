# Synthetic PHPicker orientation validation

The simulator picker host compiles the production `DemoController` and calls its real `selectPhoto()` entry. XCTest selects an imported synthetic JPEG through PHPicker; the host observes `controller.image` and reports dimensions plus top-left/top-right/bottom-left/bottom-right grayscale samples. It does not duplicate the controller's decoder, inject a picker callback, or assert Vision inference success.

Generate fixtures by compiling `MakeOrientationFixtures.swift` with `swiftc -parse-as-library`; pass a new output directory to its executable. The manifest records the JPEG's actual EXIF2/6/7,80x40 encoded dimensions and SHA256. Generate the standalone project with `ruby scripts/generate-photo-picker-tests.rb`, optionally setting `ODAI_PHOTO_PICKER_TEST_DIR` to a new directory. Use the existing simulator KMP framework and local native package; the protected Demo project is untouched.

For each orientation:

1. Create a new named simulator and record its returned UDID. Import only the corresponding generated JPEG with `simctl addmedia`. New runtimes may already contain Apple sample photos.
2. Confirm the imported original in that simulator's DCIM inventory uniquely matches the fixture SHA256. Obtain its exact picker accessibility label without selecting other assets. The test retains an `isolated-picker-grid` text attachment; an intentionally nonmatching sentinel label can collect that inventory while failing before selection.
3. Set `ODAI_PICKER_FIXTURE_LABEL` in the generated xctestrun target's `EnvironmentVariables`. Missing configuration fails before launch, and a label matching zero or multiple assets fails before tapping. Do not fall back to `firstMatch` across the entire library.
4. Run the appropriate filtered test on the recorded simulator. If copying the xctestrun out of the build directory, resolve its `__TESTROOT__` references to the original products directory. Preserve user text attachments; automatic system attachments may be disabled in the artifact-only xctestrun.
5. Verify one passed test, zero failures/skips, and the `synthetic-picker-orientation` attachment. Only then clean up the simulator created for that run. Retain fixtures, manifests, source hashes, logs and xcresults.

Expected literal signatures:

| EXIF | Output |
| --- | --- |
| 2 | `80x40:white,black,white,black` |
| 6 | `40x80:black,black,white,white` |
| 7 | `40x80:white,white,black,black` |

The 2026-10-26 run is frozen under ignored `artifacts/verification/p4-picker-exif-20261026/summary.json`. All three simulator picker cases passed. The original total-grid-count failure and diagnostic rerun remain there as harness/environment evidence. This does not prove physical-device picker+Vision acceptance, full Compose Demo interaction, or that Photos preserved the original EXIF tag in its exported provider file.

## Physical-device sandbox fallback

`PhotoSandboxDeviceHost.swift` bundles the same EXIF2/6/7 JPEGs in an isolated App, loads
one by `--sandbox-exif`, and calls production `DemoController.useImage`. The UI tests
assert the literal signature above and an `apple.vision.face` result with the same
dimensions and zero faces. It does not invoke `selectPhoto()`, access Photos, or write
the user's photo library. This checks the physical device's `UIImage` normalization
and Vision path, while the real PHPicker/provider gate remains open.

To regenerate, create a fresh fixture directory with `MakeOrientationFixtures.swift`,
then set `ODAI_PHOTO_PICKER_FIXTURE_DIR` to that directory and
`ODAI_PHOTO_SANDBOX_DEVICE_TEST_DIR` to a fresh output directory when running
`ruby scripts/generate-photo-sandbox-device-tests.rb`. Build the generated
`PhotoSandboxDeviceTests` scheme with `xcodebuild build-for-testing` for the connected
device, then run `test-without-building` with
`-only-testing:PhotoSandboxDeviceTests/PhotoSandboxDeviceUITests`. Supply
`ODAI_DEVELOPMENT_TEAM` if the default local signing team does not apply.

The 2026-10-28 device result is retained under ignored
`artifacts/verification/p4-picker-sandbox-device/summary.json`: 3/3 passed on
iPhone 16 Pro Max / iOS 27.0.1. The fixture hashes, logs, xcresults, and exact
boundary are recorded there. Do not report this as a physical PHPicker pass.
