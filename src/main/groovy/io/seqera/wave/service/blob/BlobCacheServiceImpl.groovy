package io.seqera.wave.service.blob

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.util.Escape
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Implements cache for container image layer blobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class BlobCacheServiceImpl implements BlobCacheService {

    @Inject
    private BlobConfig blobConfig

    @Inject
    private BlobStore blobStore

    @Inject
    private RegistryProxyService proxyService

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Override
    CompletableFuture<URI> getBlobCacheURI(RoutePath route, Map<String,List<String>> headers) {
        final info = new BlobInfo(Instant.now())
        final target = route.targetPath
        if( blobStore.storeIfAbsent(target, info) ) {
            // start download and caching job
            // launch the build async
            CompletableFuture
                    .<BuildResult>supplyAsync(() -> store(route,headers), executor)
        }

        return blobInfo(target)
                .thenApply(it-> new URI(it.locationUri))
    }

    @Override
    CompletableFuture<BlobInfo> blobInfo(String key) {
        return blobStore
                .awaitDownload(key)
    }

    protected List<String> s5cmd(RoutePath route, Map<String,List<String>> headers) {
        final String cacheControl = headers.find(it-> it.key.toLowerCase()=='cache-control')?.value?.first()
        final String contentType = headers.find(it-> it.key.toLowerCase()=='content-type')?.value?.first()

        final result = ['s5cmd',
         'pipe',
         '--acl',
         'public-read']
        if( contentType ) {
            result.add('--content-type')
            result.add(contentType)
        }

        if( cacheControl ) {
            result.add('--cache-control')
            result.add(cacheControl)
        }

        def bucket0 = blobConfig.bucket
        while( bucket0.endsWith('/') )
            bucket0 = bucket0.substring(0,bucket0.length()-1)
        result.add( bucket0 + route.path )

        return result
    }

    protected List<String> transferCommand(RoutePath route, Map<String,List<String>> headers) {
        final curl = proxyService.curl(route, headers)
        final s5cmd = s5cmd(route, headers)

        return List.of(
                'sh',
                '-c',
                Escape.cli(curl) + ' | ' + Escape.cli(s5cmd) )
    }

    protected void store(RoutePath route, Map<String,List<String>> headers) {

        try {
            final cli = transferCommand(route, headers)

            final proc = new ProcessBuilder()
                    .command(cli)
                    .redirectErrorStream(true)
                    .start()

            final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
            final stdout = proc.inputStream.text
            return BuildResult.completed(req.id, completed ? proc.exitValue() : -1, stdout, req.startTime)

            blobStore.storeBlob()

        }
        catch (Throwable t) {

        }
    }
}
