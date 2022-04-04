package io.seqera.storage

import com.github.benmanes.caffeine.cache.RemovalCause
import groovy.util.logging.Slf4j
import com.github.benmanes.caffeine.cache.RemovalListener
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
@Singleton
class MemoryStorageListener implements RemovalListener<String, DigestByteArray> {

    private static Logger logger = LoggerFactory.getLogger(MemoryStorageListener.class);

    @Override
    void onRemoval(String key, DigestByteArray value, RemovalCause cause) {
        logger.debug("Removing $key from cache because $cause");
    }
}
