package win.fantest.localwhisper;

public final class WhisperNative {
    static {
        System.loadLibrary("localwhisper");
    }

    private WhisperNative() {}

    public static native long create(String modelPath);
    public static native void destroy(long handle);
    public static native String transcribe(
            long handle,
            float[] pcm16k,
            String language,
            boolean translate,
            boolean includeTimestamps
    );
    public static native String detectedLanguage(long handle);
}
