package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.seqera.wave.service.aws.AwsEcrService
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Define service to create {@link RegistryCredentials} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
class RegistryCredentialsFactoryImpl implements RegistryCredentialsFactory {

    @Inject
    AwsEcrService awsEcrService

    @Override
    RegistryCredentials create(String registry, String userName, String password) {

        AwsEcrService.AwsEcrHostInfo host
        if( (host=awsEcrService.getEcrHostInfo(registry)) ) {
            final token = awsEcrService.getLoginToken(userName, password, host.region)
            // token is made up by the aws username and password separated by a `:`
            final parts = token.tokenize(':')
            // wrap and return it
            return credentials(parts[0], parts[1])
        }
        else {
            credentials(userName, password)
        }
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
