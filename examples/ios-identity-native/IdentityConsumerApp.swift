import SwiftUI
import ODAIKitIdentity

@main struct IdentityConsumerApp: App {
    var body: some Scene {
        WindowGroup { Text("Identity requires a host-authorized backend and calibrated policies") }
    }
}

/// Native-only usage surface. The host owns authorization, model selection and resource disposal.
/// Nothing in this consumer generates an identity decision without the supplied SDK dependencies.
enum IdentityNativeUsage {
    static func createCandidate(store: IdentityStore, grant: AuthorizationGrant) async throws -> CandidateRef {
        try await store.createCandidate(grant)
    }

    static func verify(engine: FaceVerification, probe: IdentityProbe, template: TemplateRef,
                       grant: AuthorizationGrant, options: VerificationOptions,
                       backendId: String) async throws -> VerificationTask {
        try await engine.verify(probe: probe, templateRef: template, grant: grant,
                                options: options, backend: .explicit(backendId))
    }

    static func decodePolicy(_ canonicalJSON: String) throws -> ThresholdPolicy {
        try ThresholdPolicy.decodeJSON(canonicalJSON)
    }

    static func decodePADPolicy(_ canonicalJSON: String) throws -> PADPolicy {
        try PADPolicy.decodeJSON(canonicalJSON)
    }

    static func modelBinding(from canonicalJSON: String) throws -> ModelBinding {
        try ModelBinding.decodeJSON(canonicalJSON)
    }
}
