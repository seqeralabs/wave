/*
 * Copyright 2025, Seqera Labs
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

package io.seqera.wave.core.spec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.squareup.moshi.Moshi;
import static io.seqera.wave.core.spec.Helper.asLong;

/**
 * Model a container object reference i.e. manifest or blob
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ObjectRef {

    public String mediaType;
    public String digest;
    public Long size;
    public Map<String,String> annotations;

    /* REQUIRED BY SERIALIZATION */
    private ObjectRef() {}

    public ObjectRef(String mediaType, String digest, Long size, Map<String,String> annotations) {
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
        this.annotations = annotations;
    }

    public ObjectRef(ObjectRef that) {
        this.mediaType = that.mediaType;
        this.digest = that.digest;
        this.size = that.size;
        this.annotations = that.annotations;
    }

    static public ObjectRef of(String json) {
        Moshi moshi = new Moshi.Builder().build();
        try {
            return of(moshi.adapter(Map.class).fromJson(json));
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse ObjectRef - offending value: " + json, e);
        }
    }

    static public List<ObjectRef> of(List<Map> values) {
        return values!=null && !values.isEmpty()
            ? values.stream().map(ObjectRef::of).collect(Collectors.toList())
            : List.<ObjectRef>of();
    }

    static public ObjectRef of(Map<String,?> object) {
        return new ObjectRef(
                (String) object.get("mediaType"),
                (String) object.get("digest"),
                asLong(object.get("size")),
                (Map<String,String>) object.get("annotations"));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ObjectRef objectRef = (ObjectRef) object;
        return Objects.equals(mediaType, objectRef.mediaType) && Objects.equals(digest, objectRef.digest) && Objects.equals(size, objectRef.size) && Objects.equals(annotations, objectRef.annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaType, digest, size, annotations);
    }

    @Override
    public String toString() {
        return "ObjectRef{" +
                "mediaType='" + mediaType + '\'' +
                ", digest='" + digest + '\'' +
                ", size=" + size +
                '}';
    }
}
