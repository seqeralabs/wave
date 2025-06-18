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

package io.seqera.wave.service.scan

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.util.FusionHelper
import io.seqera.wave.configuration.ScanEnabled
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_WORKSPACE
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(bean = ScanEnabled)
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerScanStrategy extends ScanStrategy {

    @Inject
    private ScanConfig scanConfig

    @Inject
    @Named(BUILD_WORKSPACE)
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    DockerScanStrategy(ScanConfig scanConfig) {
        this.scanConfig = scanConfig
    }

    @Override
    void scanContainer(String jobName, ScanEntry entry) {
        log.info("Launching container scan job: $jobName for entry: $entry}")
        // config (docker auth) file name
        if( entry.configJson ){
            objectStorageOperations.upload(UploadRequest.fromBytes(entry.configJson.bytes, "$entry.workDir/config.json".toString()))
        }
        // outfile file name
        final reportFile = "$entry.workDir/$Trivy.OUTPUT_FILE_NAME".toString()
        // create the launch command
        final dockerCommand = dockerWrapper(jobName, entry.workDir, entry.configJson, scanConfig.environment)
        final trivyCommand = List.of(scanConfig.scanImage) + scanCommand(entry.containerImage, reportFile, entry.platform, scanConfig)
        final command = dockerCommand + trivyCommand

        //launch scanning
        log.debug("Container scan command: ${command.join(' ')}")
        final process = new ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()

        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch scan container - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> dockerWrapper(String jobName, String workDir, String credsFile, List<String> env) {

        final wrapper = ['docker',
                         'run',
                         '--detach',
                         '--name',
                         jobName,
                         '-e',
                         "AWS_ACCESS_KEY_ID=${System.getenv('AWS_ACCESS_KEY_ID')}".toString(),
                         '-e',
                         "AWS_SECRET_ACCESS_KEY=${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString()]
        // scan work dir
        wrapper.add('-e')
        wrapper.add("TRIVY_WORKSPACE_DIR=${FusionHelper.getFusionPath(scanConfig.workspaceBucketName, workDir)}".toString())

        // cache directory
        wrapper.add('-e')
        wrapper.add("TRIVY_CACHE_DIR=${FusionHelper.getFusionPath(scanConfig.workspaceBucketName, scanConfig.cacheDirectory)}".toString())

        if(credsFile) {
            wrapper.add('-e')
            wrapper.add("DOCKER_CONFIG=${FusionHelper.getFusionPath(scanConfig.workspaceBucketName, workDir)}".toString())
        }

        if( env ) {
            for( String it : env ) {
                wrapper.add('-e')
                wrapper.add(it)
            }
        }

        return wrapper
    }
}
