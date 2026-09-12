package win.fantest.localwhisper;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class AudioDecoder {
    private static final int TARGET_RATE = 16000;

    public static final class DecodedAudio {
        public final float[] samples;
        public final long durationMs;

        DecodedAudio(float[] samples, long durationMs) {
            this.samples = samples;
            this.durationMs = durationMs;
        }
    }

    private AudioDecoder() {}

    public static DecodedAudio decode(File input) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int track = findAudioTrack(extractor);
            if (track < 0) throw new IOException("No audio track found");

            extractor.selectTrack(track);
            MediaFormat sourceFormat = extractor.getTrackFormat(track);
            String mime = sourceFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("Audio format has no MIME type");

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(sourceFormat, null, null, 0);
            codec.start();

            FloatAccumulator pcm = new FloatAccumulator(256 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            int sampleRate = sourceFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : TARGET_RATE;
            int channels = sourceFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
            long maxPtsUs = 0L;

            while (!outputEnded) {
                if (!inputEnded) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("Decoder input buffer unavailable");
                        int size = extractor.readSampleData(inputBuffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIndex, 0, size, pts, 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        appendPcm(outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN), pcm, channels, pcmEncoding);
                        maxPtsUs = Math.max(maxPtsUs, info.presentationTimeUs);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                }
            }

            float[] mono = pcm.toArray();
            float[] resampled = sampleRate == TARGET_RATE ? mono : Resampler.linear(mono, sampleRate, TARGET_RATE);
            long durationMs = resampled.length * 1000L / TARGET_RATE;
            if (durationMs <= 0 && maxPtsUs > 0) durationMs = maxPtsUs / 1000L;
            return new DecodedAudio(resampled, durationMs);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            extractor.release();
        }
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private static void appendPcm(ByteBuffer buffer, FloatAccumulator out, int channels, int encoding) throws IOException {
        channels = Math.max(1, channels);
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            int frames = buffer.remaining() / (4 * channels);
            for (int frame = 0; frame < frames; frame++) {
                float sum = 0f;
                for (int ch = 0; ch < channels; ch++) sum += buffer.getFloat();
                out.add(clamp(sum / channels));
            }
            return;
        }

        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            int frames = buffer.remaining() / channels;
            for (int frame = 0; frame < frames; frame++) {
                float sum = 0f;
                for (int ch = 0; ch < channels; ch++) {
                    int unsigned = buffer.get() & 0xff;
                    sum += (unsigned - 128) / 128f;
                }
                out.add(clamp(sum / channels));
            }
            return;
        }

        if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_DEFAULT) {
            throw new IOException("Unsupported PCM encoding: " + encoding);
        }

        int frames = buffer.remaining() / (2 * channels);
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int ch = 0; ch < channels; ch++) sum += buffer.getShort() / 32768f;
            out.add(clamp(sum / channels));
        }
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static final class FloatAccumulator {
        private float[] data;
        private int size;

        FloatAccumulator(int initialCapacity) {
            data = new float[Math.max(1024, initialCapacity)];
        }

        void add(float value) {
            if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = value;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
