import Foundation

@main struct CameraLifecycleChecks {
    static func main() {
        var camera = CameraLifecycle(foreground: true)
        let initial = camera.requestStart()
        precondition(camera.canStart(initial), "start is admitted while active")
        camera.pause()
        precondition(!camera.canStart(initial), "permission completion cannot start capture in background")
        let resumed = camera.activate()
        precondition(resumed != nil && camera.canStart(resumed!), "foreground restores requested capture")
        camera.stop()
        precondition(!camera.canStart(initial), "stop invalidates pending permission and startup")
        precondition(camera.activate() == nil, "foreground never undoes explicit user stop")
        let next = camera.requestStart()
        precondition(!camera.canStart(initial) && camera.canStart(next), "old permission callbacks cannot restart a new session")
        camera.pause(); _ = camera.activate(); camera.pause()
        precondition(!camera.canStart(next), "rapid lifecycle transitions cannot start capture while inactive")
        precondition(camera.activate() == next && camera.canStart(next), "latest foreground transition can resume")
        print("Camera lifecycle checks passed (7 scenarios)")
    }
}
