//
// Created by longjm on 2025/9/5.
// Improved audio recording implementation with TinyALSA
//
#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <signal.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include "tinyalsa/asoundlib.h"
#include "alsaRecorder.h"
#include "tinyalsa/alsaConfigs.h"
#include "android/log.h"
#include "api/scoped_refptr.h"
#include "api/audio/audio_processing.h"

static const char *TAG = "alsaRecorder_JNI";

#define LOGD(fmt, args...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...)  __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)
#define LOGI(fmt, args...)  __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##args)

/*** IO Variables    ***/
// Global variables for file recording
static recording_context_t *g_file_recording = NULL;
static pthread_mutex_t g_file_mutex = PTHREAD_MUTEX_INITIALIZER;
// Global variables for mic capture callback
static jobject g_dataListener = NULL;
static jmethodID g_onMicPcmRead = NULL;
static recording_context_t *g_mic_recording = NULL;
static pthread_mutex_t g_mic_mutex = PTHREAD_MUTEX_INITIALIZER;
static JavaVM *g_jvm = NULL;

/*** IO Functions ***/
// Function prototypes
static void *recording_thread(void *arg);

static void *mic_capture_thread(void *arg);

static void write_wav_header(FILE *file,
                             struct wav_header *header);

static void update_wav_header(FILE *file,
                              FILE *filePp,
                              FILE *filePp_1ch,
                              unsigned int frames,
                              unsigned int channels,
                              unsigned int sample_rate,
                              enum pcm_format format);

static recording_context_t *
create_recording_context(unsigned int card,
                         unsigned int device,
                         unsigned int channels,
                         unsigned int rate,
                         enum pcm_format format,
                         unsigned int period_size,
                         unsigned int period_count);

static void destroy_recording_context(recording_context_t *ctx);

// Write initial WAV header with placeholder values
static void write_wav_header(FILE *file,
                             struct wav_header *header) {
    fseek(file, 0, SEEK_SET);
    fwrite(header, sizeof(struct wav_header), 1, file);
    fflush(file);
}


// Update WAV header with actual recorded data size
static void update_wav_header(FILE *file,
                              FILE *filePp,
                              FILE *filePp_1ch,
                              unsigned int frames,
                              unsigned int channels,
                              unsigned int sample_rate,
                              enum pcm_format format) {
    struct wav_header header;
    unsigned int bits_per_sample = pcm_format_to_bits(format);
    unsigned int block_align = channels * (bits_per_sample / 8);
    unsigned int data_sz = frames * block_align;

    header.riff_id = ID_RIFF;
    header.riff_sz = data_sz + sizeof(header) - 8;
    header.riff_fmt = ID_WAVE;
    header.fmt_id = ID_FMT;
    header.fmt_sz = 16;
    header.audio_format = FORMAT_PCM;
    header.num_channels = channels;
    header.sample_rate = sample_rate;
    header.bits_per_sample = bits_per_sample;
    header.byte_rate = (bits_per_sample / 8) * channels * sample_rate;
    header.block_align = block_align;
    header.data_id = ID_DATA;
    header.data_sz = data_sz;
    write_wav_header(file, &header);
    write_wav_header(filePp, &header);

    /** 1ch only save the first channel **/
    header.num_channels = 1;
    header.sample_rate = sample_rate;
    header.bits_per_sample = bits_per_sample;
    header.byte_rate = (bits_per_sample / 8) * channels * sample_rate;
    header.block_align = block_align;
    header.data_id = ID_DATA;
    header.data_sz = data_sz;
    write_wav_header(filePp_1ch, &header);
}

// Create and initialize recording context
static recording_context_t *
create_recording_context(unsigned int card,
                         unsigned int device,
                         unsigned int channels,
                         unsigned int rate,
                         enum pcm_format format,
                         unsigned int period_size,
                         unsigned int period_count) {
    recording_context_t *ctx =
            (recording_context_t *) calloc(1, sizeof(recording_context_t));
    if (!ctx) {
        LOGE("Failed to allocate recording context");
        return NULL;
    }

    ctx->card = card;
    ctx->device = device;
    ctx->channels = channels;
    ctx->rate = rate;
    ctx->target_channels = channels;
    ctx->target_rate = rate;
    ctx->format = format;
    ctx->period_size = period_size;
    ctx->period_count = period_count;
    ctx->capturing = 0;
    ctx->frames_captured = 0;

    return ctx;
}

