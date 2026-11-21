import Foundation
import ODAIKitBinding
final class ProbeTask: NSObject, BridgeTask { func cancel() {} }
final class SwiftFaceBridge: NSObject, FaceBridge {
    func backendId() -> String { "apple.vision" }
    func availability() -> BridgeAvailability { BridgeAvailability(available: true, reason: nil, backendId: backendId(), minimumOS: "15.0") }
    func detect(inputId: String, selection: BridgeBackendSelection, callback: FaceCallback) -> BridgeTask { callback.onCancelled(); return ProbeTask() }
    func close() {}
}
func verifySwiftBridge() { precondition(FaceBridgeKt.probeBackend(bridge: SwiftFaceBridge()) == "apple.vision") }

final class SwiftSpeechBackendsCallback: NSObject, SpeechBridgeBackendsCallback {
    func onResult(backends: [SpeechBridgeBackend]) {}
    func onFailure(failure: SpeechBridgeFailure) {}
    func onCancelled() {}
}

final class SwiftSpeechTtsCallback: NSObject, SpeechBridgeTtsCallback {
    func onResult(synthesis: SpeechBridgeTtsResult) {}
    func onFailure(failure: SpeechBridgeFailure) {}
    func onCancelled() {}
}

final class SwiftSpeechAsrCallback: NSObject, SpeechBridgeAsrCallback {
    func onResult(output: SpeechBridgeAsrResult) {}
    func onFailure(failure: SpeechBridgeFailure) {}
    func onCancelled() {}
}

final class SwiftSpeechCloseCallback: NSObject, SpeechBridgeCloseCallback {
    func onClosed() {}
    func onFailure(failure: SpeechBridgeFailure) {}
}

// Kotlin sealed interfaces are exported as Swift protocols. Native adapters must reject
// conformers other than the generated Default and Explicit selection implementations.
final class SwiftUnknownSpeechSelection: NSObject, SpeechBridgeSelection {}

func verifySwiftSpeechBridge(bridge: SpeechBridge) {
    let backendCallback = SwiftSpeechBackendsCallback()
    let ttsCallback = SwiftSpeechTtsCallback()
    let asrCallback = SwiftSpeechAsrCallback()
    let defaultSelection = SpeechBridgeSelectionDefault.shared
    let explicitSelection = SpeechBridgeSelectionExplicit(backendId: "system.tts")
    let microphone = SpeechBridgeInputMicrophone.shared
    let file = SpeechBridgeInputAudioFile(reference: "opaque-file")
    let availability = BridgeAvailability(available: true, reason: nil, backendId: "system.asr", minimumOS: "15.0")
    _ = SpeechBridgeBackend(capability: .asr, backendId: "system.asr", availability: availability,
                            supportedInputs: [.audioFile], offline: true)
    _ = SpeechBridgeTtsResult(backendId: "system.tts", voiceId: nil)
    _ = SpeechBridgeAsrResult(backendId: "system.asr", transcript: "fixture", confidence: nil)
    _ = SpeechBridgeFailure(code: .platform, reason: nil, domain: "fixture", platformCode: 7)
    bridge.queryBackends(capability: .tts, callback: backendCallback).cancel()
    bridge.speak(request: SpeechBridgeTtsRequest(text: "hello", locale: "en-US", voiceId: nil),
                 selection: defaultSelection, callback: ttsCallback).cancel()
    bridge.recognize(request: SpeechBridgeAsrRequest(locale: "en-US", input: microphone),
                     selection: explicitSelection, callback: asrCallback).cancel()
    bridge.recognize(request: SpeechBridgeAsrRequest(locale: "en-US", input: file),
                     selection: SwiftUnknownSpeechSelection(), callback: asrCallback).cancel()
    bridge.close(callback: SwiftSpeechCloseCallback())
}

final class SwiftIdentityCallback: NSObject, IdentityCallback {
    func onSuccess(value: Any?) {}
    func onFailure(error: IdentityBridgeFailure) {}
}

