/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import static io.seqera.wave.WaveDefault.HTTP_RETRYABLE_ERRORS;

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
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
        HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if( HTTP_RETRYABLE_ERRORS.contains(resp.statusCode()) ) {
            final String err = IOUtils.toString(resp.body(), Charset.defaultCharset());
            final String msg = String.format("Unexpected server response code %d for request 'GET %s' - message: %s", resp.statusCode(), url, err);
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
