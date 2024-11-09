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

package io.seqera.wave.configuration

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
/**
 * Model build logs configuration settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'wave.build.logs')
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class LogsConfig {

    @Nullable
    @Value('${wave.build.logs.prefix}')
    private String prefix

    @Nullable
    @Value('${wave.build.logs.bucket}')
    private String bucket

    @Value('${wave.build.logs.maxLength:100000}')
    private long maxLength

    @Nullable
    @Value('${wave.build.logs.conda-lock-prefix}')
    private String condaLockPrefix

    @Nullable
    @Value('${wave.build.logs.local.path}')
    private String localPath

    String getPrefix() {
        return prefix
    }

    String getBucket() {
        return bucket
    }

    long getMaxLength() {
        return maxLength
    }

    String getCondaLockPrefix() {
        return condaLockPrefix
    }

    @Memoized
    Path getLocalPath() {
        if( !localPath )
            return null
        final result = Path.of(localPath)
        try {
            Files.createDirectories(result)
        } catch (IOException e) {
            log.warn ("Unable to create logs local storage path: $localPath - cause: ${e.message}")
        }
        return result
    }
}
