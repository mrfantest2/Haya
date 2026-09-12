package win.fantest.localwhisper;

public final class Resampler {
    private static final int HALF_TAPS = 16;

    private Resampler() {}

    /**
     * Band-limited windowed-sinc resampler. WhatsApp Opus is commonly 48 kHz;
     * direct decimation to 16 kHz aliases >8 kHz energy back into speech.
     */
    public static float[] quality(float[] input, int sourceRate, int targetRate) {
        if (input == null || input.length == 0) return new float[0];
        if (sourceRate <= 0 || targetRate <= 0) {
            throw new IllegalArgumentException("Sample rates must be positive");
        }
        if (sourceRate == targetRate) return input.clone();

        int outputLength = Math.max(1, (int) Math.round(
                input.length * (double) targetRate / sourceRate
        ));
        float[] output = new float[outputLength];
        double ratio = sourceRate / (double) targetRate;
        double cutoff = 0.94 * Math.min(1.0, targetRate / (double) sourceRate);

        for (int i = 0; i < outputLength; i++) {
            double center = i * ratio;
            int base = (int) Math.floor(center);
            double sum = 0.0;
            double weightSum = 0.0;

            for (int tap = -HALF_TAPS + 1; tap <= HALF_TAPS; tap++) {
                int index = base + tap;
                if (index < 0 || index >= input.length) continue;

                double x = center - index;
                double sincArg = Math.PI * cutoff * x;
                double sinc = Math.abs(sincArg) < 1e-9
                        ? 1.0
                        : Math.sin(sincArg) / sincArg;

                double normalized = x / HALF_TAPS;
                if (Math.abs(normalized) > 1.0) continue;
                double window = 0.54 + 0.46 * Math.cos(Math.PI * normalized);
                double weight = cutoff * sinc * window;
                sum += input[index] * weight;
                weightSum += weight;
            }

            if (Math.abs(weightSum) > 1e-12) sum /= weightSum;
            output[i] = clamp((float) sum);
        }
        return output;
    }

    // Kept for existing tests/callers; accuracy path now uses quality().
    public static float[] linear(float[] input, int sourceRate, int targetRate) {
        return quality(input, sourceRate, targetRate);
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}
