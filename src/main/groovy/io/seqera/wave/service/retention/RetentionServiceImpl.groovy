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

package io.seqera.wave.service.retention

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.function.Consumer
import java.util.function.Predicate

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.RetentionConfig
import io.seqera.wave.configuration.RetentionEnabled
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.RecordType
import io.seqera.wave.service.scan.ContainerScanService
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements the retention of the Wave records and of the objects they reference.
 *
 * Artifacts are removed before the row that points at them, so an interrupted pass leaves an
 * orphaned row that the next pass collects, rather than an object nothing refers to any more.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Requires(bean = RetentionEnabled)
@Singleton
@CompileStatic
class RetentionServiceImpl implements RetentionService {

    @Inject
    private RetentionConfig config

    @Inject
    private PersistenceService persistenceService

    @Nullable
    @Inject
    private BuildLogService buildLogService

    @Nullable
    @Inject
    private ContainerScanService scanService

    // replaced by the tests so the pass budget does not depend on wall time
    private Clock clock = Clock.systemUTC()

    @PostConstruct
    private void init() {
        if( !buildLogService )
            log.warn "Data retention is not removing BUILD records - the build service is disabled, so their logs and Conda locks cannot be deleted"
        if( !scanService )
            log.warn "Data retention is not removing SCAN records - the scan service is disabled, so their reports cannot be deleted"
    }

    @Override
    long purge() {
        // bound the pass well inside the lease held by the cron, so it cannot still be running
        // when the lease expires and a second replica starts its own pass
        final deadline = Instant.now(clock).plus(config.interval.dividedBy(2))
        long count = 0
        // a type whose artifact service is absent is left alone: dropping the row would strand its
        // objects in the bucket with nothing left to name them
        if( buildLogService )
            count += purge0(RecordType.BUILD, config.build, slice(deadline, 4), id -> buildLogService.deleteArtifacts(id))
        count += purge0(RecordType.REQUEST, config.request, slice(deadline, 3), null)
        count += purge0(RecordType.MIRROR, config.mirror, slice(deadline, 2), null)
        if( scanService )
            count += purge0(RecordType.SCAN, config.scan, slice(deadline, 1), id -> scanService.deleteReports(id), id -> scanService.getScanState(id) != null)
        return count
    }

    /**
     * Give each type its own share of what is left of the pass, so a type that cannot drain within
     * the budget does not starve the ones behind it. Time a type leaves unused rolls over.
     */
    protected Instant slice(Instant deadline, int remaining) {
        final now = Instant.now(clock)
        final left = Duration.between(now, deadline)
        return left.isNegative() ? now : now.plus(left.dividedBy(remaining))
    }

    /**
     * Remove every record of one type past its period, a batch at a time until the type is drained
     * or the deadline is reached. Draining within the pass is what keeps retention converging: one
     * batch per run caps removals at a rate a busy type can outrun.
     */
    protected long purge0(RecordType type, Duration period, Instant deadline, @Nullable Consumer<String> artifacts, @Nullable Predicate<String> inUse=null) {
        if( !Instant.now(clock).isBefore(deadline) ) {
            log.warn "Retention skipped ${type} records - the pass ran out of time before reaching them"
            return 0
        }
        final before = Instant.now(clock).minus(period)
        long total = 0
        long orphaned = 0
        int kept = 0
        boolean complete = false
        while( Instant.now(clock) < deadline ) {
            // the query is oldest first from offset zero, so a record left in place comes back at
            // the head of the next batch: widen the batch by what was kept, or it blocks the queue
            final int limit = config.batchSize + kept
            final ids = persistenceService.findIdsOlderThan(type, before, limit)
            if( !ids ) {
                complete = true
                break
            }
            final List<String> due = inUse ? ids.findAll { !inUse.test(it) } : ids
            kept = ids.size() - due.size()
            if( due ) {
                final failed = deleteArtifacts0(type, artifacts, due)
                if( failed && failed==due.size() ) {
                    // not one bad record but a broken dependency: retrying the rest of the pass would
                    // only orphan every object it touches, so leave the rows for the next run
                    log.error "Retention could not delete the artifacts of any ${type} record - abandoning this pass, ${total} record(s) removed so far"
                    return total
                }
                orphaned += failed
                total += persistenceService.deleteByIds(type, due)
            }
            if( ids.size() < limit ) {
                complete = true
                break
            }
        }
        if( kept )
            log.debug "Retention kept ${kept} ${type} record(s) still in use - they go on a later pass"
        if( total )
            log.info "Retention removed ${total} ${type} record(s) older than ${period}"
        if( !complete )
            log.warn "Retention stopped early on ${type} records - more remain past ${period}, continuing on the next run"
        if( orphaned )
            log.warn "Retention removed ${orphaned} ${type} record(s) whose artifacts could not be deleted - those objects are now orphaned in the object store"
        return total
    }

    /**
     * @return how many of the given records lost their row but kept their artifacts. The row goes
     * anyway: holding it back would park the failure at the head of the queue and stall every
     * younger record behind it.
     */
    private long deleteArtifacts0(RecordType type, @Nullable Consumer<String> artifacts, List<String> ids) {
        if( !artifacts )
            return 0
        long failed = 0
        for( String id : ids ) {
            try {
                artifacts.accept(id)
            }
            catch (Exception e) {
                failed++
                log.error "Unable to delete the artifacts of ${type} record '${id}' - cause: ${e.message}", e
            }
        }
        return failed
    }

}
