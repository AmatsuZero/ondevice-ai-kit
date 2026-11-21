import CoreGraphics

// Compile with Swift6 and complete concurrency checking. The same camera is
// used by owner tasks, capture callbacks and concurrent terminal-close callers.
// This checks the public transfer contract, not the implementation's spelling.
private func requireCameraTransfer<Value: Sendable>(_ value: Value) {}

func checkCameraConcurrencyContract(_ camera: CameraSource) {
    requireCameraTransfer(camera)
    let callback: (@Sendable (CGImage) -> Void)? = camera.onFrame
    camera.onFrame = callback
}
