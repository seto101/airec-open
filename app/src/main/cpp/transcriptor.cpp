// Puente JNI mínimo hacia whisper.cpp para ec.paul.grabador.transcripcion.Whisper.
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>

#include "whisper.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "Transcriptor", __VA_ARGS__)

static std::atomic<bool> g_cancelar{false};

struct Avisos {
    JNIEnv * env;
    jobject progreso;
    jmethodID metodo;
};

extern "C" JNIEXPORT jlong JNICALL
Java_ec_paul_grabador_transcripcion_Whisper_iniciar(JNIEnv * env, jobject, jstring ruta) {
    const char * p = env->GetStringUTFChars(ruta, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = false;
    whisper_context * ctx = whisper_init_from_file_with_params(p, cp);
    LOG("modelo %s: %s", p, ctx ? "cargado" : "NO se pudo cargar");
    env->ReleaseStringUTFChars(ruta, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_ec_paul_grabador_transcripcion_Whisper_liberar(JNIEnv *, jobject, jlong ptr) {
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

extern "C" JNIEXPORT void JNICALL
Java_ec_paul_grabador_transcripcion_Whisper_cancelar(JNIEnv *, jobject) {
    g_cancelar = true;
}

/**
 * Transcribe [pcm] (float, 16 kHz, mono). Devuelve una línea por frase: "t0\tt1\ttexto" con t0/t1 en
 * milisegundos relativos al trozo. Cadena vacía si se canceló o falló.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_ec_paul_grabador_transcripcion_Whisper_transcribir(
        JNIEnv * env, jobject, jlong ptr, jfloatArray pcm, jint hilos, jstring idioma, jobject progreso) {
    auto * ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return env->NewStringUTF("");
    g_cancelar = false;

    const char * lang = env->GetStringUTFChars(idioma, nullptr);
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    p.beam_search.beam_size = 5;  // como whisper-cli en la prueba de velocidad
    p.greedy.best_of = 5;
    p.language = lang;
    p.n_threads = hilos;
    p.translate = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.print_special = false;
    // Frases cortas (la app «sigue la lectura» frase a frase).
    p.token_timestamps = true;
    p.split_on_word = true;
    p.max_len = 110;

    Avisos avisos{env, progreso, nullptr};
    if (progreso) {
        jclass k = env->GetObjectClass(progreso);
        avisos.metodo = env->GetMethodID(k, "avance", "(I)V");
        p.progress_callback = [](whisper_context *, whisper_state *, int pct, void * u) {
            auto * a = static_cast<Avisos *>(u);
            a->env->CallVoidMethod(a->progreso, a->metodo, pct);
        };
        p.progress_callback_user_data = &avisos;
    }
    p.abort_callback = [](void *) { return g_cancelar.load(); };

    jsize n = env->GetArrayLength(pcm);
    jfloat * datos = env->GetFloatArrayElements(pcm, nullptr);
    int r = whisper_full(ctx, p, datos, n);
    env->ReleaseFloatArrayElements(pcm, datos, JNI_ABORT);
    env->ReleaseStringUTFChars(idioma, lang);
    if (r != 0 || g_cancelar) {
        LOG("whisper_full: r=%d cancelado=%d", r, (int) g_cancelar.load());
        return env->NewStringUTF("");
    }

    std::string out;
    const int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        out += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10);
        out += '\t';
        out += std::to_string(whisper_full_get_segment_t1(ctx, i) * 10);
        out += '\t';
        std::string t = whisper_full_get_segment_text(ctx, i);
        for (auto & c : t) if (c == '\n' || c == '\t') c = ' ';
        out += t;
        out += '\n';
    }
    return env->NewStringUTF(out.c_str());
}
