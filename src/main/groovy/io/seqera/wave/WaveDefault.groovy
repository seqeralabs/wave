package io.seqera.wave
/**
 * Wave app defaults
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface WaveDefault {

    final static public String DOCKER_IO = 'docker.io'
    final static public String DOCKER_REGISTRY_1 = 'https://registry-1.docker.io'
    final static public String DOCKER_INDEX_V1 = 'https://index.docker.io/v1/'
    final static public String TOWER = 'tower'

    final static public Map<String,List<String>> ACCEPT_HEADERS = Map.of(
            'Accept', List.of(
                    'application/json',
                    'application/vnd.oci.image.index.v1+json',
                    'application/vnd.oci.image.manifest.v1+json',
                    'application/vnd.docker.distribution.manifest.v1+prettyjws',
                    'application/vnd.docker.distribution.manifest.v2+json',
                    'application/vnd.docker.distribution.manifest.list.v2+json' ) )
}
