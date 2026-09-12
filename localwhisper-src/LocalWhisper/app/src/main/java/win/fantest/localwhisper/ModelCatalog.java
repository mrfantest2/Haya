package win.fantest.localwhisper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModelCatalog {
    public static final String QUALITY = "quality";
    public static final String FAST = "fast";

    public static final class ModelInfo {
        public final String id;
        public final String title;
        public final String fileName;
        public final String url;
        public final String sha1;
        public final long bytes;

        public ModelInfo(String id, String title, String fileName, String url, String sha1, long bytes) {
            this.id = id;
            this.title = title;
            this.fileName = fileName;
            this.url = url;
            this.sha1 = sha1;
            this.bytes = bytes;
        }
    }

    private static final Map<String, ModelInfo> MODELS = new LinkedHashMap<>();

    static {
        MODELS.put(QUALITY, new ModelInfo(
                QUALITY,
                "Quality · Large v3 Turbo Q5",
                "ggml-large-v3-turbo-q5_0.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin?download=true",
                "e050f7970618a659205450ad97eb95a18d69c9ee",
                547L * 1024L * 1024L
        ));
        MODELS.put(FAST, new ModelInfo(
                FAST,
                "Fast · Base multilingual",
                "ggml-base.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true",
                "465707469ff3a37a2b9b8d8f89f2f99de7299dac",
                142L * 1024L * 1024L
        ));
    }

    private ModelCatalog() {}

    public static ModelInfo get(String id) {
        ModelInfo info = MODELS.get(id);
        return info != null ? info : MODELS.get(QUALITY);
    }

    public static Map<String, ModelInfo> all() {
        return MODELS;
    }
}
