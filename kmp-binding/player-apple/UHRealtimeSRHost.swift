import UHPlayerSDK
import ODAIKitBinding

/// Bind the Kotlin controller to one view and a prepared engine. Use on the main thread.
@MainActor
public final class UHRealtimeSRHost: NSObject, @preconcurrency RealtimeSRHost {
    private weak var view: UHPlayerMetalView?
    private let engine: UHPlayerSuperResolution?
    private var requested = false
    public init(view: UHPlayerMetalView, engine: UHPlayerSuperResolution?) {
        self.view = view; self.engine = engine
        super.init()
        view.tracksPresentedBackend = true
    }
    public func applyEnabled(enabled: Bool) {
        requested = enabled
        view?.superResolution = enabled ? engine : nil
    }
    public func readStatus() -> RealtimeSRStatus {
        func mapped(_ value: UHRealtimeSRBackend) -> RealtimeSRBackend {
            switch value { case .spatial: return .spatial; case .system: return .system; case .cunny: return .cunny; case .original: return .original; @unknown default: return .original }
        }
        let presented = view?.lastPresentedBackend
        return RealtimeSRStatus(enabled:requested, preferredBackend:mapped(requested ? engine?.preferredBackend ?? .original : .original),
                                presentedBackend:presented.map(mapped), fellBackToOriginal:requested && presented == .original)
    }
}
