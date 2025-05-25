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

package io.seqera.wave.service.persistence.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.transform.CompileStatic
import io.seqera.util.pool.SimplePool
import io.seqera.wave.service.persistence.PostgresIgnore
import io.seqera.wave.util.JacksonHelper
/**
 * Implementation of a mapper for JSON objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class Mapper {

    static private SimplePool<ObjectMapper> defaultMapperPool = new SimplePool<>(()-> defaultMapper())

    static private SimplePool<ObjectMapper> ignoreMapperPool = new SimplePool<>(()-> ignoreMapper())

    static private ObjectMapper defaultMapper() {
        return JacksonHelper.defaultJsonMapper()
    }

    static private ObjectMapper ignoreMapper() {
        JacksonHelper.defaultJsonMapper()
                .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
                    @Override
                    boolean hasIgnoreMarker(AnnotatedMember a) {
                        return a.hasAnnotation(PostgresIgnore) || super.hasIgnoreMarker(a)
                    }
                })
    }

    static String toJson(Object object) {
        if( object==null )
            return null
        return ignoreMapperPool.apply((mapper)-> mapper.writeValueAsString(object))
    }

    static <T> T fromJson(Class<T> type, String... json) {
        if( !json )
            return null
        final mapper = defaultMapperPool.borrow()
        try {
            ObjectNode node = mapper.createObjectNode()
            for( String it : json ) {
                final n = mapper.readTree(it) as ObjectNode
                node.setAll(n)
            }
            return mapper.treeToValue(node, type)
        }
        finally {
            defaultMapperPool.release(mapper)
        }
    }

    static <T> T fromJson(Class<T> type, String json, Map<String, ?> other) {
        if( !json )
            return null
        final mapper = defaultMapperPool.borrow()
        try {
            ObjectNode node = mapper.readTree(json) as ObjectNode
            if( other )
                node.setAll( mapper.valueToTree(other) as ObjectNode )
            return mapper.treeToValue(node, type)
        }
        finally {
            defaultMapperPool.release(mapper)
        }
    }

}
