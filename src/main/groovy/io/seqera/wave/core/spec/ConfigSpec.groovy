/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.core.spec

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Model a container config specification
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ConfigSpec {
    String hostName
    String domainName
    String user
    Boolean attachStdin
    Boolean attachStdout
    Boolean attachStderr
    Boolean tty
    List<String> env
    List<String> cmd
    String image
    String workingDir
    List<String> entrypoint

    ConfigSpec() {
        this(Map.of())
    }

    ConfigSpec(Map opts) {
        this.hostName = opts.Hostname
        this.domainName = opts.Domainname
        this.user = opts.User
        this.attachStdin = opts.AttachStdin as Boolean
        this.attachStdout = opts.AttachStdout as Boolean
        this.attachStderr = opts.AttachStderr as Boolean
        this.tty = opts.Tty as Boolean
        this.env = opts.Env as List<String> ?: List.<String>of()
        this.cmd = opts.Cmd as List<String> ?: List.<String>of()
        this.image = opts.Image
        this.workingDir = opts.WorkingDir
        this.entrypoint = opts.Entrypoint as List<String> ?: List.<String>of()
    }

}
