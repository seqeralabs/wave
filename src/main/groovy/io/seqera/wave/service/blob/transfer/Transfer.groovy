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

package io.seqera.wave.service.blob.transfer

import groovy.transform.Canonical
import groovy.transform.ToString

/**
 * Model a transfer operation state
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@ToString(includePackage = false, includeNames = true)
@Canonical
class Transfer {

    enum Status { PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN }

    final Status status
    final Integer exitCode
    final String stdout

    final boolean completed() {
        return status==Status.SUCCEEDED || status==Status.FAILED
    }

    final boolean succeeded() {
        status==Status.SUCCEEDED && exitCode==0
    }

    static Transfer pending() {
        return new Transfer(Status.PENDING)
    }

    static Transfer running() {
        return new Transfer(Status.RUNNING)
    }

    static Transfer failed(Integer exit, String logs) {
        return new Transfer(Status.FAILED, exit, logs)
    }

    static Transfer succeeded(String logs) {
        return new Transfer(Status.SUCCEEDED, 0, logs)
    }

    static Transfer completed(Integer exit, String logs) {
        final st = exit==0 ? Status.SUCCEEDED : Status.FAILED
        return new Transfer(st, exit, logs)
    }

    static Transfer unknown(String logs) {
        return new Transfer(Status.UNKNOWN,null,logs)
    }
}