// Typecheck the exported consumer ABI, including ordinary opaque classes and session controls.
// This probe does not assert biometric availability or construct a calibrated native policy.
func verifySwiftIdentityBridge(bridge: IdentityBridge, session: IdentityLivenessSession) {
    let callback = SwiftIdentityCallback()
    let grant = IdentityAuthorizationGrant(canonicalValue: "native-grant-json")
    let candidate = IdentityCandidateReference(canonicalValue: "native-candidate-json")
    let template = IdentityTemplateReference(canonicalValue: "native-template-json")
    let probe = IdentityProbeReference(canonicalValue: "host-input-handle")
    let policy = IdentityThresholdPolicy(canonicalValue: "native-policy-json")
    let backend = IdentityBackendSelectionExplicit(backendId: "optional.identity")
    let owner = IdentityOwnershipAuthorization(storeId: "opaque-store", evidenceRef: "opaque-evidence")
    let options = IdentityVerificationOptions(policy: policy, revision: "v1")
    bridge.createCandidate(grant: grant, callback: callback).close()
    bridge.enroll(probe: probe, candidate: candidate, grant: grant, options: IdentityEnrollmentOptions(qualityFloor: 0.8, qualityPolicyId: "host-quality", revision: "v1"), backend: backend, callback: callback).cancel()
    bridge.verify(probe: probe, template: template, grant: grant, options: options, backend: backend, callback: callback).cancel()
    bridge.createGalleryBuilder(baseGallery: nil, grant: grant, options: IdentityGalleryOptions(policy: policy, aggregationPolicyId: "subject-max-v1"), backend: backend, callback: callback).cancel()
    let builder = IdentityGalleryBuilderReference(canonicalValue: "builder-handle")
    bridge.addTemplate(builder: builder, template: template, grant: grant, callback: callback).cancel()
    bridge.removeTemplate(builder: builder, template: template, grant: grant, callback: callback).cancel()
    bridge.publishGallery(builder: builder, callback: callback).cancel()
    bridge.closeGalleryBuilder(builder: builder, callback: callback).cancel()
    bridge.identify(probe: probe, gallery: IdentityGalleryReference(canonicalValue: "native-gallery-json"), grant: grant, options: IdentityIdentificationOptions(topK: 3, policy: nil, revision: "v1"), backend: backend, callback: callback).cancel()
    bridge.openLivenessSession(grant: grant, options: IdentityLivenessOptions(policy: IdentityPADPolicy(canonicalValue: "native-pad-json"), purpose: "host-purpose", revision: "v1"), backend: backend, callback: callback).cancel()
    bridge.revokeGrant(grantId: "opaque-grant", ownership: owner, callback: callback).cancel()
    bridge.deleteTemplate(reference: template, ownership: owner, callback: callback).cancel()
    bridge.deleteCandidate(reference: candidate, ownership: owner, callback: callback).cancel()
    bridge.deleteGallery(reference: IdentityGalleryReference(canonicalValue: "native-gallery-json"), ownership: owner, callback: callback).cancel()
    let wait = IdentityDeletionWaitOptions(durationNanos: nil)
    bridge.awaitDeletion(receipt: IdentityDeletionReceipt(canonicalValue: "native-receipt-json", store: .identity), options: wait, callback: callback).cancel()
    bridge.awaitRevocation(receipt: IdentityRevocationReceipt(canonicalValue: "native-revocation-json"), options: wait, callback: callback).cancel()
    session.start(callback: callback).cancel()
    _ = session.submitFrame(frame: IdentityLivenessFrame(encodedFrame: KotlinByteArray(size: 1), timestampNanos: 1, revision: "v1", challenge: nil))
    session.nextEvent(callback: callback).cancel()
    session.result(callback: callback).cancel()
    session.consumeProof(proof: IdentityLivenessProof(canonicalValue: "native-proof-json"), captureDigest: "capture-digest", purpose: "host-purpose", callback: callback).cancel()
    session.finish(); session.cancel(); session.close()
    bridge.close()
}
