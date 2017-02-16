#ifndef _AUDIO_ENCODER_H_
#define _AUDIO_ENCODER_H_

typedef struct audioencoder_handle audioencoder_handle;

long open_audio_encoder(int sample_rate, int channels, int bitrate, int add_adts_header);

// return output data size
// < 0, error
int encode_audio(long handle, const char *data, int len, char *output, int out_max_len);

int get_buffer_size(long handle);

void close_audio_encoder(long handle);

#endif //_AUDIO_ENCODER_H_

