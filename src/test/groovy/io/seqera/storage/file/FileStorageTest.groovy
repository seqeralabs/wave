package io.seqera.storage.file

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import io.micronaut.context.ApplicationContext
import io.seqera.storage.DigestStore
import io.seqera.storage.Storage

class FileStorageTest extends Specification{


    Path path

    void setup(){
        path = Files.createTempDirectory('test')
    }

    def cleanup(){
        path.toFile().deleteDir()
    }

    Map validConfiguration(){
        [
                "towerreg.storage.file.path": path.toAbsolutePath().toString()
        ]
    }

    Map pipedValidConfiguration(){
        [
                "towerreg.storage.file.path": path.toAbsolutePath().toString(),
                "towerreg.storage.file.intermediate": true
        ]
    }

    void 'test is injected'() {
        given: 'a configuration'
        Map configuration = validConfiguration()
        when:
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        then:
        ctx.containsBean(Storage)
        ctx.getBean(Storage) instanceof FileStorage
    }

    void 'save blob'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a storage'
        FileStorage storage = ctx.getBean(FileStorage)

        when:
        storage.saveBlob("/a/path", "12345".bytes , "application/text", "digest")

        then:
        def content = path.resolve("a/path.blob").toFile()
        content.exists()
        (content.newObjectInputStream().readObject() as DigestStore).inputStream.readAllBytes() == "12345".bytes
        (content.newObjectInputStream().readObject() as DigestStore).mediaType == 'application/text'
    }

    void 'save lazy blob'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a storage'
        FileStorage storage = ctx.getBean(FileStorage)
        File blob
        blob = Files.createTempFile(path, "", ".dump").toFile()
        blob.text = "Hi!"

        when:
        storage.saveBlob("/a/path", Path.of(blob.absolutePath) , "application/text", "digest")

        then:
        def content = path.resolve("a/path.blob").toFile()
        content.exists()
        (content.newObjectInputStream().readObject() as DigestStore).inputStream.readAllBytes() == "Hi!".bytes
        (content.newObjectInputStream().readObject() as DigestStore).mediaType == 'application/text'
    }

    void 'save manifest'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a storage'
        FileStorage storage = ctx.getBean(FileStorage)

        when:
        storage.saveManifest("/a/path", "12345" , "application/text", "digest")

        then:
        def content = path.resolve("a/path.manifest").toFile()
        content.exists()
        (content.newObjectInputStream().readObject() as DigestStore).bytes
        (content.newObjectInputStream().readObject() as DigestStore).mediaType == 'application/text'
    }

    void 'create the storage if directory doesnt exist'() {
        given: 'a configuration'
        Map configuration = validConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        path.toFile().deleteDir()

        and: 'a storage'
        FileStorage storage = ctx.getBean(FileStorage)

        when:
        storage.saveBlob("/a/path", "12345".bytes , "application/text", "digest")

        then:
        path.toFile().exists()
        def content = path.resolve("a/path.blob").toFile()
        content.exists()
    }

    void 'save remote inputstream create a lazy blob'() {
        given: 'a configuration'
        Map configuration = pipedValidConfiguration()

        and: 'an application'
        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, configuration)

        and: 'a storage'
        FileStorage storage = ctx.getBean(FileStorage)
        File blob
        blob = Files.createTempFile(path, "", ".dump").toFile()
        blob.text = "Hi!"*1000

        when:
        def wrapped = storage.wrapInputStream("/a/path", blob.newInputStream() , "application/text", "digest")

        then:
        wrapped.readAllBytes() == blob.bytes
        def content = path.resolve("a/path.blob").toFile()
        content.exists()
        (content.newObjectInputStream().readObject() as DigestStore).inputStream.readAllBytes() == blob.bytes
        (content.newObjectInputStream().readObject() as DigestStore).mediaType == 'application/text'
    }
}
