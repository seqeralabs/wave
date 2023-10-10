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

package io.seqera.wave.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import groovy.transform.CompileStatic
/**
 * Helper class to handle JSON rendering and parsing
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class JacksonHelper {

    static private ObjectMapper DEFAULT_JSON_MAPPER = defaultJsonMapper()

    static private ObjectMapper PUBLIC_JSON_MAPPER = publicViewJsonMapper()

    static private ObjectMapper YAML_MAPPER = defaultYamlMapper() 

    static private ObjectMapper createMapper0(boolean yaml=false, boolean failOnUnknownProperties=false) {
        // GString serializer
        final module = new SimpleModule()
        module.addSerializer( GString, new ToStringSerializer() )
        module.addSerializer( String, new ToStringSerializer() )

        return (yaml ? new ObjectMapper(new YAMLFactory()) : new ObjectMapper())
                .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
                .registerModule(new JavaTimeModule())
                .registerModule(module)
    }

    static ObjectMapper defaultJsonMapper() {
        return createMapper0()
    }

    static ObjectMapper publicViewJsonMapper() {
        return defaultJsonMapper() .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
    }

    static ObjectMapper defaultYamlMapper() {
        return createMapper0(true, true)
    }

    /**
     * Converts a JSON string to the parameter object
     *
     * @param str A json formatted string representing a job config object
     * @return A concrete instance of {@code T}
     */
    static <T> T fromJson(String str, Class<T> type) {
        str != null ? DEFAULT_JSON_MAPPER.readValue(str, type) : null
    }

    static <T> T fromJson(String str, TypeReference<T> type) {
        str != null ? DEFAULT_JSON_MAPPER.readValue(str, type) : null
    }

    /**
     * Converts a concrete instance of of {@code T} to a json
     * representation
     *
     * @param config A concrete instance of of {@code T}
     * @return A json representation of the specified object
     */
    static String toJson(Object config) {
        config != null ? DEFAULT_JSON_MAPPER.writeValueAsString(config) : null
    }

    static String toJsonWithPublicView(Object config) {
        config != null ? PUBLIC_JSON_MAPPER.writerWithView(Views.Public).writeValueAsString(config) : null
    }

    /**
     * Converts a YAML string to the parameter object
     *
     * @param str A yaml formatted string representing a job config object
     * @return A concrete instance of {@code T}
     */
    static <T> T fromYaml(String str, Class<T> type) {
        str != null ? YAML_MAPPER.readValue(str, type) : null
    }

    /**
     * Converts a concrete instance of of {@code T} to a yaml
     * representation
     *
     * @param config A concrete instance of of {@code T}
     * @return A yaml representation of the specified object
     */
    static String toYaml(Object config) {
        config != null ? YAML_MAPPER.writeValueAsString(config) : null
    }

}
