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
