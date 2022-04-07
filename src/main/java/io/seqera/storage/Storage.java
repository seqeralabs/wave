package io.seqera.storage;


import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Storage {

    boolean containsManifest(String path);

    Optional<DigestStore> getManifest(String path);

    DigestStore saveManifest(String path, String manifest, String type, String digest);

    boolean containsBlob(String path);

    Optional<DigestStore> getBlob(String path);

    DigestStore saveBlob(String path, byte[] content, String type, String digest);

    DigestStore saveBlob(String path, Path content, String type, String digest);

    void asyncSaveBlob(String path, InputStream inputStream, String type, String digest);
}
