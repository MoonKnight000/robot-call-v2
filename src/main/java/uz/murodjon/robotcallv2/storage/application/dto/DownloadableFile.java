package uz.murodjon.robotcallv2.storage.application.dto;

import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.io.InputStream;

/** A resolved {@link StoredFile} plus an open stream on its bytes. */
public record DownloadableFile(StoredFile meta, InputStream content) {
}
