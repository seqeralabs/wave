package io.seqera.config;

import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
public interface S3StorageConfiguration {

    Optional<String> getEndpoint();

    String getBucket();

    boolean isStoreRemotes();
}