// Cleanup and destroy recording context
static void destroy_recording_context(recording_context_t *ctx) {
    if (!ctx) return;

    if (ctx->buffer) {
        free(ctx->buffer);
        ctx->buffer = NULL;
    }

    if (ctx->pcm) {
        pcm_close(ctx->pcm);
        ctx->pcm = NULL;
    }

    if (ctx->file) {
        fclose(ctx->file);
        ctx->file = NULL;
    }
    if (ctx->filePp) {
        fclose(ctx->filePp);
        ctx->filePp = NULL;
    }
    if (ctx->filePp_1ch) {
        fclose(ctx->filePp_1ch);
        ctx->filePp_1ch = NULL;
    }

    free(ctx);
}

// ---------------------------------------------------------------------------
// process_ns_chunk
//
// Runs NS (noise suppression) via WebRTC APM over a single-channel (mono)
// int16_t buffer spanning one or more 10 ms frames.  Any trailing samples
// that don't fill a full 480-sample frame are left untouched.
//
//   buf           : mono int16_t samples (in-place)
//   total_samples : total mono sample count
//   apm           : NS-configured AudioProcessing instance (mono StreamConfig)
//   sc            : StreamConfig with rate=48000, channels=1
// ---------------------------------------------------------------------------
static void process_ns_chunk(int16_t *buf,
                             unsigned int total_samples,
                             webrtc::AudioProcessing *apm,
                             const webrtc::StreamConfig &sc) {
    const unsigned int frame_stride = APM_FRAME_SAMPLES;   // 480 mono samples / 10 ms
    unsigned int offset = 0;

    while (offset + frame_stride <= total_samples) {
        int err = apm->ProcessStream(buf + offset,
                                     sc,
                                     sc,
                                     buf + offset);
        if (err != webrtc::AudioProcessing::kNoError)
            LOGE("NS APM: ProcessStream error %d at offset %u", err, offset);
        offset += frame_stride;
    }

    if (offset < total_samples)
        LOGD("NS APM: %u trailing samples skipped (< one 10 ms frame)",
             total_samples - offset);
}

// ---------------------------------------------------------------------------
// process_aec_reverse_chunk
//
// Registers the playback reference signal into the AEC reverse path.
// Must be called EXACTLY ONCE per period — before any ProcessStream calls —
// regardless of how many mic channels will be processed afterward.
//
// Calling this multiple times per period (e.g. once per mic channel) would
// advance the AEC delay line and adaptive filter N times, corrupting the
// echo path model and breaking Double-Talk Detection.
//
// The ProcessReverseStream output is written into a separate scratch buffer
// (ref_out) to avoid mutating the original reference samples, which would
// cause each subsequent mic channel to see a differently-scaled reference.
//
//   ref_buf       : mono playback reference samples (read-only)
//   ref_out       : scratch buffer for ProcessReverseStream output (same length)
//   total_samples : total mono sample count
//   apm           : AEC-configured AudioProcessing instance
//   sc            : StreamConfig with rate=48000, channels=1
// ---------------------------------------------------------------------------
static void process_aec_reverse_chunk(const int16_t *ref_buf,
                                      int16_t *ref_out,
                                      unsigned int total_samples,
                                      webrtc::AudioProcessing *apm,
                                      const webrtc::StreamConfig &sc) {
    const unsigned int frame_stride = APM_FRAME_SAMPLES;
    unsigned int offset = 0;

    while (offset + frame_stride <= total_samples) {
        // Copy frame into ref_out so ProcessReverseStream writes there,
        // leaving the original ref_buf untouched for subsequent mic channels.
        memcpy(ref_out + offset,
               ref_buf + offset,
               frame_stride * sizeof(int16_t));

        int err = apm->ProcessReverseStream(ref_buf + offset,
                                            sc,
                                            sc,
                                            ref_out + offset);
        if (err != webrtc::AudioProcessing::kNoError)
            LOGE("AEC APM: ProcessReverseStream error %d at offset %u", err, offset);

        offset += frame_stride;
    }

    if (offset < total_samples)
        LOGD("AEC APM: %u reverse trailing samples skipped (< one 10 ms frame)",
             total_samples - offset);
}

