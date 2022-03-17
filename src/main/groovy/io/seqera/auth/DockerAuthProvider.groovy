package io.seqera.auth

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
interface DockerAuthProvider {

    boolean isSsl()

    String getTokenFor(String image)

    void cleanTokenFor(String image)

}