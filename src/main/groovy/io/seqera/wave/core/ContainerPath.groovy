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

package io.seqera.wave.core

/**
 * Define container name components
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerPath {

    /**
     * The image name in a container path. For example given then container:
     * {@code docker.io/library/busybox:latest} returns {@code library/busybox}
     *
     * @return The container image name
     */
    String getImage()

    /**
     * The container registry server name. For example given then container:
     * {@code docker.io/library/busybox:latest} returns {@code docker.io}
     *
     * @return The container registry server name
     */
    String getRegistry()

    /**
     * The container repository name defined as the registry name followed by the image name.
     * For example given the container {@code docker.io/library/busybox:latest} returns {@code docker.io/library/busybox}
     *
     * @return The container repository name
     */
    String getRepository()

    /**
     * The container reference name a.k.a tag. For example given the container
     * {@code docker.io/library/busybox:latest} returns {@code latest}
     *
     * @return The container reference
     */
    String getReference()

    /**
     * The fully qualified container name e.g. {@code docker.io/library/busybox:latest}
     * @return The container name
     */
    String getTargetContainer()
}