// ---------------------------------------------------------------------------
// process_aec_forward_chunk
//
// Applies echo cancellation (forward stream) to a single mono mic channel
// in-place.  process_aec_reverse_chunk MUST have been called once for this
// period before this function is called for any mic channel.
//
//   mic_buf       : mono mic samples (in-place, echo-cancelled output)
//   total_samples : total mono sample count
//   apm           : same AEC-configured AudioProcessing instance
//   sc            : StreamConfig with rate=48000, channels=1
// ---------------------------------------------------------------------------
static void process_aec_forward_chunk(int16_t *mic_buf,
                                      unsigned int total_samples,
                                      webrtc::AudioProcessing *apm,
                                      const webrtc::StreamConfig &sc) {
    const unsigned int frame_stride = APM_FRAME_SAMPLES;
    unsigned int offset = 0;

    while (offset + frame_stride <= total_samples) {
        int err = apm->ProcessStream(mic_buf + offset,
                                     sc,
                                     sc,
                                     mic_buf + offset);
        if (err != webrtc::AudioProcessing::kNoError)
            LOGE("AEC APM: ProcessStream error %d at offset %u", err, offset);

        offset += frame_stride;
    }

    if (offset < total_samples)
        LOGD("AEC APM: %u forward trailing samples skipped (< one 10 ms frame)",
             total_samples - offset);
}

