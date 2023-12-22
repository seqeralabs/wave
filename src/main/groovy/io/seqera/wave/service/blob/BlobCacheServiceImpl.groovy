package io.seqera.wave.service.blob


import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.util.Escape
import io.seqera.wave.util.StringUtils
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
    CompletableFuture<BlobInfo> getBlobCacheURI(RoutePath route, Map<String,List<String>> headers) {
        final uri = blobDownloadUrl(route)
        final info = BlobInfo.create(uri)
        final target = route.targetPath
        if( blobStore.storeIfAbsent(target, info) ) {
            // start download and caching job
            // launch the build async
            CompletableFuture
                    .<BlobInfo>supplyAsync(() -> store(route,headers,info), executor)
        }

        return blobStore.awaitDownload(target)
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

        // the target store path
        result.add( blobStorePath(route) )

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

    protected BlobInfo store(RoutePath route, Map<String,List<String>> headers, BlobInfo info) {

        try {
            // the transfer command to be executed
            final cli = transferCommand(route, headers)
            // launch the execution
            final proc = new ProcessBuilder()
                    .command(cli)
                    .redirectErrorStream(true)
                    .start()
            // wait for the completion and save thr result
            final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
            final int status = completed ? proc.exitValue() : -1
            final logs = proc.inputStream.text
            final result = info.completed(status, logs)
            blobStore.storeBlob(route.targetPath, result)
            return result
        }
        catch (Throwable t) {
            final result = info.failed(t.message)
            blobStore.storeBlob(route.targetPath, result)
            return result
        }
    }

    protected String blobStorePath(RoutePath route) {
        StringUtils.pathConcat(blobConfig.bucket, route.path)
    }

    protected String blobDownloadUrl(RoutePath route) {
        StringUtils.pathConcat(blobConfig.baseUrl, route.path)
    }
}
