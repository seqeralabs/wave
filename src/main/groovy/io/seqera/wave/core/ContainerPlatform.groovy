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

package io.seqera.wave.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.exception.BadRequestException
/**
 * Model a container platform
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode(includes = 'platforms')
@CompileStatic
class ContainerPlatform {

    public static final List<String> ARM64 = ['arm64', 'aarch64']
    private static final List<String> V8 = ['8','v8']
    public static final List<String> AMD64 = ['amd64', 'x86_64', 'x86-64']
    public static final List<String> ALLOWED_ARCH = AMD64 + ARM64 + ['arm']
    public static final String DEFAULT_ARCH = 'amd64'
    public static final String DEFAULT_OS = 'linux'

    @EqualsAndHashCode
    @CompileStatic
    static class Platform implements Serializable {
        final String os
        final String arch
        final String variant

        Platform(String os, String arch, String variant=null) {
            this.os = os
            this.arch = arch
            this.variant = variant
        }

        static Platform of(String value) {
            if( !value )
                throw new BadRequestException("Missing container platform attribute")

            final items = value.tokenize('/')
            if( items.size()==1 )
                items.add(0, DEFAULT_OS)

            if( items.size()==2 || items.size()==3 ) {
                final os = os0(items[0])
                final arch = arch0(items[1])
                // variant v8 for amd64 is normalised to empty
                // see https://github.com/containerd/containerd/blob/v1.4.3/platforms/database.go#L96
                final variant = variant0(arch, items[2])
                return new Platform(os, arch, variant)
            }

            throw new BadRequestException("Invalid container platform: $value -- offending value: $value")
        }

        @Override
        String toString() {
            def result = os + "/" + arch
            if( variant )
                result += "/" + variant
            return result
        }

        static private String arch0(String value) {
            if( !value )
                return DEFAULT_ARCH
            if( value !in ALLOWED_ARCH) throw new BadRequestException("Unsupported container platform: $value")
            // see
            // https://github.com/containerd/containerd/blob/v1.4.3/platforms/database.go#L89
            if( value in AMD64 )
                return AMD64.get(0)
            if( value in ARM64 )
                return ARM64.get(0)
            return  value
        }

        static private String os0(String value) {
            return value ?: DEFAULT_OS
        }

        static private String variant0(String arch, String variant) {
            if( arch in ARM64 && variant in V8 ) {
                // this also address this issue
                //   https://github.com/GoogleContainerTools/kaniko/issues/1995#issuecomment-1327706161
                return null
            }
            if( arch == 'arm' ) {
                // arm defaults to variant v7
                //   https://github.com/containerd/containerd/blob/v1.4.3/platforms/database.go#L89
                if( (!variant || variant=='7') ) return 'v7'
                if( variant in ['5','6','8']) return 'v'+variant
            }
            return variant
        }
    }

    public static final ContainerPlatform DEFAULT = new ContainerPlatform([new Platform(DEFAULT_OS, DEFAULT_ARCH)])

    public static final ContainerPlatform MULTI_PLATFORM = new ContainerPlatform([
            new Platform('linux', 'amd64'),
            new Platform('linux', 'arm64')
    ])

    final List<Platform> platforms

    ContainerPlatform(String os, String arch, String variant=null) {
        this.platforms = List.of(new Platform(os, arch, variant))
    }

    private ContainerPlatform(List<Platform> platforms) {
        assert platforms.size() >= 1, "Platform list must not be empty"
        this.platforms = List.copyOf(platforms)
    }

    String getOs() { platforms[0].os }

    String getArch() { platforms[0].arch }

    String getVariant() { platforms[0].variant }

    boolean isMultiArch() {
        return platforms.size() > 1
    }

    @JsonValue
    String toString() {
        return platforms.collect { it.toString() }.join(',')
    }

    boolean matches(Map<String,String> record) {
        return sameOs(record) && sameArch(record) && sameVariant(record)
    }

    private boolean sameOs(Map<String,String> record) {
        this.os == record.os
    }

    private boolean sameArch(Map<String,String> record) {
        if( this.arch==record.architecture )
            return true
        if( this.arch=='amd64' && record.architecture in AMD64 )
            return true
        if( this.arch=='arm64' && record.architecture in ARM64 )
            return true
        else
            return false
    }

    private boolean sameVariant(Map<String,String> record) {
        if( this.variant == record.variant )
            return true
        if( this.arch=='arm64' ) {
            if( !this.variant && (!record.variant || record.variant in V8))
                return true
            if( this.variant && record.variant==this.variant )
                return true
        }
        return false
    }

    static ContainerPlatform parseOrDefault(String value, ContainerPlatform defaultPlatform=DEFAULT) {
        return value ? of(value) : defaultPlatform
    }

    /**
     * Validate that the given platform string is a single platform value (not multi-arch).
     * Throws {@link BadRequestException} if the value contains comma-separated platforms.
     *
     * @param value The platform string to validate
     */
    static void validateSinglePlatform(String value) {
        if( value && value.contains(',') )
            throw new BadRequestException("Container multi-platform architecture not allowed - offending value: $value")
    }

    @JsonCreator
    static ContainerPlatform of(String value) {
        if( !value )
            throw new BadRequestException("Missing container platform attribute")

        return new ContainerPlatform(value
                    .tokenize(',')
                    .collect(it-> Platform.of(it.trim())))
    }
}
