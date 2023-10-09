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

package io.seqera.wave.cli.util;

import java.time.Instant;

import io.seqera.wave.api.SubmitContainerTokenResponse;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Helper methods to handle YAML content
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class YamlHelper {

    public static String toYaml(SubmitContainerTokenResponse resp) {
        final DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        final Representer representer = new Representer(opts) {
            {
                addClassTag(SubmitContainerTokenResponse.class, Tag.MAP);
                representers.put(Instant.class, data -> representScalar(Tag.STR, data.toString()));
            }
        };

        Yaml yaml = new Yaml(representer, opts);
        return yaml.dump(resp);
    }
}
