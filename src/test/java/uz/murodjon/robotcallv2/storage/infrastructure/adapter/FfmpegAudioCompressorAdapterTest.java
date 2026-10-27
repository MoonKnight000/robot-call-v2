package uz.murodjon.robotcallv2.storage.infrastructure.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.murodjon.robotcallv2.storage.domain.entity.CompressedAudio;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegAudioCompressorAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsUncompressedWhenCompressionDisabled() throws IOException {
        AudioStorageProperties props = new AudioStorageProperties(
                true, "http://localhost:9000", "key", "secret", "files", true, false, "ffmpeg", 32);
        FfmpegAudioCompressorAdapter adapter = new FfmpegAudioCompressorAdapter(props);

        Path wav = Files.createFile(tempDir.resolve("test.wav"));
        CompressedAudio result = adapter.compress(wav, "test.wav");

        assertThat(result.path()).isEqualTo(wav);
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.isTemporary()).isFalse();
    }

    @Test
    void returnsUncompressedWhenFileDoesNotExist() {
        AudioStorageProperties props = new AudioStorageProperties(
                true, "http://localhost:9000", "key", "secret", "files", true, true, "ffmpeg", 32);
        FfmpegAudioCompressorAdapter adapter = new FfmpegAudioCompressorAdapter(props);

        Path missing = tempDir.resolve("missing.wav");
        CompressedAudio result = adapter.compress(missing, "missing.wav");

        assertThat(result.path()).isEqualTo(missing);
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.isTemporary()).isFalse();
    }

    @Test
    void returnsUncompressedWhenFileIsTooSmall() throws IOException {
        AudioStorageProperties props = new AudioStorageProperties(
                true, "http://localhost:9000", "key", "secret", "files", true, true, "ffmpeg", 32);
        FfmpegAudioCompressorAdapter adapter = new FfmpegAudioCompressorAdapter(props);

        Path small = Files.write(tempDir.resolve("small.wav"), new byte[]{1, 2, 3});
        CompressedAudio result = adapter.compress(small, "small.wav");

        assertThat(result.path()).isEqualTo(small);
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.isTemporary()).isFalse();
    }

    @Test
    void fallsBackGracefullyWhenFfmpegBinaryNotFound() throws IOException {
        AudioStorageProperties props = new AudioStorageProperties(
                true, "http://localhost:9000", "key", "secret", "files", true, true, "invalid_ffmpeg_binary_xyz", 32);
        FfmpegAudioCompressorAdapter adapter = new FfmpegAudioCompressorAdapter(props);

        // Dummy WAV with > 44 bytes
        byte[] dummyWav = new byte[100];
        Path wav = Files.write(tempDir.resolve("dummy.wav"), dummyWav);

        CompressedAudio result = adapter.compress(wav, "dummy.wav");

        assertThat(result.path()).isEqualTo(wav);
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.isTemporary()).isFalse();
    }
}
