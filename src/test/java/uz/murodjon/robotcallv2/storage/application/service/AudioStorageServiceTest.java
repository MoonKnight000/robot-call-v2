package uz.murodjon.robotcallv2.storage.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.application.port.output.AudioCompressorPort;
import uz.murodjon.robotcallv2.storage.domain.entity.CompressedAudio;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AudioStorageServiceTest {

    private FileStorageUseCase fileStorageUseCase;
    private AudioCompressorPort audioCompressor;
    private AudioStorageProperties properties;
    private AudioStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageUseCase = mock(FileStorageUseCase.class);
        audioCompressor = mock(AudioCompressorPort.class);
        properties = new AudioStorageProperties(true, "http://localhost:9000", "key", "secret", "files", true, true, "ffmpeg", 32);
        service = new AudioStorageService(fileStorageUseCase, properties, audioCompressor);
    }

    @Test
    void uploadStoresCompressedFileAndCleansUpTemp() throws IOException {
        Path originalWav = Files.createFile(tempDir.resolve("original.wav"));
        Path tempMp3 = Files.createFile(tempDir.resolve("temp.mp3"));

        CompressedAudio compressed = new CompressedAudio(tempMp3, "original.mp3", "audio/mpeg", true);
        when(audioCompressor.compress(originalWav, "original.wav")).thenReturn(compressed);

        StoredFile storedFile = new StoredFile(1L, 10L, FileCategory.AUDIO, "original.mp3", "key/original.mp3", "files", ".mp3", 100L, Instant.now());
        when(fileStorageUseCase.store(tempMp3, 10L, FileCategory.AUDIO, "original.mp3", "audio/mpeg")).thenReturn(storedFile);

        StoredFile result = service.upload(originalWav, 10L, "original.wav");

        assertThat(result).isNotNull();
        assertThat(result.originalName()).isEqualTo("original.mp3");
        // Verify temp compressed file was cleaned up
        assertThat(Files.exists(tempMp3)).isFalse();
        // Original wav was not touched by upload cleanup
        assertThat(Files.exists(originalWav)).isTrue();
    }

    @Test
    void uploadStoresUncompressedFileWhenCompressionFails() throws IOException {
        Path originalWav = Files.createFile(tempDir.resolve("fallback.wav"));

        CompressedAudio uncompressed = CompressedAudio.uncompressed(originalWav, "fallback.wav");
        when(audioCompressor.compress(originalWav, "fallback.wav")).thenReturn(uncompressed);

        StoredFile storedFile = new StoredFile(2L, 10L, FileCategory.AUDIO, "fallback.wav", "key/fallback.wav", "files", ".wav", 200L, Instant.now());
        when(fileStorageUseCase.store(originalWav, 10L, FileCategory.AUDIO, "fallback.wav", "audio/wav")).thenReturn(storedFile);

        StoredFile result = service.upload(originalWav, 10L, "fallback.wav");

        assertThat(result).isNotNull();
        assertThat(result.originalName()).isEqualTo("fallback.wav");
        assertThat(Files.exists(originalWav)).isTrue();
    }

    @Test
    void deleteLocalCopyDeletesWhenConfigured() throws IOException {
        Path file = Files.createFile(tempDir.resolve("delete-me.wav"));
        assertThat(Files.exists(file)).isTrue();

        service.deleteLocalCopy(file);

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteLocalCopyDoesNothingWhenDisabled() throws IOException {
        AudioStorageProperties disabledProperties = new AudioStorageProperties(true, "http://localhost:9000", "key", "secret", "files", false);
        AudioStorageService noDeleteService = new AudioStorageService(fileStorageUseCase, disabledProperties, audioCompressor);

        Path file = Files.createFile(tempDir.resolve("keep-me.wav"));
        noDeleteService.deleteLocalCopy(file);

        assertThat(Files.exists(file)).isTrue();
    }
}
