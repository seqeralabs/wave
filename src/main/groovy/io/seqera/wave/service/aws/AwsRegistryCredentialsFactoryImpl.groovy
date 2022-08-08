package io.seqera.wave.service.aws

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsFactory
import io.seqera.wave.auth.RegistryCredentialsFactoryImpl
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Define service to create {@link io.seqera.wave.auth.RegistryCredentials} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
@Requires(env = 'ec2')
@Replaces(RegistryCredentialsFactoryImpl)
class AwsRegistryCredentialsFactoryImpl implements RegistryCredentialsFactory {

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
