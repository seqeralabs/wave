package io.seqera.storage;


import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Storage {

    Optional<DigestByteArray> getManifest(String path);

    DigestByteArray saveManifest(String path, String manifest, String type, String digest);

    Optional<DigestByteArray> getBlob(String path);

    DigestByteArray saveBlob(String path, byte[] bytes, String type, String digest);
}
