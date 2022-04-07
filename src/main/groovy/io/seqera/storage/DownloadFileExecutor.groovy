package io.seqera.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

import groovy.util.logging.Slf4j
import jakarta.inject.Singleton
import reactor.core.scheduler.Schedulers


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
class DownloadFileExecutor {

    private int maxConcurrentDownloads = 50

    Map<String, Function<Path, Void>> stagingDownloads = Collections.synchronizedMap(
            new HashMap<String, Function<Path, Void>>())

    synchronized void scheduleDownload(final String key, final InputStream inputStream, final Function<Path, Void>onComplete){

        if( stagingDownloads.containsKey(key)){
            return
        }

        if( stagingDownloads.size() >= maxConcurrentDownloads ){
            return
        }

        stagingDownloads.put(key, onComplete)

        Schedulers.boundedElastic().schedule{
            final Path dump = Files.createTempFile("download-","dump")
            try {
                final FileOutputStream fileOutputStream = new FileOutputStream(dump.toFile())
                inputStream.transferTo(fileOutputStream)
                log.debug "Path $key downloaded"
                stagingDownloads.get(key).apply( dump )
            } catch (IOException e) {
                log.debug "Error dumping remote path ==> $key", e
                dump.toFile().delete()
            }
            stagingDownloads.remove(key)
        }
    }

}
