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

/**
 * Define the scan format that should be used
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
enum ScanType {

    Default("json", "report.json"),
    Spdx("spdx-json", "spdx.json"),
    CycloneDx("cyclonedx", "cyclonedx.json"),

    final private String format;
    final private String output;

    ScanType(String format, String output) {
        this.format = format
        this.output = output
    }

    String getFormat() { format }

    String getOutput() { output }
}
