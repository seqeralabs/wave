package io.seqera.docker

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
interface DockerAuthProvider {

    String getTokenForImage(String image)

    void cleanTokenForImage(String image)

}