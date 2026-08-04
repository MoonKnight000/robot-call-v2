package uz.murodjon.uysotvoice.storage.dto;

import java.io.InputStream;

/** A resolved {@link StoredFile} plus an open stream on its bytes — {@code FileControllerImpl}'s input. */
public record DownloadableFile(StoredFile meta, InputStream content) {
}
