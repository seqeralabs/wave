/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
import groovy.transform.CompileStatic
import io.seqera.util.pool.SimplePool
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.cfg.MapperBuilder
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer
import tools.jackson.dataformat.yaml.YAMLMapper

/**
 * Helper class to handle JSON rendering and parsing
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class JacksonHelper {

    static private SimplePool<ObjectMapper> defaultJsonMapper = new SimplePool<>(()-> defaultJsonMapper())

    static private SimplePool<ObjectMapper> publicJsonMapper = new SimplePool<>(()-> publicViewJsonMapper())

    static private SimplePool<ObjectMapper> yamlMapper = new SimplePool<>(()-> defaultYamlMapper())

    /**
     * NOTE: kept in a separate method on purpose. Choosing the builder inline (ternary or if/else)
     * makes Groovy static compilation infer the type of the first branch ({@code YAMLMapper.Builder})
     * and insert a cast that fails at runtime for {@code JsonMapper.Builder}.
     */
    static private MapperBuilder newBuilder(boolean yaml) {
        if( yaml )
            return YAMLMapper.builder()
        return JsonMapper.builder()
    }

    /**
     * Creates a Jackson 3 mapper builder with the Wave defaults. Jackson 3 mappers are
     * immutable once built, therefore all the configuration is applied on the builder
     * (see {@link #defaultJsonMapperBuilder()} for callers needing further customisation).
     */
    static private MapperBuilder createBuilder0(boolean yaml=false, boolean failOnUnknownProperties=false) {
        // GString serializer
        final module = new SimpleModule()
        module.addSerializer( GString, ToStringSerializer.instance )
        module.addSerializer( String, ToStringSerializer.instance )

        return newBuilder(yaml)
                .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                // restore Jackson 2 defaults changed by Jackson 3 (see adr/20261015-micronaut-5-migration.md)
                .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)   // Groovy @Canonical/final fields
                .enable(MapperFeature.USE_GETTERS_AS_SETTERS)
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                // tolerate enum values not (yet) known to Wave: map them to `null` instead of
                // failing the whole payload — e.g. a `waveBuildNotification` value added on the
                // Tower side would otherwise break user-info deserialization for all builds
                .configure(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                // same semantic as Jackson 2 `setSerializationInclusion(NON_ABSENT)`
                .changeDefaultPropertyInclusion((JsonInclude.Value incl) -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_ABSENT))
                // java.time support is built-in in Jackson 3 (no JavaTimeModule needed)
                .addModule(module)
    }

    static MapperBuilder defaultJsonMapperBuilder() {
        return createBuilder0()
    }

    static ObjectMapper defaultJsonMapper() {
        return createBuilder0().build() as ObjectMapper
    }

    static ObjectMapper publicViewJsonMapper() {
        return createBuilder0().disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build() as ObjectMapper
    }

    static ObjectMapper defaultYamlMapper() {
        return createBuilder0(true, true).build() as ObjectMapper
    }

    /**
     * Converts a JSON string to the parameter object
     *
     * @param str A json formatted string representing a job config object
     * @return A concrete instance of {@code T}
     */
    static <T> T fromJson(String str, Class<T> type) {
        if( str==null )
            return null
        return defaultJsonMapper.apply((mapper)-> mapper.readValue(str, type))
    }

    static <T> T fromJson(String str, TypeReference<T> type) {
        if( str==null )
            return null
        return defaultJsonMapper.apply((mapper)->mapper.readValue(str, type))
    }

    /**
     * Converts a concrete instance of of {@code T} to a json
     * representation
     *
     * @param config A concrete instance of of {@code T}
     * @return A json representation of the specified object
     */
    static String toJson(Object config) {
        if( config==null )
            return null
        return defaultJsonMapper.apply((mapper)-> mapper.writeValueAsString(config))
    }

    static String toJsonWithPublicView(Object config) {
        if( config==null )
            return null
        return publicJsonMapper.apply((mapper)-> mapper.writerWithView(Views.Public).writeValueAsString(config))
    }

    /**
     * Converts a YAML string to the parameter object
     *
     * @param str A yaml formatted string representing a job config object
     * @return A concrete instance of {@code T}
     */
    static <T> T fromYaml(String str, Class<T> type) {
        if( str==null )
            return null
        return yamlMapper.apply((mapper)->mapper.readValue(str, type))
    }

    /**
     * Converts a concrete instance of of {@code T} to a yaml
     * representation
     *
     * @param config A concrete instance of of {@code T}
     * @return A yaml representation of the specified object
     */
    static String toYaml(Object config) {
        if( config==null )
            return null
        return yamlMapper.apply((mapper)->mapper.writeValueAsString(config))
    }

}