// Recording thread for file-based capture
static void *recording_thread(void *arg) {
    recording_context_t *ctx = (recording_context_t *) arg;
    struct pcm_config config;
    unsigned int bytes_read = 0;

    LOGD("Creating audio proc modules (NS + AEC)");

    // --- NS APM: noise suppression only, no echo canceller ---
    webrtc::AudioProcessing::Config ns_config;
    ns_config.noise_suppression.enabled = true;
    ns_config.noise_suppression.level = ns_config.noise_suppression.kHigh;
    ns_config.echo_canceller.enabled = false;
    ns_config.gain_controller1.enabled = true;
    ns_config.gain_controller1.mode =
            webrtc::AudioProcessing::Config::GainController1::kAdaptiveAnalog;
    ns_config.gain_controller2.enabled = true;
    ns_config.high_pass_filter.enabled = true;

    //rtc::scoped_refptr<webrtc::AudioProcessing> ns_apm =
    //        webrtc::AudioProcessingBuilder().Create();
    //ns_apm->ApplyConfig(ns_config);
    rtc::scoped_refptr<webrtc::AudioProcessing> ns_apm[MIC_CHANNELS];
    for (unsigned int c = 0; c < MIC_CHANNELS; c++) {
        ns_apm[c] = webrtc::AudioProcessingBuilder().Create();
        ns_apm[c]->ApplyConfig(ns_config);
    }

    // --- AEC APM: echo cancellation only, no extra NS (already done above) ---
    webrtc::AudioProcessing::Config aec_config;
    aec_config.echo_canceller.enabled = true;
    aec_config.echo_canceller.mobile_mode = false;
    aec_config.noise_suppression.enabled = false;
    aec_config.gain_controller1.enabled = false;
    aec_config.gain_controller2.enabled = false;
    aec_config.high_pass_filter.enabled = false;

    //rtc::scoped_refptr<webrtc::AudioProcessing> aec_apm =
    //        webrtc::AudioProcessingBuilder().Create();
    //aec_apm->ApplyConfig(aec_config);

    rtc::scoped_refptr<webrtc::AudioProcessing> aec_apm[MIC_CHANNELS];
    for (unsigned int c = 0; c < MIC_CHANNELS; c++) {
        aec_apm[c] = webrtc::AudioProcessingBuilder().Create();
        aec_apm[c]->ApplyConfig(aec_config);
    }


    // Both APMs operate on mono 16 KHz/ 48 kHz frames
    webrtc::StreamConfig stream_config(DEFAULT_RATE, 1);

    LOGD("Recording thread started");

    // Configure PCM
    memset(&config, 0, sizeof(config));
    config.channels = ctx->channels;
    config.rate = ctx->rate;
    config.period_size = ctx->period_size;
    config.period_count = ctx->period_count;
    config.format = ctx->format;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;

    // Open PCM device
    ctx->pcm = pcm_open(ctx->card, ctx->device, PCM_IN, &config);
    if (!ctx->pcm || !pcm_is_ready(ctx->pcm)) {
        LOGE("Unable to open PCM device: %s", pcm_get_error(ctx->pcm));
        ctx->capturing = 0;
        return NULL;
    }

    // Allocate buffer
    ctx->size = pcm_frames_to_bytes(ctx->pcm, pcm_get_buffer_size(ctx->pcm));
    ctx->buffer = (char *) malloc(ctx->size);
    if (!ctx->buffer) {
        LOGE("Unable to allocate %u bytes", ctx->size);
        ctx->capturing = 0;
        return NULL;
    }

    LOGI("Recording started: %u ch, %u Hz, %u bit, period_size=%u",
         ctx->channels, ctx->rate, pcm_format_to_bits(ctx->format), ctx->period_size);

    // Main recording loop
    while (ctx->capturing) {
        int ret = pcm_read(ctx->pcm, ctx->buffer, ctx->size);
        LOGI("Read new PCM data: %d", ctx->size);
        if (ret != 0) {
            LOGE("Error reading PCM data: %d", ret);
            break;
        }

        // Write raw (pre-processing) multichannel data
        size_t written = fwrite(ctx->buffer, 1, ctx->size, ctx->file);
        if (written != ctx->size) {
            LOGE("Error writing to file: wrote %zu of %u bytes", written, ctx->size);
            break;
        }

        // ----------------------------------------------------------------
        // DSP pipeline (16-bit PCM only):
        //
        //  Layout: 8 interleaved channels per frame
        //    ch 0-3  →  microphone / recorded input
        //    ch 4-7  →  system playback reference
        //    ch 7    →  primary reference channel for AEC reverse stream
        //
        //  Processing order — AEC BEFORE NS (critical for DTD correctness):
        //
        //    WebRTC AEC3's Double-Talk Detector cross-correlates the raw mic
        //    signal against its echo estimate.  Running NS first distorts the
        //    mic spectrum non-linearly, weakening that correlation and causing
        //    the DTD to misclassify near-end speech as echo during double-talk.
        //    AEC must always receive the unmodified (post-deinterleave) mic.
        //
        //  Steps:
        //    1. Deinterleave all 8 channels into per-channel mono buffers
        //    2. AEC reverse — inject ch7 reference ONCE into the reverse path
        //                     (calling this once per mic channel would advance
        //                      the echo model N times, corrupting DTD)
        //    3. AEC forward — ProcessStream on each mic channel (0-3)
        //    4. NS  — run noise suppression on each mic channel (0-3)
        //    5. Reinterleave all channels (mic channels now AEC+NS clean)
        // ----------------------------------------------------------------

        if (pcm_format_to_bits(ctx->format) == 16) {
            /*** Deinterleave channels into per-channel mono buffers ***/
            int16_t *interleaved = reinterpret_cast<int16_t *>(ctx->buffer);
            unsigned int total_ch = ctx->channels;               // 8
            unsigned int n_frames = (ctx->size / sizeof(int16_t)) / total_ch;

            // Allocate per-channel mono scratch buffers + one ref output scratch
            int16_t **ch_buf = (int16_t **) malloc(total_ch * sizeof(int16_t *));
            int16_t *ref_out = (int16_t *) malloc(n_frames * sizeof(int16_t));
            bool alloc_ok = (ch_buf != nullptr) && (ref_out != nullptr);

            if (alloc_ok) {
                for (unsigned int c = 0; c < total_ch; c++) {
                    ch_buf[c] = (int16_t *) malloc(n_frames * sizeof(int16_t));
                    if (!ch_buf[c]) {
                        alloc_ok = false;
                        break;
                    }
                }
            }

            if (alloc_ok) {
                // Step 1: Deinterleave  [ch0 ch1 ch2 … ch7 | ch0 …]
                for (unsigned int f = 0; f < n_frames; f++)
                    for (unsigned int c = 0; c < total_ch; c++)
                        ch_buf[c][f] = interleaved[f * total_ch + c];

                // Step 2: AEC reverse — inject playback reference EXACTLY ONCE.
                //   ref_out receives ProcessReverseStream's output so that
                //   ch_buf[REF_CHANNEL_IDX] is never mutated between mic channels.
                for (unsigned int c = 0; c < MIC_CHANNELS; c++) {
                    // ref_out is reused as scratch for each channel's reverse call;
                    // ch_buf[REF_CHANNEL_IDX] is read-only here.
                    process_aec_reverse_chunk(ch_buf[REF_CHANNEL_IDX], ref_out,
                                              n_frames, aec_apm[c].get(), stream_config);

                }

                // Step 3: AEC forward — echo-cancel each mic channel independently.
                //   The reverse path has already been registered once above;
                //   only ProcessStream is called here (no further reverse injection).
                for (unsigned int c = 0; c < MIC_CHANNELS; c++)
                    process_aec_forward_chunk(ch_buf[c], n_frames,
                                              aec_apm[c].get(), stream_config);


                // Step 4: NS — apply noise suppression to each AEC-cleaned mic channel.
                //   Running NS after AEC preserves the raw mic signal that AEC3's
                //   Double-Talk Detector needs, preventing near-end speech suppression.
                for (unsigned int c = 0; c < MIC_CHANNELS; c++)
                    process_ns_chunk(ch_buf[c], n_frames,
                                     ns_apm[c].get(), stream_config);

                // Step 5: Reinterleave all channels back into ctx->buffer
                for (unsigned int f = 0; f < n_frames; f++)
                    for (unsigned int c = 0; c < total_ch; c++)
                        interleaved[f * total_ch + c] = ch_buf[c][f];

                // Step 5: Write only channel 0 (mono) to the output file — skip reinterleave.
                size_t writtenPp_1ch = fwrite(ch_buf[0],
                                              sizeof(int16_t),
                                              n_frames,
                                              ctx->filePp_1ch);
                if (writtenPp_1ch != n_frames) {
                    LOGE("Error writing to filePp: wrote %zu of %u frames", writtenPp_1ch,
                         n_frames);
                    // break; — moved inside alloc_ok block, handle as needed
                }
            } else {
                LOGE("DSP: failed to allocate per-channel scratch buffers — skipping");
            }

            // Free scratch buffers
            if (ref_out) free(ref_out);
            if (ch_buf) {
                for (unsigned int c = 0; c < total_ch; c++)
                    if (ch_buf[c]) free(ch_buf[c]);
                free(ch_buf);
            }
        }

        //// Write post-processing (NS + AEC applied) multichannel data
        size_t writtenPp = fwrite(ctx->buffer, 1, ctx->size, ctx->filePp);
        if (writtenPp != ctx->size) {
            LOGE("Error writing to filePp: wrote %zu of %u bytes", writtenPp, ctx->size);
            break;
        }

        bytes_read += ctx->size;
    }

    ctx->frames_captured = pcm_bytes_to_frames(ctx->pcm, bytes_read);
    LOGI("Recording stopped, captured %u frames", ctx->frames_captured);

    return NULL;
}

