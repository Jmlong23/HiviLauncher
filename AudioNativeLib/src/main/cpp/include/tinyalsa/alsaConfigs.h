#ifndef ALSA_CONFIGS_H
#define ALSA_CONFIGS_H

#include <stdio.h>

// AUDIO INPUT  constants
#define DEFAULT_BLOCK_MS 10
#define DEFAULT_RATE 16000
#define DEFAULT_CHANNELS 8   // 8-ch capture: ch0-3 = mic, ch4-7 = system playback reference

// Channel layout
// Channels 0-3 : microphone / recorded input  → NS + AEC applied
// Channels 4-7 : system playback reference    → fed into AEC reverse stream
// Channel  7   : primary playback reference channel used for AEC reverse stream
#define MIC_CHANNELS      1 //4
#define REF_CHANNELS      4
#define REF_CHANNEL_IDX   6   // index of the primary playback reference channel

// APM  constants
#define APM_RATE          DEFAULT_RATE //16000
#define APM_BLOCK_MS      DEFAULT_BLOCK_MS //10
#define APM_FRAME_SAMPLES (APM_RATE * APM_BLOCK_MS / 1000)   // = 480 if 48k, 160 if 16k
#define PERIOD_SIZE (APM_FRAME_SAMPLES * 4)
#define PERIOD_COUNT 4

// WAV file format constants
#define ID_RIFF 0x46464952
#define ID_WAVE 0x45564157
#define ID_FMT  0x20746d66
#define ID_DATA 0x61746164
#define FORMAT_PCM 1

// WAV file header structure
struct wav_header {
    uint32_t riff_id;
    uint32_t riff_sz;
    uint32_t riff_fmt;
    uint32_t fmt_id;
    uint32_t fmt_sz;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
    uint32_t data_id;
    uint32_t data_sz;
};

// Recording context structure
typedef struct {
    FILE *file;
    FILE *filePp;
    FILE *filePp_1ch;
    struct pcm *pcm;
    char *buffer;
    unsigned int size;
    unsigned int card;
    unsigned int device;
    unsigned int channels;
    unsigned int rate;
    unsigned int target_channels;
    unsigned int target_rate;
    enum pcm_format format;
    unsigned int period_size;
    unsigned int period_count;
    volatile int capturing;
    pthread_t thread;
    unsigned int frames_captured;
} recording_context_t;

#endif // ALSA_CONFIGS_H
