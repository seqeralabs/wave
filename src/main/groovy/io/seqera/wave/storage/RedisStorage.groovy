package io.seqera.wave.storage

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.storage.reader.ContentReader
import jakarta.inject.Inject
import jakarta.inject.Singleton

import static io.seqera.wave.storage.DigestStoreEncoder.encode
import static io.seqera.wave.storage.DigestStoreEncoder.decode

/**
 * Implements a redis base cache for {@link DigestStore} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisStorage implements Storage {

    @Inject
    private StatefulRedisConnection<String,String> redisConn

    @Value('${wave.storage.cache.duration:`1h`}')
    private Duration maxDuration

    private String key(String name) { "wave-blobs:$name" }

    @PostConstruct
    private void init() {
        log.info "Redis blob store - duration=$maxDuration"
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        final result = redisConn.sync().get(key(path))
        result!=null ? Optional.of(decode(result)) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        redisConn.sync().psetex(key(path), maxDuration.toMillis(), encode(result))
        return result;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        final result = redisConn.sync().get(key(path))
        result!=null ? Optional.of(decode(result)) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest)
        redisConn.sync().psetex(key(path), maxDuration.toMillis(), encode(result))
        return result
    }

    @Override
    DigestStore saveBlob(String path, ContentReader content, String type, String digest) {
        log.debug "Save Blob ==> $path"
        final result = new LazyDigestStore(content, type, digest);
        redisConn.sync().psetex(key(path), maxDuration.toMillis(), encode(result))
        return result
    }

    void clearCache() {
        redisConn.sync().flushall()
    }
}
