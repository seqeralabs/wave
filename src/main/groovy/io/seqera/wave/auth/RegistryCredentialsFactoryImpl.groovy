package io.seqera.wave.auth

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

/**
 * Define service to create {@link RegistryCredentials} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
class RegistryCredentialsFactoryImpl implements RegistryCredentialsFactory {

    @Override
    RegistryCredentials create(String registry, String userName, String password) {
        credentials(userName, password)
    }

    private RegistryCredentials credentials(final String username, final String password) {
        new RegistryCredentials() {
            @Override
            String getUsername() {
                return username
            }

            @Override
            String getPassword() {
                return password
            }
        }
    }
}
