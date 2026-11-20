package io.ondeviceai.realtimesr.validation;

import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public final class SrFixtureCrop {
    private static final long TIMEOUT_US = 100_000L;
    private static final long MAX_WAIT_NS = 15_000_000_000L;

    private SrFixtureCrop() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                    "args: <input.mp4> <timestampUs> <x> <y> <w> <h> <outputY8>");
        }
        final String inputPath = args[0];
        final long targetUs = Long.parseLong(args[1]);
        final int x = Integer.parseInt(args[2]);
        final int y = Integer.parseInt(args[3]);
        final int w = Integer.parseInt(args[4]);
        final int h = Integer.parseInt(args[5]);
        final String outputPath = args[6];

        MediaExtractor extractor = null;
        MediaCodec codec = null;
        Image currentImage = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);

            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    track = i;
                    break;
                }
            }
            if (track < 0) throw new RuntimeException("no video track in " + inputPath);

            MediaFormat fmt = extractor.getTrackFormat(track);
            extractor.selectTrack(track);

            String mime = fmt.getString(MediaFormat.KEY_MIME);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEos = false;
            boolean done = false;
            final long startNs = System.nanoTime();

            while (!done) {
                if (System.nanoTime() - startNs > MAX_WAIT_NS) {
                    throw new RuntimeException(
                            "timeout waiting for decoded frame ptsUs>=" + targetUs);
                }

                if (!inputEos) {
                    int in = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(in);
                        int size = extractor.readSampleData(inBuf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            int sampleFlags = extractor.getSampleFlags();
                            if ((sampleFlags & (MediaExtractor.SAMPLE_FLAG_ENCRYPTED | MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME)) != 0) {
                                throw new IllegalStateException("Only clear, complete local H.264 samples are supported; encrypted or partial frames are rejected");
                            }
                            int flags = (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                            codec.queueInputBuffer(in, 0, size, pts, flags);
                            extractor.advance();
                        }
                    }
                }

                int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    System.out.println("output format changed: " + codec.getOutputFormat());
                } else if (out == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // no action required with the Image API
                } else if (out == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                } else if (out >= 0) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    boolean cfg = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    boolean decodeOnly =
                            (info.flags & MediaCodec.BUFFER_FLAG_DECODE_ONLY) != 0;
                    boolean selected = info.size > 0 && !cfg && !decodeOnly
                            && info.presentationTimeUs >= targetUs;

                    if (selected) {
                        Image img;
                        try {
                            img = codec.getOutputImage(out);
                        } catch (RuntimeException e) {
                            throw new RuntimeException(
                                    "getOutputImage unsupported by codec", e);
                        }
                        if (img == null) {
                            throw new RuntimeException(
                                    "getOutputImage returned null (unsupported)");
                        }
                        currentImage = img;
                        try {
                            writeLumaCrop(img, info.presentationTimeUs,
                                    x, y, w, h, outputPath);
                        } finally {
                            img.close();
                            currentImage = null;
                            codec.releaseOutputBuffer(out, false);
                        }
                        done = true;
                    } else {
                        codec.releaseOutputBuffer(out, false);
                        if (eos) {
                            throw new RuntimeException("EOS at ptsUs="
                                    + info.presentationTimeUs
                                    + " before target ptsUs=" + targetUs);
                        }
                    }
                }
            }
        } finally {
            if (currentImage != null) {
                try { currentImage.close(); } catch (RuntimeException ignore) {}
            }
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignore) {}
                codec.release();
            }
            if (extractor != null) extractor.release();
        }
    }

    private static void writeLumaCrop(Image image, long ptsUs, int x, int y,
                                      int w, int h, String outputPath) throws Exception {
        Rect crop = image.getCropRect();
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            throw new RuntimeException("decoder image has no planes");
        }
        Image.Plane yPlane = planes[0];
        ByteBuffer buf = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();
        int basePos = buf.position();

        System.out.println("actualPtsUs=" + ptsUs);
        System.out.println("image=" + image.getWidth() + "x" + image.getHeight());
        System.out.println("cropRect=" + crop);
        System.out.println("rowStride=" + rowStride + " pixelStride=" + pixelStride
                + " position=" + basePos + " limit=" + buf.limit());

        if (crop.isEmpty()) throw new RuntimeException("empty crop rect");
        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            throw new RuntimeException("invalid crop request");
        }
        if ((long) x + w > crop.width() || (long) y + h > crop.height()) {
            throw new RuntimeException("crop request outside visible frame");
        }

        int absX = crop.left + x;
        int absY = crop.top + y;
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        if (absX < 0 || absY < 0 || (long) absX + w > imgW || (long) absY + h > imgH) {
            throw new RuntimeException("crop outside image bounds");
        }

        long last = (long) basePos
                + (long) (absY + h - 1) * rowStride
                + (long) (absX + w - 1) * pixelStride;
        if (last < 0 || last >= buf.limit()) {
            throw new RuntimeException("crop outside luma buffer bounds");
        }

        System.out.println("clip requested=" + x + "," + y + "," + w + "," + h
                + " (relative to visible frame)");
        System.out.println("clip absolute=" + absX + "," + absY + "," + w + "," + h);

        byte[] out = new byte[w * h];
        int o = 0;
        for (int row = 0; row < h; row++) {
            int rowBase = basePos + (absY + row) * rowStride + absX * pixelStride;
            for (int col = 0; col < w; col++) {
                out[o++] = buf.get(rowBase + col * pixelStride);
            }
        }

        FileOutputStream fos = new FileOutputStream(outputPath);
        try {
            fos.write(out);
        } finally {
            fos.close();
        }
        System.out.println("wrote " + out.length + " raw Y bytes -> " + outputPath);
        System.out.println("note: raw coded luma samples, unchanged (limited range 16-235"
                + " for limited-range sources)");
    }
}
