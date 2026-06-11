/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1.mapper

import groovy.transform.CompileStatic
import io.seqera.wave.api.v1.model.ScanSubmitResponse
import io.seqera.wave.api.v1.model.Vulnerability
import io.seqera.wave.api.v1.model.WaveScanRecord as V1WaveScanRecord
import io.seqera.wave.service.persistence.WaveScanRecord as InternalWaveScanRecord
import io.seqera.wave.service.scan.ScanVulnerability

/**
 * Maps internal {@link InternalWaveScanRecord} model objects to their v1 API counterparts
 * for the scans API.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ScansV1Mapper {

    /**
     * Map an internal {@link InternalWaveScanRecord} to a v1 {@link V1WaveScanRecord}.
     * <p>
     * Vulnerabilities are grouped by severity and returned as a count-per-severity
     * summary (matching the v1 {@link Vulnerability} model).
     * Logs and workDir are intentionally excluded from the record response.
     *
     * @param internal  the internal scan record (must not be null)
     * @return a v1 model record with all available fields populated
     */
    static V1WaveScanRecord toV1Record(InternalWaveScanRecord internal) {
        return new V1WaveScanRecord()
                .id(internal.id)
                .buildId(internal.buildId ?: '')
                .containerImage(internal.containerImage ?: '')
                .startTime(internal.startTime?.toString() ?: '')
                .duration(internal.duration?.toMillis() ?: 0L)
                .status(internal.status ?: '')
                .vulnerabilities(toV1Vulnerabilities(internal.vulnerabilities))
    }

    /**
     * Map an internal {@link InternalWaveScanRecord} to a v1 {@link ScanSubmitResponse}.
     *
     * @param internal  the internal scan record (must not be null)
     * @return a v1 submit response with scanId and targetImage
     */
    static ScanSubmitResponse toV1Submit(InternalWaveScanRecord internal) {
        return new ScanSubmitResponse()
                .scanId(internal.id)
                .targetImage(internal.containerImage)
    }

    private static List<Vulnerability> toV1Vulnerabilities(List<ScanVulnerability> vulnerabilities) {
        if( !vulnerabilities )
            return List.of()
        // group by severity, count occurrences, and convert to v1 Vulnerability objects
        final summary = new LinkedHashMap<String, Integer>()
        for( ScanVulnerability v : vulnerabilities ) {
            summary.merge(v.severity ?: 'UNKNOWN', 1, Integer::sum)
        }
        return summary.collect { String severity, Integer count ->
            new Vulnerability().severity(severity).count(count)
        }
    }
}
