package io.seqera.config;

import java.time.Duration;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
public interface StorageConfiguration {

    int getMaximumSize();

    Duration getExpireAfter();

}
