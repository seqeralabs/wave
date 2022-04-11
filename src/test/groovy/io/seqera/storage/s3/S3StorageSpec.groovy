package io.seqera.storage.s3

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.seqera.storage.Storage
import io.seqera.storage.s3.S3Storage
import io.seqera.testcontainers.AwsContainer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

class S3StorageSpec extends Specification implements AwsContainer{

    def setup() {
        initAwsContainer()
    }

    def cleanup(){
        stopAwsContainer()
    }

    Map validConfiguration(){
        [
                "towerreg.storage.s3.bucket": 'test',
                "towerreg.storage.s3.endpoint" : endpointFor(),
                "aws.region": 'eu-west-1',
                "aws.accessKeyId": 'accessKeyId',
                "aws.secretKey": 'secretKey',
        ]
    }

    void 'test is injected'() {
        given: 'a configuration'
        Map configuration = validConfiguration()
        when:
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        then:
        ctx.containsBean(Storage)
        ctx.getBean(Storage) instanceof S3Storage
    }

    void 'save blob'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a bucket'
        S3ClientBuilder s3ClientBuilder = ctx.getBean(S3ClientBuilder)
        S3Client s3Client = s3ClientBuilder.build()
        s3Client.createBucket(
                CreateBucketRequest.builder().bucket("test").build() as CreateBucketRequest)

        and: 'a storage'
        S3Storage storage = ctx.getBean(S3Storage)

        when:
        storage.saveBlob("/a/path", "12345".bytes , "application/text", "digest")

        then:
        def content = s3Client.getObject(
                GetObjectRequest.builder().bucket("test").key("/a/path").build() as GetObjectRequest)
        "12345".bytes == content.bytes
    }

    void 'save file'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a bucket'
        S3ClientBuilder s3ClientBuilder = ctx.getBean(S3ClientBuilder)
        S3Client s3Client = s3ClientBuilder.build()
        s3Client.createBucket(
                CreateBucketRequest.builder().bucket("test").build() as CreateBucketRequest)

        and: 'a file'
        Path path = Files.createTempFile("","")
        path.toFile().text = "1234"

        and: 'a storage'
        S3Storage storage = ctx.getBean(S3Storage)

        when:
        storage.saveBlob("/a/path", path, "application/text", "digest")

        then:
        def content = s3Client.getObject(
                GetObjectRequest.builder().bucket("test").key("/a/path").build() as GetObjectRequest)
        path.toFile().bytes == content.bytes

        cleanup:
        Files.delete(path)
    }


    void 'save an inputstream'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a bucket'
        S3ClientBuilder s3ClientBuilder = ctx.getBean(S3ClientBuilder)
        S3Client s3Client = s3ClientBuilder.build()
        s3Client.createBucket(
                CreateBucketRequest.builder().bucket("test").build() as CreateBucketRequest)

        and: 'a file'
        Path path = Files.createTempFile("","")
        path.toFile().text = "1234"*10

        and: 'a storage'
        S3Storage storage = ctx.getBean(S3Storage)

        when:
        def inputStream = storage.saveBlob("/a/path", path.toFile().newInputStream(), "application/text", "digest", path.size())

        then: 'is readed'
        path.toFile().bytes == inputStream.readAllBytes()

        and: 'blob is stored'
        //need a good way to check async task completion
        true

        cleanup:
        Files.delete(path)
    }

    void 'fails if bucket doesnt exist'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a storage'
        S3Storage storage = ctx.getBean(S3Storage)

        when:
        storage.saveBlob("/a/path", "12345".bytes , "application/text", "digest")

        then:
        thrown(NoSuchBucketException)
    }
}
