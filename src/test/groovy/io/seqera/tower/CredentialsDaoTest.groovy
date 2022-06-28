package io.seqera.tower

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class CredentialsDaoTest extends Specification {

    @Inject @Shared CredentialsDao credentialsDao

    def 'should find creds by user' () {
        when:
        def result = credentialsDao.findRegistryCredentialsByUser(1)
        println "creds: $result"
        then:
        result
    }

    def 'should find creds by workspace' () {
        when:
        def result = credentialsDao.findRegistryCredentialsByWorkspaceId(123230369066943)
        then:
        result
    }

}
