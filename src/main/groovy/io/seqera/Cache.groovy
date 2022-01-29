package io.seqera

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class Cache {

    static class ResponseCache {
        byte[] bytes
        String mediaType
        String digest
    }

    Map<String,ResponseCache> target = new HashMap<>()

    ResponseCache get(String path) {
        return target.get(path)
    }

    Cache put(String path, byte[] bytes, String type, String digest) {
        target.put(path, new ResponseCache(bytes: bytes, mediaType: type, digest: digest))
        return this
    }

}
