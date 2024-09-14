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
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
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

    /**
     * Load all available implementations of {@link JobHandler}. Job handler should be:
     * 1. declared as a @Singleton
     * 2. annotated with the {@link Named} annotation
     * 3. the Named value should be a literal matching the corresponding {@JobSpec.Type}
     */
    @PostConstruct
    protected init() {
        final handlers = context.getBeansOfType(JobHandler)
        for( JobHandler it : handlers ) {
            final qualifier = it.getClass().getAnnotation(Named)?.value()
            if( !qualifier )
                throw new IllegalStateException("Missing 'Named' annotation for handler ${it.class.name}")
            final type = JobSpec.Type.valueOf(qualifier)
            log.info "Adding job handler for type: $type; handler=${it.class.simpleName}"
            dispatch.put(type, it)
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
