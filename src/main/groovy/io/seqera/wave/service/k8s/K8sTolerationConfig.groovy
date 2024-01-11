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
package io.seqera.wave.service.k8s

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
/**
 * Model for kubernetes toleration
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@Introspected
@ConfigurationProperties('wave.build.k8s.tolerations')
class K8sTolerationsConfig {
    boolean enabled;
    List<Toleration> arm64;
    List<Toleration> amd64;

    static class Toleration {
        String key;
        String value;
        String operator;
        String effect;
    }
}
