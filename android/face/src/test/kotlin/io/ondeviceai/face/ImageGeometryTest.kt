package io.ondeviceai.face
import kotlin.test.*
class ImageGeometryTest {
    @Test fun rotationsAndPixelBoxesMatchContract() {
        val p = FacePoint(0.2,0.3)
        for ((angle,want) in listOf(0 to FacePoint(0.2,0.3),90 to FacePoint(0.7,0.2),180 to FacePoint(0.8,0.7),270 to FacePoint(0.3,0.8))) {
            val out = ImageGeometry.rotate(p,angle); assertEquals(want.x,out.x,1e-6); assertEquals(want.y,out.y,1e-6)
        }
        assertEquals(FaceRect(0.1,0.1,0.3,0.2),ImageGeometry.pixelRect(10.0,20.0,30.0,40.0,100,200))
        assertFails { ImageGeometry.pixelRect(0.0,0.0,1.0,1.0,0,10) }
        val preview=ImageGeometry.preview(100,200,200.0,200.0,true).apply(FacePoint(0.1,0.1))
        assertEquals(180.0,preview.x,1e-6); assertEquals(-60.0,preview.y,1e-6)
    }
}
