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

package io.seqera.wave.cli.model;

import java.util.List;

import io.seqera.wave.core.spec.ContainerSpec;
import io.seqera.wave.core.spec.ObjectRef;

/**
 * Wrapper for {@link ContainerSpec} that replaces
 * {@link ObjectRef} with {@link LayerRef} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerSpecEx extends ContainerSpec  {
    public ContainerSpecEx(ContainerSpec spec) {
        super(spec);
        // update the layers uri
        if( spec.getManifest()!=null && spec.getManifest().getLayers()!=null ) {
            List<ObjectRef> layers = spec.getManifest().getLayers();
            for( int i=0; i<layers.size(); i++ ) {
                ObjectRef it = layers.get(i);
                String uri = spec.getHostName() + "/v2/" + spec.getImageName() + "/blobs/" + it.digest;
                layers.set(i, new LayerRef(it, uri));
            }
        }
    }
}
