package io.seqera.storage.file

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.config.FileStorageConfiguration
import io.seqera.config.StorageConfiguration
import io.seqera.storage.AbstractCacheStorage
import io.seqera.storage.DigestStore
import io.seqera.storage.util.LazyDigestStore
import io.seqera.storage.util.ZippedDigestStore
import jakarta.inject.Singleton
import reactor.core.scheduler.Schedulers

@Primary
@Requires(property = "towerreg.storage.file.path")
@Singleton
@Slf4j
class FileStorage extends AbstractCacheStorage{

    boolean intermediateBlobs
    Path rootStorage

    FileStorage( StorageConfiguration storageConfiguration,
                 FileStorageConfiguration fileStorageConfiguration){
        super(storageConfiguration)
        rootStorage = Paths.get(fileStorageConfiguration.path)
        this.intermediateBlobs = fileStorageConfiguration.storeRemotes
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        File f = newFile("${path}.blob")
        if( !f.exists()){
            return Optional.empty()
        }
        DigestStore ret = f.newObjectInputStream().readObject() as DigestStore
        Optional.of(ret)
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.length}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest)
        newFile("${path}.blob").newObjectOutputStream().writeObject(result)
        result
    }

    @Override
    DigestStore saveBlob(String path, Path content, String type, String digest) {
        log.debug "Save Blob [size: ${Files.size(content)}] ==> $path"
        final result = new LazyDigestStore(content, type, digest)
        newFile("${path}.blob").newObjectOutputStream().writeObject(result)
        result
    }

    @Override
    InputStream wrapInputStream(final String path, final InputStream inputStream, final String type, final String digest) {
        if (!intermediateBlobs) {
            return inputStream
        }
        pipeInputStream(path, inputStream, type, digest)
    }

    protected InputStream pipeInputStream(final String path, final InputStream inputStream, final String type, final String digest) {
        log.debug "Save remote Blob ==> $path"
        final PipedOutputStream pipedOutputStream = new PipedOutputStream()
        final PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream)
        final String suffix = "${System.currentTimeMillis()}"
        final File dump = newFile("${path}.${suffix}")
        final FileOutputStream fileOutputStream = new FileOutputStream(dump)
        Schedulers.boundedElastic().schedule({
            try {
                byte[] bytes = new byte[1024*32]
                int data = inputStream.read(bytes)
                while(data != -1){
                    pipedOutputStream.write(bytes, 0, data)
                    fileOutputStream.write(bytes, 0, data)
                    data = inputStream.read(bytes)
                }

                String finalName = dump.absolutePath.replace(suffix,"dump")
                Files.move( Path.of(dump.absolutePath), Path.of(finalName))
                saveBlob(path, Path.of(finalName), type, digest)

                pipedOutputStream.flush()
                pipedOutputStream.close()
                log.debug "Stored remote Blob  ==> $path"
            } catch (IOException e) {
                log.debug "Error dumping remote Blob ==> $path", e
                dump.delete()
            }
        })
        pipedInputStream
    }

    private File newFile(String path){
        File ret = rootStorage.resolve("./${path}").toFile()
        ret.parentFile.mkdirs()
        ret
    }
}
