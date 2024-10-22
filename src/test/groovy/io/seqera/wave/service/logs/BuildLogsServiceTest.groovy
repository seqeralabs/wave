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

package io.seqera.wave.service.logs

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.seqera.wave.test.AwsS3TestContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildLogsServiceTest extends Specification implements AwsS3TestContainer {

    @Unroll
    def 'should make log key name' () {
        expect:
        new BuildLogServiceImpl(prefix: PREFIX).logKey(BUILD) == EXPECTED

        where:
        PREFIX          | BUILD         | EXPECTED
        null            | null          | null
        null            | '123'         | '123.log'
        'foo'           | '123'         | 'foo/123.log'
        '/foo/bar/'     | '123'         | 'foo/bar/123.log'
    }

    def 'should remove conda lockfile from logs' () {
        def logs = """
                #9 12.23 logs....
                #10 12.24 >> CONDA_LOCK_START
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 << CONDA_LOCK_END
                #11 12.26 logs....""".stripIndent()
        def service = new BuildLogServiceImpl()

        when:
        def result = service.removeCondaLockFile(logs)
        then:
        result == """
             #9 12.23 logs....
             #11 12.26 logs....""".stripIndent()
    }

    @Unroll
    def 'should make conda lock key name' () {
        expect:
        new BuildLogServiceImpl(condaLockPrefix: PREFIX).condaLockKey(BUILD) == EXPECTED

        where:
        PREFIX          | BUILD         | EXPECTED
        null            | null          | null
        null            | '123'         | '123.lock'
        'foo'           | '123'         | 'foo/123.lock'
        '/foo/bar/'     | '123'         | 'foo/bar/123.lock'
    }

    def 'should extract conda lockfile' () {
        def logs = """
                #9 12.23 logs....
                #10 12.24 >> CONDA_LOCK_START
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 << CONDA_LOCK_END
                #11 12.26 logs....""".stripIndent()
        def service = new BuildLogServiceImpl()

        when:
        def result = service.extractCondaLockFile(logs)

        then:
        result == """
             # This file may be used to create an environment using:
             # \$ conda create --name <env> --file <this file>
             # platform: linux-aarch64
             @EXPLICIT
             """.stripIndent()
    }

    def 'should extract conda lockfile from s3' (){
        given:
        def s3Client = S3Client.builder()
                .endpointOverride(URI.create("http://${awsS3HostName}:${awsS3Port}"))
                .region(Region.EU_WEST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accesskey", "secretkey")))
                .forcePathStyle(true)
                .build()


        def inputStreamMapper = Mock(InputStreamMapper)

        and: "create s3 bucket"
        def storageBucket = "test-bucket"
        s3Client.createBucket { it.bucket(storageBucket) }
        and:
        def configuration = new AwsS3Configuration('build-logs')
        configuration.setBucket(storageBucket)
        and:
        AwsS3Operations awsS3Operations = new AwsS3Operations(configuration, s3Client, inputStreamMapper)
        and:
        def service = new BuildLogServiceImpl(objectStorageOperations: awsS3Operations, condaLockPrefix: "build-logs/conda-lock")
        and:
        def buildID = "123"
        def logs = """
                #9 12.23 logs....
                #10 12.24 >> CONDA_LOCK_START
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 << CONDA_LOCK_END
                #11 12.26 logs....""".stripIndent()

        when:
        service.storeCondaLock(buildID, logs)

        then:
        service.fetchCondaLockString(buildID) == """
             # This file may be used to create an environment using:
             # \$ conda create --name <env> --file <this file>
             # platform: linux-aarch64
             @EXPLICIT
             """.stripIndent()
    }

    def 'should throw no exception when there is no conda lockfile in logs' (){
        given:
        def service = new BuildLogServiceImpl()
        and:
        def logs = """
                #9 12.23 logs....
                #11 12.26 logs....""".stripIndent()

        when:
        service.extractCondaLockFile(logs)

        then:
        noExceptionThrown()
    }

}
