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
import groovy.transform.ToString

/**
 * Model a transfer operation state
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@ToString(includePackage = false, includeNames = true)
@Canonical
class JobState {

    enum Status { PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN }

    final Status status
    final Integer exitCode
    final String stdout

    boolean completed() {
        return status==Status.SUCCEEDED || status==Status.FAILED
    }

    boolean succeeded() {
        status==Status.SUCCEEDED && exitCode==0
    }

    static JobState pending() {
        return new JobState(Status.PENDING)
    }

    static JobState running() {
        return new JobState(Status.RUNNING)
    }

    static JobState failed(Integer exit, String logs) {
        return new JobState(Status.FAILED, exit, logs)
    }

    static JobState succeeded(String logs) {
        return new JobState(Status.SUCCEEDED, 0, logs)
    }

    static JobState completed(Integer exit, String logs) {
        final st = exit==0 ? Status.SUCCEEDED : Status.FAILED
        return new JobState(st, exit, logs)
    }

    static JobState unknown(String logs) {
        return new JobState(Status.UNKNOWN,null,logs)
    }
}
