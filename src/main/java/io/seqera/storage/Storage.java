package io.seqera.storage;


import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Storage {

    Optional<DigestStore> getManifest(String path);

    DigestStore saveManifest(String path, String manifest, String type, String digest);

    Optional<DigestStore> getBlob(String path);

    DigestStore saveBlob(String path, byte[] content, String type, String digest);

    DigestStore saveBlob(String path, Path content, String type, String digest);

    InputStream wrapInputStream(String path, InputStream inputStream, String type, String digest);
}
