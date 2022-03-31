package io.seqera.auth

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SimpleAuthProvider implements DockerAuthProvider{

    @Override
    String getTokenFor(String image) {
        return ""
    }

    @Override
    void cleanTokenFor(String image) {

    }
}
