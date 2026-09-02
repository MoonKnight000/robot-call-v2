package uz.murodjon.robotcallv2.storage.application.port.output;

import java.io.InputStream;
import java.nio.file.Path;

/** Outbound port SPI for object storage (MinIO/S3). */
public interface ObjectStoragePort {

    boolean available();

    boolean upload(byte[] data, String bucket, String objectName, String contentType);

    boolean uploadFile(Path file, String bucket, String objectName, String contentType);

    InputStream download(String bucket, String objectName) throws Exception;

    void delete(String bucket, String objectName) throws Exception;
}
