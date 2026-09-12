#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cstdio>
#include <sstream>
#include <string>
#include <thread>

#include "whisper.h"

#define TAG "LocalWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    whisper_context *ctx = nullptr;
};

std::string jstringToUtf8(JNIEnv *env, jstring value) {
    if (value == nullptr) return "auto";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "auto";
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string formatTime(int64_t centiseconds) {
    const int64_t totalMs = centiseconds * 10;
    const int64_t hours = totalMs / 3600000;
    const int64_t minutes = (totalMs % 3600000) / 60000;
    const int64_t seconds = (totalMs % 60000) / 1000;
    const int64_t millis = totalMs % 1000;

    char buffer[32];
    if (hours > 0) {
        std::snprintf(buffer, sizeof(buffer), "%02lld:%02lld:%02lld.%03lld",
                      static_cast<long long>(hours),
                      static_cast<long long>(minutes),
                      static_cast<long long>(seconds),
                      static_cast<long long>(millis));
    } else {
        std::snprintf(buffer, sizeof(buffer), "%02lld:%02lld.%03lld",
                      static_cast<long long>(minutes),
                      static_cast<long long>(seconds),
                      static_cast<long long>(millis));
    }
    return buffer;
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_win_fantest_localwhisper_WhisperNative_create(
        JNIEnv *env, jclass, jstring modelPath) {
    const std::string path = jstringToUtf8(env, modelPath);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) {
        LOGE("Could not load model: %s", path.c_str());
        return 0;
    }

    auto *engine = new Engine();
    engine->ctx = ctx;
    LOGI("Whisper model loaded");
    return reinterpret_cast<jlong>(engine);
}

extern "C"
JNIEXPORT void JNICALL
Java_win_fantest_localwhisper_WhisperNative_destroy(
        JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    if (engine->ctx != nullptr) whisper_free(engine->ctx);
    delete engine;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_win_fantest_localwhisper_WhisperNative_transcribe(
        JNIEnv *env,
        jclass,
        jlong handle,
        jfloatArray pcm16k,
        jstring language,
        jboolean translate,
        jboolean includeTimestamps) {

    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr || engine->ctx == nullptr || pcm16k == nullptr) {
        return env->NewStringUTF("");
    }

    const jsize sampleCount = env->GetArrayLength(pcm16k);
    if (sampleCount <= 0) return env->NewStringUTF("");

    jfloat *samples = env->GetFloatArrayElements(pcm16k, nullptr);
    if (samples == nullptr) return env->NewStringUTF("");

    std::string lang = jstringToUtf8(env, language);
    if (lang.empty()) lang = "auto";

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = translate == JNI_TRUE;
    params.language = lang.c_str();
    params.no_context = false;
    params.single_segment = false;
    params.suppress_blank = true;
    params.temperature = 0.0f;

    const unsigned int hc = std::max(1u, std::thread::hardware_concurrency());
    params.n_threads = static_cast<int>(std::clamp(hc > 2 ? hc - 2 : hc, 2u, 8u));

    const int rc = whisper_full(engine->ctx, params, samples, sampleCount);
    env->ReleaseFloatArrayElements(pcm16k, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        return env->NewStringUTF("");
    }

    std::ostringstream output;
    const int segmentCount = whisper_full_n_segments(engine->ctx);

    for (int i = 0; i < segmentCount; ++i) {
        const char *segment = whisper_full_get_segment_text(engine->ctx, i);
        if (segment == nullptr) continue;

        if (includeTimestamps == JNI_TRUE) {
            output << "[" << formatTime(whisper_full_get_segment_t0(engine->ctx, i))
                   << " -> " << formatTime(whisper_full_get_segment_t1(engine->ctx, i))
                   << "] ";
        }

        std::string text(segment);
        const auto first = text.find_first_not_of(" \t\r\n");
        if (first != std::string::npos) text = text.substr(first);

        output << text;
        if (includeTimestamps == JNI_TRUE) output << "\n";
        else if (!text.empty() && text.back() != ' ' && text.back() != '\n') output << " ";
    }

    std::string result = output.str();
    while (!result.empty() && std::isspace(static_cast<unsigned char>(result.back()))) {
        result.pop_back();
    }

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_win_fantest_localwhisper_WhisperNative_detectedLanguage(
        JNIEnv *env, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr || engine->ctx == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(engine->ctx);
    const char *lang = whisper_lang_str(id);
    return env->NewStringUTF(lang == nullptr ? "" : lang);
}
