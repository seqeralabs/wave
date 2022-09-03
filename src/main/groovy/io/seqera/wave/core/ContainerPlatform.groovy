package io.seqera.wave.core

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.exception.BadRequestException
/**
 * Model a container platform
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerPlatform {

    public static final ContainerPlatform DEFAULT = new ContainerPlatform(DEFAULT_OS, DEFAULT_ARCH)

    private static List<String> ALLOWED_ARCH = ['x86_64', 'amd64', 'arm64', 'arm']
    public static final String DEFAULT_ARCH = 'amd64'
    public static final String DEFAULT_OS = 'linux'

    final String os
    final String arch
    final String variant

    String toString() {
        def result = "$os/$arch"
        if( variant )
            result += "/$variant"
        return result
    }

    static ContainerPlatform of(String value) {
        if( !value )
            return DEFAULT

        final items= value.tokenize('/')
        if( items.size()==1 )
            items.add(0, DEFAULT_OS)

        if( items.size()==2 ) {
            final os = os0(items[0])
            final arch = arch0(items[1])
            final variant = arch=='arm64' ? 'v8' : null as String
            return new ContainerPlatform(os, arch, variant)
        }
        if( items.size()==3 )
            return new ContainerPlatform(os0(items[0]), arch0(items[1]), items[2])

        throw new BadRequestException("Invalid container platform: $value -- offending value: $value")
    }

    static private String arch0(String value) {
        if( !value )
            return DEFAULT_ARCH
        if( value !in ALLOWED_ARCH) throw new BadRequestException("Unsupported container platform: $value")
        return value == 'x86_64' ? 'amd64' : value
    }

    static private String os0(String value) {
        return value ?: DEFAULT_OS
    }

}