// Recording thread for mic capture with callback
static void *mic_capture_thread(void *arg) {
    recording_context_t *ctx = (recording_context_t *) arg;
    struct pcm_config config;
    JNIEnv *env = NULL;
    jint attachResult;

    LOGD("Mic capture thread started");

    // Attach thread to JVM
    // c syntax
    //attachResult = (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
    // c++ syntax
    attachResult = g_jvm->AttachCurrentThread(&env, NULL);

    if (attachResult != JNI_OK || env == NULL) {
        LOGE("Failed to attach thread to JVM");
        ctx->capturing = 0;
        return NULL;
    }

    // Configure PCM
    memset(&config, 0, sizeof(config));
    config.channels = ctx->channels;
    config.rate = ctx->rate;
    config.period_size = ctx->period_size;
    config.period_count = ctx->period_count;
    config.format = ctx->format;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;

    // Open PCM device
    ctx->pcm = pcm_open(ctx->card, ctx->device, PCM_IN, &config);
    if (!ctx->pcm || !pcm_is_ready(ctx->pcm)) {
        LOGE("Unable to open PCM device: %s", pcm_get_error(ctx->pcm));
        ctx->capturing = 0;
        // c syntax
        //(*g_jvm)->DetachCurrentThread(g_jvm);
        // c++ syntax
        g_jvm->DetachCurrentThread();
        return NULL;
    }

    // Allocate buffer
    ctx->size = pcm_frames_to_bytes(ctx->pcm, pcm_get_buffer_size(ctx->pcm));
    ctx->buffer = (char *) malloc(ctx->size);
    if (!ctx->buffer) {
        LOGE("Unable to allocate %u bytes", ctx->size);
        ctx->capturing = 0;
        //c syntax
        //(*g_jvm)->DetachCurrentThread(g_jvm);
        //c++ syntax
        g_jvm->DetachCurrentThread();
        return NULL;
    }

    LOGI("Mic capture started: %u ch, %u Hz, %u bit",
         ctx->channels, ctx->rate, pcm_format_to_bits(ctx->format));

    // Main capture loop
    while (ctx->capturing) {
        int ret = pcm_read(ctx->pcm, ctx->buffer, ctx->size);
        if (ret != 0) {
            LOGE("Error reading PCM data: %d", ret);
            break;
        }

        // Convert bytes to shorts (assuming 16-bit PCM)
        if (pcm_format_to_bits(ctx->format) == 16) {
            jsize shortLen = ctx->size / 2;
            jshort *shortData = (jshort *) malloc(shortLen * sizeof(jshort));
            if (shortData == NULL) {
                LOGE("Failed to allocate short buffer");
                break;
            }

            for (int i = 0; i < shortLen; i++) {
                int byteIndex = i * 2;
                shortData[i] = (jshort) ((ctx->buffer[byteIndex] & 0xFF) |
                                         ((ctx->buffer[byteIndex + 1] & 0xFF) << 8));
            }

            int actualChannels = ctx->channels <= 0 ? 1 : (int) ctx->channels;
            int desiredChannels = ctx->target_channels <= 0 ? 1 : (int) ctx->target_channels;
            if (desiredChannels > actualChannels) {
                desiredChannels = actualChannels;
            }

            int totalFrames = shortLen / actualChannels;
            int decimation = 1;
            if (ctx->target_rate > 0 && ctx->rate > ctx->target_rate) {
                decimation = (int) (ctx->rate / ctx->target_rate);
                if (decimation <= 0) {
                    decimation = 1;
                }
            }

            int resampledFrames = totalFrames / decimation;
            if (resampledFrames <= 0) {
                resampledFrames = totalFrames;
                decimation = 1;
            }

            int resultLen = resampledFrames * desiredChannels;
            jshortArray result = env->NewShortArray(resultLen);
            if (result == NULL) {
                LOGE("Failed to allocate short array");
                free(shortData);
                break;
            }

            jshort *resultData = (jshort *) malloc(resultLen * sizeof(jshort));
            if (resultData == NULL) {
                LOGE("Failed to allocate resampled short buffer");
                env->DeleteLocalRef(result);
                free(shortData);
                break;
            }

            for (int frame = 0; frame < resampledFrames; frame++) {
                int srcFrame = frame * decimation;
                if (srcFrame >= totalFrames) {
                    srcFrame = totalFrames - 1;
                }
                for (int ch = 0; ch < desiredChannels; ch++) {
                    resultData[frame * desiredChannels + ch] =
                            shortData[srcFrame * actualChannels + ch];
                }
            }

            env->SetShortArrayRegion(result, 0, resultLen, resultData);
            env->CallVoidMethod(g_dataListener, g_onMicPcmRead, result, resultLen);

            // Check for exceptions
            //if ((*env)->ExceptionCheck(env)) {
            //    (*env)->ExceptionDescribe(env);
            //    (*env)->ExceptionClear(env);
            //}

            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }

            free(resultData);
            free(shortData);
            env->DeleteLocalRef(result);
        }
    }

    LOGI("Mic capture stopped");

    // Detach thread from JVM
    //(*g_jvm)->DetachCurrentThread(g_jvm);
    g_jvm->DetachCurrentThread();
    return NULL;
}

