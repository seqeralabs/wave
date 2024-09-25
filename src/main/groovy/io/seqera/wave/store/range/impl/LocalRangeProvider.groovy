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

package io.seqera.wave.store.range.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
/**
 * Local based implementation for a range set
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalRangeProvider implements RangeProvider {

    private Map<String,Map<String,Double>> store = new HashMap<>()

    @Override
    void add(String key, String element, double score) {
        final map = store.getOrDefault(key, [:])
        map.put(element, score)
        store.put(key, map)
        log.trace "* add range - store: $store"
    }

    @Override
    List<String> getRange(String key, double min, double max, int count, boolean remove) {
        final map = store.getOrDefault(key, [:])
        final result = new ArrayList<String>()
        for( Map.Entry<String,Double> entry : map.entrySet().sort(it->it.value) ) {
            if( result.size()>=count )
                break
            if( entry.value>=min && entry.value<=max ) {
                result.add(entry.key)
                if( remove )
                    map.remove(entry.key)
            }
        }
        log.trace "* get range result=$result - store: $store"
        return result
    }
}
