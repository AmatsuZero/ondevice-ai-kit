package io.ondeviceai.demo

import android.app.Instrumentation
import android.os.Bundle
import io.ondeviceai.litertsr.LiteRtVideoSuperResolutionBackend
import io.ondeviceai.video.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.*
import io.ondeviceai.litertsr.LiteRtSrModel

/** Headless owned-fixture inference: does not launch an Activity or unlock the device. */
class VideoSrInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = JSONObject().put("runId", java.util.UUID.randomUUID().toString()).put("status", "running")
        val bundle = Bundle()
        try {
            runBlocking {
                val root = targetContext.filesDir.resolve("video-sr-validation")
                root.resolve("report.json").writeText(report.toString(2))
                val backend = LiteRtVideoSuperResolutionBackend(root.resolve("ESRGAN.tflite"), root.resolve("outputs"))
                check(backend.availability().status.name == "AVAILABLE")
                val client = VideoProcessingClient(backend.backendId, listOf(backend))
                val input = root.resolve("input.mp4")
                val metadata = MediaExtractor()
                metadata.setDataSource(input.absolutePath)
                val video = (0 until metadata.trackCount).map { metadata.getTrackFormat(it) }.first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                val audioCount = (0 until metadata.trackCount).count { metadata.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                val expectedWidth = video.getInteger(MediaFormat.KEY_WIDTH) * 4
                val expectedHeight = video.getInteger(MediaFormat.KEY_HEIGHT) * 4
                metadata.release()
                val started = android.os.SystemClock.elapsedRealtime()
                val output = client.process(VideoProcessingRequest(input, 4, VideoAudioPolicy.PRESERVE))
                check(output.output.info.frameCount == 2)
                check(output.output.info.width == expectedWidth && output.output.info.height == expectedHeight)
                check(output.output.info.audioTrackCount == audioCount)
                output.output.file.copyTo(root.resolve("result.mp4"), overwrite = true)
                val discarded = client.process(VideoProcessingRequest(input, 4, VideoAudioPolicy.DISCARD))
                check(discarded.output.info.audioTrackCount == 0)
                validateMedia(root.resolve("result.mp4"), input, true)
                validateMedia(discarded.output.file, input, false)
                report.put("muxedTracksAndPtsValidated", true).put("firstFrameDecoded", true)
                discarded.output.file.copyTo(root.resolve("discarded.mp4"), overwrite = true)
                discarded.output.release()
                // Cancel from real completed-frame progress, then drain before same-client reentry.
                val firstFrame = CompletableDeferred<Unit>()
                val cancelled = async {
                    client.process(VideoProcessingRequest(input, 4, VideoAudioPolicy.DISCARD)) { firstFrame.complete(Unit) }
                }
                firstFrame.await(); cancelled.cancelAndJoin(); client.drain()
                check(root.resolve("outputs").walkTopDown().count { it.isFile } == 1)
                val reentry = client.process(VideoProcessingRequest(input, 4, VideoAudioPolicy.DISCARD))
                check(reentry.output.info.frameCount == 2); reentry.output.release()
                client.close()
                check(output.output.file.exists())
                report.put("backend", output.backendId).put("frames", output.output.info.frameCount)
                    .put("width", output.output.info.width).put("height", output.output.info.height)
                    .put("audioTracks", audioCount).put("audioDiscarded", true)
                    .put("modelSha256", LiteRtSrModel.SHA256).put("device", android.os.Build.MODEL)
                    .put("sdk", android.os.Build.VERSION.SDK_INT).put("durationMs", android.os.SystemClock.elapsedRealtime() - started)
                output.output.release()
                check(!output.output.file.exists())
                check(root.resolve("outputs").walkTopDown().none { it.isFile })
                report.put("status", "passed").put("outputSurvivedClose", true).put("releaseRemovedOutput", true)
                    .put("cancelDrainReentry", true)
                root.resolve("report.json").writeText(report.toString(2))
            }
            bundle.putString("status", "passed")
            finish(0, bundle)
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.stackTraceToString())
            targetContext.filesDir.resolve("video-sr-validation/report.json").writeText(report.toString(2))
            bundle.putString("status", "failed")
            bundle.putString("error", error.stackTraceToString())
            finish(1, bundle)
        }
    }
    private fun validateMedia(output: File, input: File, preserveAudio: Boolean) {
        fun describe(file: File): Pair<List<MediaFormat>, List<List<Long>>> {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val timestamps = formats.indices.map { index ->
                    extractor.selectTrack(index); extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val values = mutableListOf<Long>()
                    while (extractor.sampleTime >= 0) { values += extractor.sampleTime; if (!extractor.advance()) break }
                    extractor.unselectTrack(index)
                    values.toList()
                }
                return formats to timestamps
            } finally { extractor.release() }
        }
        val (sourceFormats, sourcePts) = describe(input)
        val (formats, pts) = describe(output)
        val video = formats.indices.single { formats[it].getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC }
        val sourceVideo = sourceFormats.indices.single { sourceFormats[it].getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC }
        check(formats[video].getInteger(MediaFormat.KEY_WIDTH) == sourceFormats[sourceVideo].getInteger(MediaFormat.KEY_WIDTH) * 4)
        check(formats[video].getInteger(MediaFormat.KEY_HEIGHT) == sourceFormats[sourceVideo].getInteger(MediaFormat.KEY_HEIGHT) * 4)
        check(pts[video].size == sourcePts[sourceVideo].size)
        check(pts[video].zipWithNext().all { (a, b) -> b > a })
        check(pts[video].zip(sourcePts[sourceVideo]).all { (a, b) -> kotlin.math.abs(a - b) <= 1 })
        val audio = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        val sourceAudio = sourceFormats.indices.filter { sourceFormats[it].getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        check(audio.size == if (preserveAudio) sourceAudio.size else 0)
        if (preserveAudio) audio.zip(sourceAudio).forEach { (out, src) -> check(pts[out] == sourcePts[src]) }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val bitmap = checkNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
            try { check(bitmap.width == formats[video].getInteger(MediaFormat.KEY_WIDTH) && bitmap.height == formats[video].getInteger(MediaFormat.KEY_HEIGHT)) }
            finally { bitmap.recycle() }
        } finally { retriever.release() }
    }

}
