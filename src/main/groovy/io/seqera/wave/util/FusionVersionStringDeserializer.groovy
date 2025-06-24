/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

package io.seqera.wave.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import groovy.transform.CompileStatic
import io.seqera.wave.service.persistence.migrate.MigrationOnly

/**
 * Custom deserializer for Fusion version strings.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MigrationOnly
@CompileStatic
class FusionVersionStringDeserializer extends JsonDeserializer<String> {
    @Override
    String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isTextual()) {
            return node.asText()
        } else if (node.isObject()) {
            JsonNode numberNode = node.get("number")
            return numberNode != null && !numberNode.isNull() ? numberNode.asText() : null
        }

        return null
    }
}


