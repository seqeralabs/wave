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

package io.seqera.wave.service

import spock.lang.Specification

/**
 * Unit tests for ContainerRegistryKeys
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerRegistryKeysTest extends Specification {

    def 'should parse container registry credentials from JSON' () {
        given:
        def json = '''
        {
            "discriminator": "container-reg",
            "userName": "testuser",
            "password": "testpass",
            "registry": "docker.io"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'testuser'
        keys.password == 'testpass'
        keys.registry == 'docker.io'
    }

    def 'should parse AWS credentials with access keys from JSON' () {
        given:
        def json = '''
        {
            "discriminator": "aws",
            "accessKey": "AKIAIOSFODNN7EXAMPLE",
            "secretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'AKIAIOSFODNN7EXAMPLE'
        keys.password == 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
        keys.registry == null
    }

    def 'should parse AWS credentials with IAM role from JSON' () {
        given:
        def json = '''
        {
            "discriminator": "aws",
            "assumeRoleArn": "arn:aws:iam::123456789012:role/MyRole",
            "externalId": "externalId123"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'arn:aws:iam::123456789012:role/MyRole'
        keys.password == 'externalId123'
        keys.registry == null
    }

    def 'should parse AWS credentials with IAM role without external ID from JSON' () {
        given:
        def json = '''
        {
            "discriminator": "aws",
            "assumeRoleArn": "arn:aws:iam::123456789012:role/MyRole"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'arn:aws:iam::123456789012:role/MyRole'
        keys.password == null
        keys.registry == null
    }

    def 'should prioritize IAM role over access keys in AWS credentials' () {
        given:
        def json = '''
        {
            "discriminator": "aws",
            "assumeRoleArn": "arn:aws:iam::123456789012:role/MyRole",
            "externalId": "externalId123",
            "accessKey": "AKIAIOSFODNN7EXAMPLE",
            "secretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'arn:aws:iam::123456789012:role/MyRole'
        keys.password == 'externalId123'
        keys.registry == null
    }

    def 'should throw exception for unsupported discriminator type' () {
        given:
        def json = '''
        {
            "discriminator": "unsupported-type",
            "userName": "testuser",
            "password": "testpass"
        }
        '''

        when:
        ContainerRegistryKeys.fromJson(json)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Unsupported credentials key discriminator type: unsupported-type')
    }

    def 'should handle null registry in container-reg discriminator' () {
        given:
        def json = '''
        {
            "discriminator": "container-reg",
            "userName": "testuser",
            "password": "testpass"
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'testuser'
        keys.password == 'testpass'
        keys.registry == null
    }

    def 'should redact password in toString method' () {
        given:
        def keys = new ContainerRegistryKeys(
            userName: 'testuser',
            password: 'secretpassword',
            registry: 'docker.io'
        )

        when:
        def result = keys.toString()

        then:
        result.contains('registry=docker.io')
        result.contains('userName=testuser')
        result.contains('sec****')
        !result.contains('secretpassword')
    }

    def 'should redact short password in toString method' () {
        given:
        def keys = new ContainerRegistryKeys(
            userName: 'testuser',
            password: 'abc',
            registry: 'docker.io'
        )

        when:
        def result = keys.toString()

        then:
        result.contains('registry=docker.io')
        result.contains('userName=testuser')
        result.contains('****')
        !result.contains('abc')
    }

    def 'should handle null password in toString method' () {
        given:
        def keys = new ContainerRegistryKeys(
            userName: 'testuser',
            password: null,
            registry: 'docker.io'
        )

        when:
        def result = keys.toString()

        then:
        result.contains('registry=docker.io')
        result.contains('userName=testuser')
        result.contains('(null)')
    }

    def 'should handle empty password in toString method' () {
        given:
        def keys = new ContainerRegistryKeys(
            userName: 'testuser',
            password: '',
            registry: 'docker.io'
        )

        when:
        def result = keys.toString()

        then:
        result.contains('registry=docker.io')
        result.contains('userName=testuser')
        result.contains('(empty)')
    }

    def 'should handle various registry hostnames' () {
        given:
        def json = """
        {
            "discriminator": "container-reg",
            "userName": "testuser",
            "password": "testpass",
            "registry": "$registry"
        }
        """

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.registry == registry

        where:
        registry << [
            'docker.io',
            'quay.io',
            '123456789012.dkr.ecr.us-east-1.amazonaws.com',
            'gcr.io',
            'ghcr.io',
            'registry.gitlab.com',
            'myregistry.azurecr.io'
        ]
    }

    def 'should handle JSON with extra fields' () {
        given:
        def json = '''
        {
            "discriminator": "container-reg",
            "userName": "testuser",
            "password": "testpass",
            "registry": "docker.io",
            "extraField": "extraValue",
            "anotherField": 123
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'testuser'
        keys.password == 'testpass'
        keys.registry == 'docker.io'
    }

    def 'should parse AWS credentials with missing optional fields' () {
        given:
        def json = '''
        {
            "discriminator": "aws",
            "assumeRoleArn": "arn:aws:iam::123456789012:role/MyRole",
            "accessKey": null,
            "secretKey": null
        }
        '''

        when:
        def keys = ContainerRegistryKeys.fromJson(json)

        then:
        keys.userName == 'arn:aws:iam::123456789012:role/MyRole'
        keys.password == null
        keys.registry == null
    }
}