package io.seqera.util

import io.seqera.Cache
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
class MemoryCache implements Cache{

    Map<String,ResponseCache> target = new HashMap<>()

    ResponseCache get(String path) {
        return target.get(path)
    }

    Cache put(String path, byte[] bytes, String type, String digest) {
        target.put(path, new ResponseCache(bytes: bytes, mediaType: type, digest: digest))
        return this
    }

    @Override
    Optional<ResponseCache> find(String path) {
        Optional.ofNullable(target.get(path))
    }
}
