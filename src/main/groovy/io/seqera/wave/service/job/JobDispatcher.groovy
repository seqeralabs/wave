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

import java.util.function.BiConsumer

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

    private Map<JobSpec.Type, JobHandler<? extends JobRecord>> dispatch = new HashMap<>()

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

    protected void apply(JobSpec job, BiConsumer<JobHandler, JobRecord> consumer) {
        final handler = dispatch.get(job.type)
        final record = handler.getJobRecord(job)
        if( !record ) {
            log.error "== ${job.type} record unknown for job=${job.recordId}"
        }
        else if( record.done() ) {
            log.warn "== ${job.type} record already marked as completed for job=${job.recordId}"
        }
        else {
            consumer.accept(handler, record)
        }
    }

    void notifyJobCompletion(JobSpec job, JobState state) {
        apply(job, (handler, record)-> handler.onJobCompletion(job, record, state))
    }

    void notifyJobError(JobSpec job, Throwable error) {
        apply(job, (handler, record)-> handler.onJobException(job, record, error))
    }

    void notifyJobTimeout(JobSpec job) {
        apply(job, (handler, record)-> handler.onJobTimeout(job, record))
    }

}
