package uz.murodjon.robotcallv2.agent.vad;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * The C API of TEN VAD ({@code include/ten_vad.h} in TEN-framework/ten-vad), bound through JNA.
 * Every function returns 0 on success and -1 on failure. {@code size_t} is mapped to a Java
 * {@code long}, which is right on the 64-bit Linux and Windows builds the project ships.
 */
interface TenVadLibrary extends Library {

    /** {@code int ten_vad_create(ten_vad_handle_t *handle, size_t hop_size, float threshold)} */
    int ten_vad_create(PointerByReference handle, long hopSize, float threshold);

    /**
     * {@code int ten_vad_process(ten_vad_handle_t handle, const int16_t *audio_data,
     * size_t audio_data_length, float *out_probability, int *out_flag)} — one hop of 16 kHz audio.
     */
    int ten_vad_process(Pointer handle, short[] audioData, long audioDataLength,
                        FloatByReference outProbability, IntByReference outFlag);

    /** {@code int ten_vad_destroy(ten_vad_handle_t *handle)} */
    int ten_vad_destroy(PointerByReference handle);

    /** {@code const char *ten_vad_get_version(void)} */
    String ten_vad_get_version();
}
