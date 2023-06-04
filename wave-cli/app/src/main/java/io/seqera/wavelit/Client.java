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

    public static String DEFAULT_ENDPOINT = "https://wave.seqera.io";

    private HttpClient httpClient;

    private String endpoint = DEFAULT_ENDPOINT;


    Client() {
        // create http client
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
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
        return safeApply(() -> httpClient.send(req, HttpResponse.BodyHandlers.ofString()));
    }
}
