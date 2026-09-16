package uz.murodjon.robotcallv2.storage.application.port.input;

import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.nio.file.Path;

/** Where a finished call recording goes once the call is over. */
public interface AudioStorageUseCase {

    /** Puts the WAV into object storage; null when storage refused it. */
    StoredFile upload(Path file, long companyId, String originalName);

    /** Removes the node's own copy once it is safely uploaded — when configured to. */
    void deleteLocalCopy(Path file);
}
