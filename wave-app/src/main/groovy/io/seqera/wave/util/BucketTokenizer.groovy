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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Parse a cloud bucket uri and decompose in scheme, bucket and path
 * components eg. s3://foo/some/file.txt
 * - scheme: s3
 * - bucket: foo
 * - path  : /some/file.txt
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode(includeFields = true)
@CompileStatic
class BucketTokenizer {

    private String scheme
    private String bucket
    private String path
    private boolean directory

    String getScheme() { scheme }
    String getBucket() { bucket }
    String getPath() { path }
    boolean isDirectory() { directory }

    protected BucketTokenizer(String s, String b, String p, boolean dir=false) {
        this.scheme = s
        this.bucket = b
        this.path = p
        this.directory = dir
    }

    protected BucketTokenizer() {}

    static BucketTokenizer from(String uri) {
        new BucketTokenizer().parse(uri)
    }

    BucketTokenizer parse(String uri) {
        final m = StringUtils.URL_PROTOCOL.matcher(uri)
        if( !m.matches() ) {
            return this
        }

        this.scheme = m.group(1)
        final location = m.group(2)

        final p = location.indexOf('/')
        if( p==-1 ) {
            bucket = location
            path = ''
        }
        else {
            bucket = location.substring(0,p)
            path = location.substring(p)
        }

        directory = path.endsWith('/')

        if( bucket.startsWith('/') || bucket.endsWith('/') )
            throw new IllegalArgumentException("Invalid bucket URI path: $uri")

        while(path.endsWith('/'))
            path = path.substring(0,path.length()-1)

        return this
    }

    BucketTokenizer withPath(String newPath) {
        final dir = newPath?.endsWith('/')
        while(newPath.endsWith('/'))
            newPath = newPath.substring(0,newPath.length()-1)
        new BucketTokenizer(this.scheme, this.bucket, newPath, dir)
    }

    String toString() {
        def result = scheme
        if( bucket )
            result += '://' + bucket
        if( bucket && path )
            result += '/' + path
        return result
    }

    /**
     * @return The object key, essentially the same as {@link #path} stripping the leading slash character
     */
    String getKey() {
        path?.startsWith('/') ? path.substring(1) : path
    }

}
