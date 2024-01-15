/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
package io.seqera.wave.service.blob

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
/**
 * Model a blob cache metadata entry
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class BlobCacheInfo {

    /**
     * The HTTP location from the where the cached container blob can be retrieved
     */
    final String locationUri

    /**
     * The request http headers
     */
    final Map<String,String> headers

    /**
     * The instant when the cache request was created
     */
    final Instant creationTime

    /**
     * The instant when the cache request completed
     */
    final Instant completionTime

    /**
     * The exit status of the command carried out to store the container
     * blob in the object storage cache
     */
    final Integer exitStatus

    /**
     * The output logs of the command carried out to store the container
     * blob in the object storage cache
     */
    final String logs

    boolean succeeded() {
        locationUri && exitStatus==0
    }

    boolean done() {
        locationUri && completionTime!=null
    }

    String getContentType() {
        headers?.find(it-> it.key.toLowerCase()=='content-type')?.value
    }

    String getCacheControl() {
        headers?.find(it-> it.key.toLowerCase()=='cache-control')?.value
    }

    static BlobCacheInfo create(String locationUrl, Map<String,List<String>> headers) {
        final headers0 = new LinkedHashMap<String,String>()
        for( Map.Entry<String,List<String>> it : headers )
            headers0.put( it.key, it.value.join(',') )
        new BlobCacheInfo(locationUrl, headers0, Instant.now())
    }

    static BlobCacheInfo create1(String locationUrl, Map<String,String> headers) {
        new BlobCacheInfo(locationUrl, headers, Instant.now())
    }

    BlobCacheInfo cached() {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                creationTime,
                0)
    }

    BlobCacheInfo completed(int status, String logs) {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                Instant.now(),
                status,
                logs)
    }

    BlobCacheInfo failed(String logs) {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                Instant.now(),
                null,
                logs)
    }

    BlobCacheInfo withLocation(String uri) {
        new BlobCacheInfo(
                uri,
                headers,
                creationTime,
                completionTime,
                exitStatus,
                logs)
    }

    @Memoized
    static BlobCacheInfo unknown() {
        new BlobCacheInfo(null, null, Instant.ofEpochMilli(0), Instant.ofEpochMilli(0), null) {
            @Override
            BlobCacheInfo withLocation(String uri) {
                // prevent the change of location for unknown status
                return null
            }
        }
    }

}
