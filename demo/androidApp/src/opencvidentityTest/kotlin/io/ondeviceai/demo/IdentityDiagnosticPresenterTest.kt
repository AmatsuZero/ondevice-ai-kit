package io.ondeviceai.demo

import io.ondeviceai.opencvidentity.*
import kotlin.test.*
import org.junit.Test

class IdentityDiagnosticPresenterTest {
    @Test fun inferenceCallbackPublishesActionableTextWithoutPrivateCause() {
        val presenter = IdentityDiagnosticPresenter()
        val callback: (OpenCvIdentityDiagnostic) -> Unit = presenter::report
        callback(OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.ALIGNMENT,
            IllegalStateException("private/path/input.png and raw image contents")))
        assertTrue(checkNotNull(presenter.message).contains("对齐"))
        assertFalse(checkNotNull(presenter.message).contains("private"))
        callback(OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.EMBEDDING, IllegalStateException("raw vector")))
        assertTrue(checkNotNull(presenter.message).contains("特征提取"))
        assertFalse(checkNotNull(presenter.message).contains("raw"))
        presenter.clear()
        assertNull(presenter.message)
    }
}
