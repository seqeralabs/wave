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

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Canonical
class JobEvent {
    enum Type { Complete, Error, Timeout }

    Type type
    JobId job
    JobState state
    Throwable error

    static JobEvent complete(JobId job, JobState state) {
        new JobEvent(Type.Complete, job, state)
    }

    static JobEvent timeout(JobId job) {
        new JobEvent(Type.Timeout, job)
    }

    static JobEvent error(JobId job, Throwable error) {
        new JobEvent(Type.Error, job, null, error)
    }

}
