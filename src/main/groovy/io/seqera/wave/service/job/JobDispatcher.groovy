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

package io.seqera.wave.service.job


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Concrete implementation of {@link JobHandler} that dispatcher event invocations
 * to the target implementation based on the job {@link JobSpec#type}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JobDispatcher {

    @Inject
    private ApplicationContext context

    private Map<JobSpec.Type, JobHandler<? extends StateRecord>> dispatch = new HashMap<>()

    @PostConstruct
    void init() {
        // implementation should be added here
        add(JobSpec.Type.Build, dispatch, true)
        add(JobSpec.Type.Scan, dispatch, false)
        add(JobSpec.Type.Transfer, dispatch, false)
    }

    protected void add(JobSpec.Type type, Map<JobSpec.Type, JobHandler> map, boolean required) {
        final handler = context.findBean(JobHandler.class, Qualifiers.byName(type.toString()))
        if( handler.isPresent() ) {
            log.debug "Adding job handler for type: $type; handler=$handler"
            map.put(type, handler.get())
        }
        else if( required ) {
            throw new IllegalStateException("Unable to find Job handler for type: $type")
        }
        else {
            log.debug "Disabled job handler for type: $type"
        }
    }

    protected StateRecord loadRecord(JobHandler handler, JobSpec job) {
        final result = handler.loadRecord(job)
        if( !result ) {
            log.error "== ${job.type} entry unknown for job=${job.stateId}"
            return null
        }
        if( result.done() ) {
            log.warn "== ${job.type} entry already marked as completed for job=${job.stateId}"
            return null
        }

        return result
    }

    void notifyJobError(JobSpec job, Throwable error) {
        final handler = dispatch.get(job.type)
        final record = loadRecord(handler,job)
        if( record ) {
            handler.handleJobException(job, record, error)
        }
    }

    void notifyJobCompletion(JobSpec job, JobState state) {
        final handler = dispatch.get(job.type)
        final record = loadRecord(handler,job)
        if( record ) {
            handler.handleJobCompletion(job, record, state)
        }
    }

    void notifyJobTimeout(JobSpec job) {
        final handler = dispatch.get(job.type)
        final record = loadRecord(handler,job)
        if( record ) {
            handler.handleJobTimeout(job, record)
        }
    }

}
