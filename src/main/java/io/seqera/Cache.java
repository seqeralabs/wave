package io.seqera;

import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Cache {

    class ResponseCache {
        public byte[] bytes;
        public String mediaType;
        public String digest;
    }

    ResponseCache get(String path);

    Cache put(String path, byte[] bytes, String type, String digest);

    Optional<ResponseCache> find(String path);
}
