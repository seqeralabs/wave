package io.seqera.proxy;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
@Deprecated
public interface RemoteDocker {

    HttpResponse<String> getString(String path, Map<String, List<String>> headers);
    HttpResponse<InputStream> getStream(String path, Map<String,List<String>> headers);
    HttpResponse<byte[]> getBytes(String path, Map<String,List<String>> headers);
    HttpResponse<Void> head(String path, Map<String,List<String>> headers);
    HttpResponse<Void> head(URI uri, Map<String,List<String>> headers);
}
