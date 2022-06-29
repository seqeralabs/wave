package io.seqera.wave.storage;


import java.util.Optional;

import io.seqera.wave.storage.reader.ContentReader;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Storage {

    Optional<DigestStore> getManifest(String path);

    DigestStore saveManifest(String path, String manifest, String type, String digest);

    Optional<DigestStore> getBlob(String path);

    DigestStore saveBlob(String path, byte[] content, String type, String digest);

    DigestStore saveBlob(String path, ContentReader content, String type, String digest);
}
