/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wavelit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.function.CheckedSupplier;
import io.seqera.wave.api.SubmitContainerTokenRequest;
import io.seqera.wave.api.SubmitContainerTokenResponse;
import io.seqera.wavelit.config.RetryOpts;
import io.seqera.wavelit.json.JsonHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bare simple client for Wave service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class Client {

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    final static private String[] REQUEST_HEADERS =  new String[]{
            "Content-Type","application/json",
            "Accept","application/json",
            "Accept","application/vnd.oci.image.index.v1+json",
            "Accept","application/vnd.oci.image.manifest.v1+json",
            "Accept","application/vnd.docker.distribution.manifest.v1+prettyjws",
            "Accept","application/vnd.docker.distribution.manifest.v2+json",
            "Accept","application/vnd.docker.distribution.manifest.list.v2+json" };

    final static private List<Integer> SERVER_ERRORS = List.of(249,502,503,504);

    public static String DEFAULT_ENDPOINT = "https://wave.seqera.io";

    private HttpClient httpClient;

    private String endpoint = DEFAULT_ENDPOINT;


    Client() {
        // create http client
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    SubmitContainerTokenResponse submit(SubmitContainerTokenRequest request) {
        final String body = JsonHelper.toJson(request);
        final URI uri = URI.create(endpoint + "/container-token");
        log.debug("Wave request: {} - payload: {}", uri, request);
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .headers("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            final HttpResponse<String> resp = httpSend(req);
            log.debug("Wave response: statusCode={}; body={}", resp.statusCode(), resp.body());
            if( resp.statusCode()==200 )
                return JsonHelper.fromJson(resp.body(), SubmitContainerTokenResponse.class);
            else {
                String msg = String.format("Wave invalid response: [%s] %s", resp.statusCode(), resp.body());
                throw new IllegalStateException(msg);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to connect Wave service: " + endpoint);
        }
    }

    public Client withEndpoint(String endpoint) {
        if( !StringUtils.isEmpty(endpoint) ) {
            this.endpoint = StringUtils.stripEnd(endpoint, "/");
        }
        return this;
    }

    protected <T> RetryPolicy<T> retryPolicy(Predicate<? extends Throwable> cond) {
        final RetryOpts cfg = new RetryOpts();
        final EventListener<ExecutionAttemptedEvent<T>> listener = new EventListener<ExecutionAttemptedEvent<T>>() {
            @Override
            public void accept(ExecutionAttemptedEvent<T> event) throws Throwable {
                log.debug("Wave connection failure - attempt: " + event.getAttemptCount(), event.getLastFailure());
            }
        };

        return RetryPolicy.<T>builder()
                .handleIf(cond)
                .withBackoff(cfg.delay.toMillis(), cfg.maxDelay.toMillis(), ChronoUnit.MILLIS)
                .withMaxAttempts(cfg.maxAttempts)
                .withJitter(cfg.jitter)
                .onRetry(listener)
                .build();
    }

    protected <T> T safeApply(CheckedSupplier<T> action) {
        final Predicate<? extends Throwable> cond = (e -> e instanceof IOException);
        final RetryPolicy<T> policy = retryPolicy(cond);
        return Failsafe.with(policy).get(action);
    }

    protected HttpResponse<String> httpSend(HttpRequest req)  {
        return safeApply(() -> {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if( SERVER_ERRORS.contains(resp.statusCode())) {
                // throws an IOException so that the condition is handled by the retry policy
                throw new IOException("Unexpected server response code ${resp.statusCode()} - message: ${resp.body()}");
            }
            return resp;
        });
    }


    protected URI imageToManifestUri(String image) {
        final int p = image.indexOf('/');
        if( p==-1 ) throw new IllegalArgumentException("Invalid container name: "+image);
        final String result = "https://" + image.substring(0,p) + "/v2" + image.substring(p).replace(":","/manifests/");
        return URI.create(result);
    }

    protected void awaitImage(String image) {
        final URI manifest = imageToManifestUri(image);
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(manifest)
                .headers(REQUEST_HEADERS)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        final long begin = System.currentTimeMillis();
        final HttpResponse<String> resp = httpSend(req);
        final int code = resp.statusCode();
        if( code>=200 && code<400 ) {
            final long delta = System.currentTimeMillis()-begin;
            log.debug("Wave container available in {} [{}] {}", delta, code, resp.body());
        }
        else {
            String message = String.format("Unexpected response for '%s': [%d] %s", manifest, resp.statusCode(), resp.body());
            throw new IllegalStateException(message);
        }
    }

}
