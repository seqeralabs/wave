package io.seqera.cache;

import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Cache {

    ResponseCache get(String path);

    Cache put(String path, byte[] bytes, String type, String digest);

    Optional<ResponseCache> find(String path);
}
