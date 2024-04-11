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

package io.seqera.wave.cli.json;

import java.io.IOException;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import io.seqera.wave.api.ContainerInspectRequest;
import io.seqera.wave.api.SubmitContainerTokenRequest;
import io.seqera.wave.api.SubmitContainerTokenResponse;
import io.seqera.wave.cli.model.ContainerInspectResponseEx;

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

    public static String toJson(ContainerInspectRequest request) {
        JsonAdapter<ContainerInspectRequest> adapter = moshi.adapter(ContainerInspectRequest.class);
        return adapter.toJson(request);
    }

    public static String toJson(ContainerInspectResponseEx response) {
        JsonAdapter<ContainerInspectResponseEx> adapter = moshi.adapter(ContainerInspectResponseEx.class);
        return adapter.toJson(response);
    }

    public static <T> T fromJson(String json, Class<T> type) throws IOException {
        JsonAdapter<T> adapter = moshi.adapter(type);
        return (T) adapter.fromJson(json);
    }
}
