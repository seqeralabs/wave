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

package io.seqera.wave.service.cleanup

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskScheduler
import io.seqera.wave.service.job.JobOperation
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanIdStore
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
 * Implement a service for resources cleanup
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class CleanupServiceImpl implements Runnable, CleanupService {

    static final private String DIR_PREFIX = 'dir:'

    static final private String JOB_PREFIX = 'job:'

    static final private String SCANID_PREFIX = 'scanid:'

    @Inject
    private TaskScheduler scheduler

    @Inject
    private CleanupStore store

    @Inject
    private CleanupConfig config

    @Inject
    private CleanupStrategy cleanupStrategy

    @Inject
    private JobOperation operation

    @Inject
    private ScanIdStore scanIdStore

    @PostConstruct
    private init() {
        log.info "Creating cleanup service - config=$config"
        // use randomize initial delay to prevent multiple replicas running at the same time
        scheduler.scheduleWithFixedDelay(
                config.cleanupStartupDelayRandomized,
                config.cleanupRunInterval,
                this )
    }

    @Override
    void run() {
        final now = Instant.now()
        final keys = store.getRange(0, now.epochSecond, config.cleanupRange)

        for( String it : keys ) {
            try {
                cleanupEntry(it)
            }
            catch (InterruptedException e) {
                Thread.interrupted()
            }
            catch (Throwable t) {
                log.error("Unexpected error in JWT heartbeat while processing key: $it", t)
            }
        }
    }

    protected void cleanupEntry(String entry) {
        log.debug "Cleaning up entry $entry"
        if( entry.startsWith(JOB_PREFIX) ) {
            cleanupJob0(entry.substring(4))
        }
        else if( entry.startsWith(DIR_PREFIX) ) {
            cleanupDir0(entry.substring(4))
        }
        else if( entry.startsWith(SCANID_PREFIX) ) {
            cleanupScanId0(entry.substring(SCANID_PREFIX.length()))
        }
        else {
            log.error "Unknown cleanup entry - offending value: $entry"
        }
    }

    protected void cleanupJob0(String jobName) {
        try {
            operation.cleanup(jobName)
        }
        catch (Throwable t) {
            log.error("Unexpected error deleting job=$jobName - cause: ${t.message}", t)
        }
    }

    protected void cleanupDir0(String path) {
        try {
            Path.of(path).deleteDir()
        }
        catch (Throwable t) {
            log.error("Unexpected error deleting path=$path - cause: ${t.message}", t)
        }
    }

    protected void cleanupScanId0(String scanId) {
        scanIdStore.remove(scanId)
    }

    @Override
    void cleanupJob(JobSpec job, Integer exitStatus) {
        if( !cleanupStrategy.shouldCleanup(exitStatus) ) {
            return
        }

        final ttl = exitStatus==0
                ? config.succeededDuration
                : config.failedDuration
        final expirationSecs = Instant.now().plus(ttl).epochSecond
        // schedule the job deletion
        store.add(JOB_PREFIX + job.operationName, expirationSecs)
        // schedule work dir path deletion
        if( job.workDir ) {
            store.add(DIR_PREFIX + job.workDir, expirationSecs)
        }
    }

    @Override
    void cleanupScan(ScanEntry entry, Duration ttl) {
        store.add(SCANID_PREFIX + entry.scanId, ttl.toSeconds())
    }
}
