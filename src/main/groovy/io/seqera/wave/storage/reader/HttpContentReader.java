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

package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;

import io.seqera.wave.http.HttpClientFactory;
import org.apache.commons.io.IOUtils;
import static io.seqera.wave.WaveDefault.HTTP_SERVER_ERRORS;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class HttpContentReader implements ContentReader {

    final private String url;

    public HttpContentReader(String url) {
        this.url = url;
    }

    @Override
    public byte[] readAllBytes() throws IOException, InterruptedException {
        try(InputStream stream = openStream()) {
            return stream.readAllBytes();
        }
    }

    @Override
    public InputStream openStream() throws IOException, InterruptedException {
        final HttpClient client = HttpClientFactory.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if( HTTP_SERVER_ERRORS.contains(resp.statusCode()) ) {
            final String err = IOUtils.toString(resp.body(), Charset.defaultCharset());
            final String msg = String.format("Unexpected server response code %d for request %s - message: %s", resp.statusCode(), url, err);
            throw new HttpServerRetryableErrorException(msg);
        }
        return resp.body();
    }

    public String getUrl() { return url; }

    @Override
    public String toString() {
        return String.format("HttpContentReader(%s)",url);
    }

    @Override
    public String toLogString() {
        return String.format("location=%s", url);
    }
}
