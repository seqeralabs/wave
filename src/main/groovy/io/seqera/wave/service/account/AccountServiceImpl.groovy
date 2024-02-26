/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.account

import java.security.MessageDigest

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
/**
 * Implements service for checking account identity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
@ConfigurationProperties('wave')
class AccountServiceImpl implements AccountService {

    private Map<String,String> accounts = Map.of()

    @PostConstruct
    private dumpAccounts() {
        log.info "Creating account service (${accounts.size()})\n${accounts.collect(it-> "- $it.key: '$it.value'").join('\n') }"
    }

    @Override
    boolean isAuthorised(String username, String password) {
        if( !username || !password )
            return false
        if( !accounts.containsKey(username) )
            return false
        final alg = MessageDigest.getInstance("SHA-256")
        final digest = bytesToHex(alg.digest(password.bytes))
        return digest == accounts.get(username)
    }

    protected String digest(String str) {
        final alg = MessageDigest.getInstance("SHA-256")
        return bytesToHex(alg.digest(str.bytes))
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte byt : bytes) result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }
}
