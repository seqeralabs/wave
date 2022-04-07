package io.seqera.config;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
public interface FileStorageConfiguration {

    String getPath();

    boolean isStoreRemotes();

}
