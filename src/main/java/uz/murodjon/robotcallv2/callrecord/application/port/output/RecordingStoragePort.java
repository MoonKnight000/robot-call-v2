package uz.murodjon.robotcallv2.callrecord.application.port.output;

import java.nio.file.Path;

/** Where a finished call's recording is kept once the call is over. */
public interface RecordingStoragePort {

    /**
     * Stores the recording under the company.
     *
     * @return the stored file's id, or {@code null} when nothing was stored
     */
    Long uploadRecording(Path wav, long companyId, String originalName);

    /** Removes the local copy of an uploaded recording, when the deployment keeps none. */
    void deleteLocalCopy(Path wav);
}
