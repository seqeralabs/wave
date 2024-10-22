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

package io.seqera.wave.service.scan

import io.seqera.wave.api.ScanMode
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequest

/**
 * Declare operations to scan containers
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerScanService {

    String getScanId(String targetImage, String digest, ScanMode mode, String format)

    void scan(ScanRequest request)

    void scanOnBuild(BuildEvent build)

    void scanOnMirror(MirrorEntry entry)

    void scanOnRequest(ContainerRequest request)

    WaveScanRecord getScanRecord(String scanId)

    ScanEntry getScanState(String scanId)

    List<WaveScanRecord> getAllScans(String scanId)

}
