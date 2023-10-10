/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.seqera.wave.service.aws.AwsEcrService
import io.seqera.wave.util.StringUtils
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
            final isPublic = host.account==null
            final token = awsEcrService.getLoginToken(userName, password, host.region, isPublic)
            // token is made up by the aws username and password separated by a `:`
            final parts = token.tokenize(':')
            // wrap and return it
            return credentials(parts[0], parts[1])
        }
        else {
            credentials(userName, password)
        }
    }

    protected RegistryCredentials credentials(final String usr, final String pwd) {
        new RegistryCredentials() {
    
            @Override
            String getUsername() {
                return usr
            }

            @Override
            String getPassword() {
                return pwd
            }

            boolean equals(Object object) {
                if (this.is(object))
                    return true
                if (object == null)
                    return false
                // type check and cast
                if (getClass() != object.getClass())
                    return false
                final that = (RegistryCredentials) object
                return Objects.equals(usr, that.getUsername())
                        && Objects.equals(pwd, that.getPassword());
            }

            @Override
            int hashCode() {
                return Objects.hash(usr, pwd)
            }

            String toString() {
                return "RegistryCredentials[username=${usr}; password=${StringUtils.redact(pwd)}]"
            }
        }
    }
}
