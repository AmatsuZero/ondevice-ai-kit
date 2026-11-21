import UHPlayerSDK
import ODAIKitBinding

/// Bind the Kotlin controller to one view and its prepared engines. Use on the main thread.
@MainActor
public final class UHRealtimeSRHost: NSObject, @preconcurrency SelectableRealtimeSRHost {
    private weak var view: UHPlayerMetalView?
    private let engine: UHPlayerSuperResolution?
    private let explicitEngines: [UHRealtimeSRBackend: UHPlayerSuperResolution]
    private var requested = false
    private var mode: RealtimeSRMode = .original

    public convenience init(view: UHPlayerMetalView, engine: UHPlayerSuperResolution?) {
        self.init(view: view, engine: engine, explicitEngines: [:])
    }

    public init(
        view: UHPlayerMetalView,
        engine: UHPlayerSuperResolution?,
        explicitEngines: [UHRealtimeSRBackend: UHPlayerSuperResolution]
    ) {
        self.view = view
        self.engine = engine
        self.explicitEngines = explicitEngines.filter { backend, preparedEngine in
            Self.isExplicitBackend(backend) && preparedEngine.preferredBackend == backend
        }

        super.init()
        view.tracksPresentedBackend = true
        if view.superResolution != nil {
            view.superResolution = nil
        }
    }

    public func supportedModes() -> [RealtimeSRMode] {
        guard view != nil else { return [] }

        var modes: [RealtimeSRMode] = [.original]
        if engine != nil {
            modes.append(.auto_)
        }
        if explicitEngines[.system] != nil {
            modes.append(.system)
        }
        if explicitEngines[.cunny] != nil {
            modes.append(.cunny)
        }
        if explicitEngines[.spatial] != nil {
            modes.append(.spatial)
        }
        return modes
    }

    public func selectedMode() -> RealtimeSRMode {
        mode
    }

    public func selectMode(mode: RealtimeSRMode) -> Bool {
        guard let view else { return false }

        if mode == .original {
            let modeChanged = self.mode != mode
            self.mode = .original
            requested = false
            if modeChanged || view.superResolution != nil {
                view.superResolution = nil
            }
            return true
        }

        guard let selectedEngine = engine(for: mode) else { return false }
        let modeChanged = self.mode != mode
        self.mode = mode
        requested = true
        if modeChanged || view.superResolution !== selectedEngine {
            view.superResolution = selectedEngine
        }
        return true
    }

    public func applyEnabled(enabled: Bool) {
        guard enabled else {
            requested = false
            mode = .original
            if view?.superResolution != nil {
                view?.superResolution = nil
            }
            return
        }

        requested = true
        if mode == .original, engine != nil {
            mode = .auto_
        }

        guard let selectedEngine = engine(for: mode) else {
            if view?.superResolution != nil {
                view?.superResolution = nil
            }
            return
        }

        if view?.superResolution !== selectedEngine {
            view?.superResolution = selectedEngine
        }
    }

    public func readStatus() -> RealtimeSRStatus {
        func mapped(_ value: UHRealtimeSRBackend) -> RealtimeSRBackend {
            switch value {
            case .spatial: return .spatial
            case .system: return .system
            case .cunny: return .cunny
            case .original: return .original
            @unknown default: return .original
            }
        }

        let preferredBackend = requested
            ? engine(for: mode)?.preferredBackend ?? .original
            : .original

        return RealtimeSRStatus(
            enabled: requested,
            preferredBackend: mapped(preferredBackend),
            presentedBackend: view?.lastPresentedBackend.map(mapped),
            fellBackToOriginal: requested && view?.lastPresentedBackend == .original
        )
    }

    private func engine(for mode: RealtimeSRMode) -> UHPlayerSuperResolution? {
        switch mode {
        case .original:
            return nil
        case .auto_:
            return engine
        case .system:
            return explicitEngines[.system]
        case .spatial:
            return explicitEngines[.spatial]
        case .cunny:
            return explicitEngines[.cunny]
        default:
            return nil
        }
    }

    private static func isExplicitBackend(_ backend: UHRealtimeSRBackend) -> Bool {
        switch backend {
        case .system, .cunny, .spatial:
            return true
        default:
            return false
        }
    }
}
