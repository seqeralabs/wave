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

package io.seqera.wave.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.seqera.wave.api.ObjectUtils.isEmpty;

/**
 * Model Fusion version info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class FusionVersion {

    static final private Pattern VERSION_JSON = Pattern.compile(".*/v(\\d+(?:\\.\\w+)*)-(\\w*)\\.json$");
    static final private Pattern VERSION_TARGZ = Pattern.compile(".*/pkg\\/(\\d+(?:\\/\\w+)+)\\/fusion-(\\w+)\\.tar\\.gz$");

    final public String number;

    final public String arch;

    FusionVersion(String number, String arch) {
        this.number = number;
        this.arch = arch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FusionVersion that = (FusionVersion) o;
        return Objects.equals(number, that.number) && Objects.equals(arch, that.arch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, arch);
    }

    static FusionVersion from(String uri) {
        if( isEmpty(uri) )
            return null;
        final Matcher matcher_json = VERSION_JSON.matcher(uri);
        if( matcher_json.matches() ) {
            return new FusionVersion(matcher_json.group(1), matcher_json.group(2));
        }
        final Matcher matcher_targz = VERSION_TARGZ.matcher(uri);
        if( matcher_targz.matches() ) {
            return new FusionVersion(matcher_targz.group(1).replaceAll("/", "."),
                    matcher_targz.group(2));
        }
        return null;
    }
}
