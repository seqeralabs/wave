/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.filter;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import groovy.transform.CompileStatic;
import io.micronaut.configuration.metrics.binder.web.WebMetricsPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

/**
 * Response publisher inspired to {@link WebMetricsPublisher}
 * @param <T>
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@SuppressWarnings("PublisherImplementation")
class TraceSlowEndpointPublisher<T extends HttpResponse<?>> extends Flux<T> {

    private static final Logger log = LoggerFactory.getLogger(TraceSlowEndpointPublisher.class);

    private final Flux<T> publisher;

    private final long begin;

    private final Duration duration;

    private final HttpRequest<?> request;

    TraceSlowEndpointPublisher(Publisher<T> publisher, HttpRequest<?> request, long begin, Duration duration) {
        this.publisher = Flux.from(publisher);
        this.begin = begin;
        this.duration = duration;
        this.request = request;
    }

    /**
     * Called for publisher.
     *
     * @param actual the original subscription
     */
    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        final CoreSubscriber<T> subscriber = new CoreSubscriber<T>() {

            @Override
            public Context currentContext() {
                return actual.currentContext();
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            public void onNext(T response) {
                try {
                    actual.onNext(response);
                }
                finally {
                    traceResponse(response);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                actual.onError(throwable);
            }

            @Override
            public void onComplete() {
                actual.onComplete();
            }
        };
        publisher.subscribe(subscriber);
    }

    protected void traceResponse(HttpResponse<?> response) {
        final long delta = System.currentTimeMillis()-begin;
        if( delta>=duration.toMillis() )
            log.warn("Slow request detected - elapsed time {}\n{}", Duration.ofMillis(delta), dumpRequest(request,response));
    }

    @CompileStatic
    protected String dumpRequest(HttpRequest<?> request, HttpResponse<?> response) {
        final StringBuilder result = new StringBuilder();
        result.append("- ").append(request.toString()).append('\n');
        // dump headers
        result.append("- request headers: \n");
        for( Map.Entry<String, List<String>> header : request.getHeaders() ) {
            result  .append("   ")
                    .append(header.getKey())
                    .append('=')
                    .append(String.join(", ", header.getValue()))
                    .append('\n');
        }
        // http status
        result.append("- status: "+response.status().getCode()+" "+response.status().name()+"\n");
        return result.toString();
    }
}