// JNI: Start file-based recording
extern "C" JNIEXPORT jboolean JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_startCapture(
        JNIEnv *env,
        jobject clazz,
        jstring filePath,
        jstring filePathPp,
        jstring filePathPp_1ch,
        jint card,
        jint device,
        jint channels,
        jint sampleRate,
        jint format) {

    pthread_mutex_lock(&g_file_mutex);

    // Check if already recording
    if (g_file_recording != NULL) {
        LOGE("Recording already in progress");
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }

    // Get file path
    //const char *path = (*env)->GetStringUTFChars(env, filePath, NULL);
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    const char *pathPp = env->GetStringUTFChars(filePathPp, nullptr);
    const char *pathPp_1ch = env->GetStringUTFChars(filePathPp_1ch, nullptr);
    if (!path || !pathPp || !pathPp_1ch) {
        LOGE("Failed to get one or more file paths");
        if (path) env->ReleaseStringUTFChars(filePath, path);
        if (pathPp) env->ReleaseStringUTFChars(filePathPp, pathPp);
        if (pathPp_1ch) env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }

    // Create recording context
    g_file_recording = create_recording_context(
            (unsigned int) card, (unsigned int) device,
            (unsigned int) channels, (unsigned int) sampleRate,
            (enum pcm_format) format, PERIOD_SIZE, PERIOD_COUNT);

    if (!g_file_recording) {
        LOGE("Failed to create recording context");
        //(*env)->ReleaseStringUTFChars(env, filePath, path);
        env->ReleaseStringUTFChars(filePath, path);
        env->ReleaseStringUTFChars(filePathPp, pathPp);
        env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }

    // Open output file
    g_file_recording->file = fopen(path, "wb");
    g_file_recording->filePp = fopen(pathPp, "wb");
    g_file_recording->filePp_1ch = fopen(pathPp_1ch, "wb");

    if (!g_file_recording->file) {
        LOGE("Failed to create file: %s", path);
        destroy_recording_context(g_file_recording);
        g_file_recording = NULL;
        //(*env)->ReleaseStringUTFChars(env, filePath, path);
        env->ReleaseStringUTFChars(filePath, path);
        env->ReleaseStringUTFChars(filePathPp, pathPp);
        env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }
    if (!g_file_recording->filePp) {
        LOGE("Failed to create file: %s", pathPp);
        destroy_recording_context(g_file_recording);
        g_file_recording = NULL;
        //(*env)->ReleaseStringUTFChars(env, filePath, path);
        env->ReleaseStringUTFChars(filePath, path);
        env->ReleaseStringUTFChars(filePathPp, pathPp);
        env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }
    if (!g_file_recording->filePp_1ch) {
        LOGE("Failed to create file: %s", pathPp_1ch);
        destroy_recording_context(g_file_recording);
        g_file_recording = NULL;
        //(*env)->ReleaseStringUTFChars(env, filePath, path);
        env->ReleaseStringUTFChars(filePath, path);
        env->ReleaseStringUTFChars(filePathPp, pathPp);
        env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }

    //(*env)->ReleaseStringUTFChars(env, filePath, path);
    env->ReleaseStringUTFChars(filePath, path);
    env->ReleaseStringUTFChars(filePathPp, pathPp);
    env->ReleaseStringUTFChars(filePathPp_1ch, pathPp_1ch);

    // Write placeholder WAV header
    struct wav_header header;
    memset(&header, 0, sizeof(header));
    header.riff_id = ID_RIFF;
    header.riff_fmt = ID_WAVE;
    header.fmt_id = ID_FMT;
    header.fmt_sz = 16;
    header.audio_format = FORMAT_PCM;
    header.num_channels = channels;
    header.sample_rate = sampleRate;
    header.bits_per_sample = pcm_format_to_bits((enum pcm_format)format);
    header.byte_rate = (header.bits_per_sample / 8) * channels * sampleRate;
    header.block_align = channels * (header.bits_per_sample / 8);
    header.data_id = ID_DATA;
    write_wav_header(g_file_recording->file, &header);

    /** 1ch only save the first channel **/
    header.num_channels = 1;
    header.sample_rate = sampleRate;
    header.bits_per_sample = pcm_format_to_bits((enum pcm_format)format);
    header.byte_rate = (header.bits_per_sample / 8) * channels * sampleRate;
    header.block_align = channels * (header.bits_per_sample / 8);
    header.data_id = ID_DATA;
    write_wav_header(g_file_recording->filePp_1ch, &header);

    // Start recording thread
    g_file_recording->capturing = 1;
    int ret = pthread_create(&g_file_recording->thread, NULL, recording_thread, g_file_recording);
    if (ret != 0) {
        LOGE("Failed to create recording thread: %d", ret);
        destroy_recording_context(g_file_recording);
        g_file_recording = NULL;
        pthread_mutex_unlock(&g_file_mutex);
        return JNI_FALSE;
    }

    pthread_mutex_unlock(&g_file_mutex);
    LOGI("File recording started successfully");
    return JNI_TRUE;
}

