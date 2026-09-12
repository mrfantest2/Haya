package win.fantest.localwhisper;

public final class Resampler {
    private Resampler() {}

    public static float[] linear(float[] input, int sourceRate, int targetRate) {
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

        for (int i = 0; i < outputLength; i++) {
            double sourcePosition = i * ratio;
            int left = (int) Math.floor(sourcePosition);
            int right = Math.min(left + 1, input.length - 1);
            left = Math.min(left, input.length - 1);
            double fraction = sourcePosition - left;
            output[i] = (float) (input[left] + (input[right] - input[left]) * fraction);
        }
        return output;
    }
}
