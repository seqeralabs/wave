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

package io.seqera.wave.api;

/**
 * Model a container build context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class BuildContext extends ContainerLayer {

    /* required by jackson serialization - do not remove */
    BuildContext() { }

    BuildContext(String location, String gzipDigest, Integer gzipSize, String tarDigest) {
        super(location, gzipDigest, gzipSize, tarDigest);
    }

    static public BuildContext of(ContainerLayer layer) {
        return new BuildContext(
                layer.location,
                layer.gzipDigest,
                layer.gzipSize,
                layer.tarDigest );
    }

}