// JNI: Stop file-based recording
extern "C" JNIEXPORT void JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_stopCapture(
        JNIEnv *env, jobject clazz) {

    pthread_mutex_lock(&g_file_mutex);

    if (g_file_recording == NULL) {
        LOGD("No recording in progress");
        pthread_mutex_unlock(&g_file_mutex);
        return;
    }

    LOGD("Stopping file recording...");
    g_file_recording->capturing = 0;

    // Wait for thread to finish
    pthread_join(g_file_recording->thread, NULL);

    // Update WAV header with actual size
    if (g_file_recording->file) {
        update_wav_header(g_file_recording->file,
                          g_file_recording->filePp,
                          g_file_recording->filePp_1ch,
                          g_file_recording->frames_captured,
                          g_file_recording->channels,
                          g_file_recording->rate,
                          g_file_recording->format);
    }

    // Cleanup
    destroy_recording_context(g_file_recording);
    g_file_recording = NULL;

    pthread_mutex_unlock(&g_file_mutex);
    LOGI("File recording stopped");
}

// JNI: Start mic capture with callback
extern "C" JNIEXPORT jboolean JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_startMicCapture(
        JNIEnv *env, jobject clazz, jint card, jint device,
        jint channels, jint sampleRate, jint format,
        jint targetChannels, jint targetSampleRate) {

    pthread_mutex_lock(&g_mic_mutex);

    if (g_jvm == NULL && env->GetJavaVM(&g_jvm) != JNI_OK) {
        LOGE("Failed to cache JavaVM");
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    // Check if already capturing
    if (g_mic_recording != NULL) {
        LOGE("Mic capture already in progress");
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    // Release old global references
    if (g_dataListener != NULL) {
        //(*env)->DeleteGlobalRef(env, g_dataListener);
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
    }

    // Create global reference for callback
    //g_dataListener = (*env)->NewGlobalRef(env, clazz);
    g_dataListener = (env)->NewGlobalRef(clazz);
    if (g_dataListener == NULL) {
        LOGE("Failed to create global ref for listener");
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    // Get callback method ID
    //jclass listenerClass = (*env)->GetObjectClass(env, clazz);
    jclass listenerClass = (env)->GetObjectClass(clazz);
    if (listenerClass == NULL) {
        LOGE("Failed to get listener class");
        //(*env)->DeleteGlobalRef(env, g_dataListener);
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    //g_onMicPcmRead = (*env)->GetMethodID(env, listenerClass, "onMicPcmRead", "([SI)V");
    g_onMicPcmRead = (env)->GetMethodID(listenerClass, "onMicPcmRead", "([SI)V");

    if (g_onMicPcmRead == NULL) {
        LOGE("Failed to get onMicPcmRead method ID");
        //(*env)->DeleteGlobalRef(env, g_dataListener);
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    // Create recording context
    g_mic_recording = create_recording_context(
            (unsigned int) card, (unsigned int) device,
            (unsigned int) channels, (unsigned int) sampleRate,
            (enum pcm_format) format, 1024, 4);

    if (!g_mic_recording) {
        LOGE("Failed to create mic recording context");
        //(*env)->DeleteGlobalRef(env, g_dataListener);
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    g_mic_recording->target_channels = targetChannels > 0
                                       ? (unsigned int) targetChannels
                                       : 1;
    g_mic_recording->target_rate = targetSampleRate > 0
                                   ? (unsigned int) targetSampleRate
                                   : (unsigned int) sampleRate;

    // Start capture thread
    g_mic_recording->capturing = 1;
    int ret = pthread_create(&g_mic_recording->thread, NULL, mic_capture_thread, g_mic_recording);
    if (ret != 0) {
        LOGE("Failed to create mic capture thread: %d", ret);
        destroy_recording_context(g_mic_recording);
        g_mic_recording = NULL;
        //(*env)->DeleteGlobalRef(env, g_dataListener);
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
        pthread_mutex_unlock(&g_mic_mutex);
        return JNI_FALSE;
    }

    pthread_mutex_unlock(&g_mic_mutex);
    LOGI("Mic capture started successfully");
    return JNI_TRUE;
}

// JNI: Stop mic capture
extern "C" JNIEXPORT void JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_stopMicCapture(
        JNIEnv *env, jobject clazz) {

    pthread_mutex_lock(&g_mic_mutex);

    if (g_mic_recording == NULL) {
        LOGD("No mic capture in progress");
        pthread_mutex_unlock(&g_mic_mutex);
        return;
    }

    LOGD("Stopping mic capture...");
    g_mic_recording->capturing = 0;

    // Wait for thread to finish
    pthread_join(g_mic_recording->thread, NULL);

    // Cleanup
    destroy_recording_context(g_mic_recording);
    g_mic_recording = NULL;

    if (g_dataListener != NULL) {
        (env)->DeleteGlobalRef(g_dataListener);
        g_dataListener = NULL;
    }

    pthread_mutex_unlock(&g_mic_mutex);
    LOGI("Mic capture stopped");
}
