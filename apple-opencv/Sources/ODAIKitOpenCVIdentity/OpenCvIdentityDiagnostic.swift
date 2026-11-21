import Foundation
import ODAIKitIdentity

/// Allowlisted native context only; never includes images, paths or exception text.
public struct OpenCvIdentityDiagnostic: Equatable, Sendable {
    public enum Stage: String, Sendable {
        case detectorLoad, recognizerLoad, decode, detection, alignment, embedding, unknown
    }
    public enum Category: String, Sendable {
        case invalidInput, multipleFaces, stopped, unavailable
    }
    public let stage: Stage
    public let category: Category
}

/// The original NSError is deliberately not retained across the native boundary.
struct OpenCvIdentityNativeFailure: Error {
    let code: IdentityErrorCode
    let diagnostic: OpenCvIdentityDiagnostic

    init(_ error: Error) {
        let native = error as NSError
        guard native.domain == "io.ondeviceai.opencv.identity" else {
            code = .backendUnavailable
            diagnostic = .init(stage: .unknown, category: .unavailable)
            return
        }
        let stage = (native.userInfo["stage"] as? String)
            .flatMap(OpenCvIdentityDiagnostic.Stage.init(rawValue:)) ?? .unknown
        let category: OpenCvIdentityDiagnostic.Category
        switch native.code {
        case 1: code = .invalidInput; category = .invalidInput
        case 2: code = .multipleFaces; category = .multipleFaces
        case 3: code = .cancelled; category = .stopped
        default: code = .backendUnavailable; category = .unavailable
        }
        diagnostic = .init(stage: stage, category: category)
    }

    func identityError(backendId: String, modelBinding: ModelBinding?,
                       diagnostic sink: ((OpenCvIdentityDiagnostic) -> Void)?) -> IdentityError {
        sink?(diagnostic)
        return IdentityError(code, backendId: backendId, modelBinding: modelBinding)
    }
}
