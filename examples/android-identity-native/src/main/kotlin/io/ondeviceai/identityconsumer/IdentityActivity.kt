package io.ondeviceai.identityconsumer

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.ondeviceai.identity.*

class IdentityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text="Identity requires a host-authorized backend and calibrated policies"
        })
    }
}

/** Called off the UI thread with the host's real authorization, stores, engines and policies. */
object IdentityNativeUsage {
    fun createCandidate(store: IdentityStore, grant: AuthorizationGrant): CandidateRef = store.createCandidate(grant)

    fun verify(engine: FaceVerification, probe: IdentityProbe, template: TemplateRef,
               grant: AuthorizationGrant, options: VerificationOptions, backendId: String): VerificationTask =
        engine.verify(probe, template, grant, options, IdentityBackendSelection.Explicit(backendId))

    fun decodePolicy(canonicalJSON: String): ThresholdPolicy = ThresholdPolicy.decodeJSON(canonicalJSON)
    fun decodePADPolicy(canonicalJSON: String): PADPolicy = PADPolicy.decodeJSON(canonicalJSON)
    fun modelBinding(canonicalJSON: String): ModelBinding = ModelBinding.decodeJSON(canonicalJSON)
}
