/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.aws

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.util.BucketTokenizer
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
/**
 * Factory implementation for ObjectStorageOperations
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Factory
@CompileStatic
@Slf4j
class ObjectStorageOperationsFactory {

    public static final String BUILD_LOGS = "build-logs"

    public static final String BUILD_LOCKS = "build-locks"

    public static final String SCAN_REPORTS = "scan-reports"

    @Inject
    private ApplicationContext context

    @Inject
    @Nullable
    private BuildConfig buildConfig

    @Inject
    @Nullable
    private ScanConfig scanConfig

    ObjectStorageOperationsFactory() {}

    @Singleton
    @Named(BUILD_LOGS)
    ObjectStorageOperations<?, ?, ?> createLogsStorageOps() {
        if( !buildConfig )
            throw new IllegalStateException("Build configuration is not defined")
        return create0(BUILD_LOGS, buildConfig.logsPath,  "wave.build.logs.path")
    }

    @Singleton
    @Named(BUILD_LOCKS)
    ObjectStorageOperations<?, ?, ?> createLocksStorageOpts() {
        if( !buildConfig )
            throw new IllegalStateException("Build configuration is not defined")
        return create0(BUILD_LOCKS, buildConfig.locksPath, "wave.build.locks.path")
    }

    @Singleton
    @Named(SCAN_REPORTS)
    ObjectStorageOperations<?, ?, ?> createScanStorageOpts() {
        if( !scanConfig )
            throw new IllegalStateException("Scan configuration is not defined")
        return create0(SCAN_REPORTS, scanConfig.reportsPath, "wave.scan.reports.path")
    }

    protected ObjectStorageOperations<?, ?, ?> create0(String scope, String path, String setting) {
        if( !path )
            throw new IllegalStateException("Missing config setting '${setting}' in the wave config")
        final store = BucketTokenizer.from(path)
        if( !store.scheme ) {
            return localFactory(scope, path)
        }
        if( store.scheme=='s3' ) {
            return awsFactory(scope, store.bucket)
        }
        throw new IllegalArgumentException("Unsupported storage scheme: '${store.scheme}' - offending setting '${setting}': ${path}" )
    }

    protected ObjectStorageOperations<?, ?, ?> localFactory(String scope, String storageBucket) {
        log.debug "Using local ObjectStorageOperations scope='${scope}'; storageBucket='${storageBucket}'"
        final localPath = Path.of(storageBucket)
        LocalStorageConfiguration configuration = new LocalStorageConfiguration(scope)
        configuration.setPath(localPath)
        return new LocalStorageOperations(configuration)
    }

    protected ObjectStorageOperations<?, ?, ?> awsFactory(String scope, String storageBucket) {
        log.debug "Using AWS S3 ObjectStorageOperations scope='${scope}'; storageBucket='${storageBucket}'"
        final s3Client = context.getBean(S3Client, Qualifiers.byName("DefaultS3Client"))
        final inputStreamMapper = context.getBean(InputStreamMapper)
        AwsS3Configuration configuration = new AwsS3Configuration(scope)
        configuration.setBucket(storageBucket)
        return new AwsS3Operations(configuration, s3Client, inputStreamMapper)
    }
}
