package io.seqera.auth

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
interface DockerAuthProvider {

    String getTokenFor(String image)

    void cleanTokenFor(String image)

}