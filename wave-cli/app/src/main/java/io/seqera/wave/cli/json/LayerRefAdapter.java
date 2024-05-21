/*
 * Copyright 2024, Seqera Labs
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

import com.squareup.moshi.ToJson;
import io.seqera.wave.cli.model.LayerRef;
import io.seqera.wave.core.spec.ObjectRef;
/**
 * Layer Ref adapter for Moshi JSON serialisation
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
public class LayerRefAdapter{

    @ToJson
    public LayerRef toJson(ObjectRef objectRef) {
        if(objectRef instanceof LayerRef) {
            return (LayerRef) objectRef;
        } else {
            return new LayerRef(objectRef, null);
        }
    }
}
