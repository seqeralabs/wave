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
import io.seqera.wave.service.blob.BlobCacheInfo

/**
 * Defines the contract to transfer a layer blob into a remote object storage
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface TransferStrategy {

    enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        UNKNOWN;

        boolean completed() {
            this==SUCCEEDED || this==FAILED
        }
    }

    @Canonical
    static class Transfer {
        Status status
        Integer exitCode
        String stdout

        final boolean completed() {
            return status
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
    }

    void transfer(BlobCacheInfo blob, List<String> command)

    Transfer status(BlobCacheInfo blob)

    void cleanup(BlobCacheInfo blob)

}
