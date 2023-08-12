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

package io.seqera.wavelit.json;

import java.io.IOException;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import io.seqera.wave.api.SubmitContainerTokenRequest;
import io.seqera.wave.api.SubmitContainerTokenResponse;
import io.seqera.wavelit.json.ByteArrayAdapter;
import io.seqera.wavelit.json.DateTimeAdapter;
import io.seqera.wavelit.json.PathAdapter;

/**
 * Helper class to encode and decode JSON payloads
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class JsonHelper {

    private static final Moshi moshi = new Moshi.Builder()
            .add(new ByteArrayAdapter())
            .add(new DateTimeAdapter())
            .add(new PathAdapter())
            .build();

    public static String toJson(SubmitContainerTokenRequest request) {
        JsonAdapter<SubmitContainerTokenRequest> adapter = moshi.adapter(SubmitContainerTokenRequest.class);
        return adapter.toJson(request);
    }

    public static String toJson(SubmitContainerTokenResponse response) {
        JsonAdapter<SubmitContainerTokenResponse> adapter = moshi.adapter(SubmitContainerTokenResponse.class);
        return adapter.toJson(response);
    }

    public static <T> T fromJson(String json, Class<T> type) throws IOException {
        JsonAdapter<T> adapter = moshi.adapter(type);
        return (T) adapter.fromJson(json);
    }
}
