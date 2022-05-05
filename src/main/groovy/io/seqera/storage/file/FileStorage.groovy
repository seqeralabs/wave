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
import io.seqera.storage.DownloadFileExecutor
import io.seqera.storage.util.LazyDigestStore
import io.seqera.storage.util.ZippedDigestStore
import io.seqera.util.TapInputStreamFilter
import jakarta.inject.Singleton

@Primary
@Requires(property = "towerreg.storage.file.enabled", value = "true")
@Singleton
@Slf4j
class FileStorage extends AbstractCacheStorage{

    private boolean intermediateBlobs
    private Path rootStorage
    private DownloadFileExecutor downloadFileExecutor

    FileStorage( StorageConfiguration storageConfiguration,
                 FileStorageConfiguration fileStorageConfiguration,
                 DownloadFileExecutor downloadFileExecutor){
        super(storageConfiguration)
        this.rootStorage = Paths.get(fileStorageConfiguration.path)
        this.intermediateBlobs = fileStorageConfiguration.storeRemotes
        this.downloadFileExecutor = downloadFileExecutor
    }

    @Override
    boolean containsBlob(String path) {
        newFile("${path}.blob").exists()
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
    InputStream saveBlob(String path, InputStream inputStream, String type, String digest, long length) {
        final File blob = newFile("${path}")
        TapInputStreamFilter tapInputStreamFilter = new TapInputStreamFilter(inputStream, blob.newOutputStream(), {
            log.debug "Save Blob ==> $path"
            final result = new LazyDigestStore( Path.of(blob.absolutePath), type, digest)
            newFile("${path}.blob").newObjectOutputStream().writeObject(result)
        })
        tapInputStreamFilter
    }

    private File newFile(String path){
        File ret = rootStorage.resolve("./${path}").toFile()
        ret.parentFile.mkdirs()
        ret
    }
}
