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

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Concrete implementation of {@link JobHandler} that dispatcher event invocations
 * to the target implementation based on the job {@link JobId#type}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JobDispatcher implements JobHandler {

    @Inject
    private ApplicationContext context

    private Map<JobId.Type, JobHandler> dispatch = new HashMap<>()

    @PostConstruct
    void init() {
        // implementation should be added here
        add(JobId.Type.Transfer, dispatch, false)
    }

    protected void add(JobId.Type type, Map<JobId.Type, JobHandler> map, boolean required) {
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

    @Override
    final Duration jobMaxDuration(JobId job) {
        return dispatch.get(job.type).jobMaxDuration(job)
    }

    @Override
    final void onJobCompletion(JobId job, JobState state) {
        dispatch.get(job.type).onJobCompletion(job, state)
    }

    @Override
    final void onJobException(JobId job, Throwable error) {
        dispatch.get(job.type).onJobException(job, error)
    }

    @Override
    final void onJobTimeout(JobId job) {
        dispatch.get(job.type).onJobTimeout(job)
    }
}
