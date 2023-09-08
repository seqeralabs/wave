/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.builder

import groovy.transform.Canonical
import groovy.transform.CompileStatic


/**
 * An event fired when a build has been completed
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Canonical
@CompileStatic
class BuildEvent {

    BuildRequest request
    BuildResult result

}
