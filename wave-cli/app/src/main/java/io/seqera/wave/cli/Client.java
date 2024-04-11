/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.cli;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.function.CheckedSupplier;
import io.seqera.wave.api.BuildStatusResponse;
import io.seqera.wave.api.ContainerInspectRequest;
import io.seqera.wave.api.ContainerInspectResponse;
import io.seqera.wave.api.ServiceInfo;
import io.seqera.wave.api.ServiceInfoResponse;
import io.seqera.wave.api.SubmitContainerTokenRequest;
import io.seqera.wave.api.SubmitContainerTokenResponse;
import io.seqera.wave.cli.config.RetryOpts;
import io.seqera.wave.cli.exception.BadClientResponseException;
import io.seqera.wave.cli.exception.ClientConnectionException;
import io.seqera.wave.cli.json.JsonHelper;
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

    ContainerInspectResponse inspect(ContainerInspectRequest request) {
        final String body = JsonHelper.toJson(request);
        final URI uri = URI.create(endpoint + "/v1alpha1/inspect");
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
                return JsonHelper.fromJson(resp.body(), ContainerInspectResponse.class);
            else {
                String msg = String.format("Wave invalid response: [%s] %s", resp.statusCode(), resp.body());
                throw new BadClientResponseException(msg);
            }
        }
        catch (IOException | FailsafeException e) {
            throw new ClientConnectionException("Unable to connect Wave service: " + endpoint, e);
        }
    }

    SubmitContainerTokenResponse submit(SubmitContainerTokenRequest request) {
        final String body = JsonHelper.toJson(request);
        final URI uri = URI.create(endpoint + "/v1alpha2/container");
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
                throw new BadClientResponseException(msg);
            }
        }
        catch (IOException | FailsafeException e) {
            throw new ClientConnectionException("Unable to connect Wave service: " + endpoint, e);
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

    protected String protocol(String endpoint) {
        if( StringUtils.isEmpty(endpoint) )
            return "https://";
        try {
            return new URL(endpoint).getProtocol() + "://";
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid endpoint URL: " + endpoint, e);
        }
    }

    protected URI imageToManifestUri(String image) {
        final int p = image.indexOf('/');
        if( p==-1 ) throw new IllegalArgumentException("Invalid container name: "+image);
        final String result = protocol(endpoint) + image.substring(0,p) + "/v2" + image.substring(p).replace(":","/manifests/");
        return URI.create(result);
    }

    void awaitCompletion(String buildId, Duration await) {
        log.debug("Waiting for build completion: {} - timeout: {} Seconds", buildId, await.toSeconds());
        final long startTime = Instant.now().toEpochMilli();
        while (!isComplete(buildId)) {
            if (System.currentTimeMillis() - startTime > await.toMillis()) {
                break;
            }
        }
    }

    protected boolean isComplete(String buildId) {
        final String statusEndpoint = endpoint + "/v1alpha1/builds/"+buildId+"/status";
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(statusEndpoint))
                .headers("Content-Type","application/json")
                .GET()
                .build();

        try {
            //interval of 10 seconds
            TimeUnit.SECONDS.sleep(10);

            final HttpResponse<String> resp = httpSend(req);
            log.debug("Wave response: statusCode={}; body={}", resp.statusCode(), resp.body());
            if( resp.statusCode()==200 ) {
                BuildStatusResponse result = JsonHelper.fromJson(resp.body(), BuildStatusResponse.class);
                return result.status == BuildStatusResponse.Status.COMPLETED;
            }
            else {
                String msg = String.format("Wave invalid response: [%s] %s", resp.statusCode(), resp.body());
                throw new BadClientResponseException(msg);
            }
        }
        catch (IOException | FailsafeException | InterruptedException e) {
            throw new ClientConnectionException("Unable to connect Wave service: " + endpoint, e);
        }
    }

    ServiceInfo serviceInfo() {
        final URI uri = URI.create(endpoint + "/service-info");
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .headers("Content-Type","application/json")
                .GET()
                .build();

        try {
            final HttpResponse<String> resp = httpSend(req);
            log.debug("Wave response: statusCode={}; body={}", resp.statusCode(), resp.body());
            if( resp.statusCode()==200 )
                return JsonHelper.fromJson(resp.body(), ServiceInfoResponse.class).serviceInfo;
            else {
                String msg = String.format("Wave invalid response: [%s] %s", resp.statusCode(), resp.body());
                throw new BadClientResponseException(msg);
            }
        }
        catch (IOException | FailsafeException e) {
            throw new ClientConnectionException("Unable to connect Wave service: " + endpoint, e);
        }
    }
}
