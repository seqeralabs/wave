package io.seqera.wave.util;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.seqera.wave.configuration.HttpClientConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Retry strategy for {@link java.net.http.HttpClient} using {@link HttpClientConfig}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
public class HttpRetryable {

    @Inject
    private HttpClientConfig config;

    public HttpClientConfig config() { return config; }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return HttpInvokation
                .builder(request, responseBodyHandler)
                .withMaxAttempts( config.getRetryAttempts() )
                .withRetryDelay( config.getRetryDelay() )
                .withRetryMaxDelay( config.getRetryMaxDelay() )
                .withRetryMultiplier( config.getRetryMultiplier() )
                .withRetryOnThrowable( e -> e instanceof IOException )
                .build()
                .invoke();
     }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        try {
            return sendAsync(request, responseBodyHandler) .get();
        }
        catch (ExecutionException e) {
            if( e.getCause() instanceof IOException )
                throw (IOException) e.getCause();
            else
                throw new RuntimeException("Unexpected error handling http request: " + request.uri(), e);
        }
    }

}
