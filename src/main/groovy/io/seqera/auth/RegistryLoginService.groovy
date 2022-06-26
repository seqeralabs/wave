package io.seqera.auth

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryLoginService {

    boolean validateUser(String registry, String user, String password)

    boolean login(String registry, String username, String password)

    void cleanTokenFor(String image)

    String getTokenFor(String image, RegistryAuth auth, RegistryCredentials creds)

}
