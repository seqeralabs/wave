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

package io.seqera.wave.service.blob.impl

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import jakarta.inject.Inject
/**
 * Simple {@link TransferStrategy} implementation that runs
 * s5cmd in the local computer. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SimpleTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> cli) {
        final proc = createProcess(cli).start()
        // wait for the completion and save the result
        final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
        final int status = completed ? proc.exitValue() : -1
        final logs = proc.inputStream.text
        return info.completed(status, logs)
    }

    protected ProcessBuilder createProcess(List<String> cli) {
        // builder
        final builder = new ProcessBuilder()
        builder.environment().putAll(blobConfig.getEnvironment())
        builder.command(cli)
        builder.redirectErrorStream(true)
        return builder
    }

    @Override
    Status status(BlobCacheInfo info) {
        // TODO
        return null
    }

}
