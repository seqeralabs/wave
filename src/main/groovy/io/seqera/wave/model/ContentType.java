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

package io.seqera.wave.model;

/**
 * Define mime types used by Docker and OCI registries
 *
 * https://www.opensourcerers.org/2020/11/16/container-images-multi-architecture-manifests-ids-digests-whats-behind/
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContentType {

    public static String DOCKER_IMAGE_INDEX_V2 = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static String DOCKER_IMAGE_TAR_GZIP = "application/vnd.docker.image.rootfs.diff.tar.gzip";
    public static String DOCKER_IMAGE_CONFIG_V1 = "application/vnd.docker.container.image.v1+json";

    /**
     * https://github.com/distribution/distribution/blob/main/docs/spec/manifest-v2-1.md
     */
    public static String DOCKER_MANIFEST_V1_TYPE = "application/vnd.docker.distribution.manifest.v1+json";
    public static String DOCKER_MANIFEST_V1_JWS_TYPE = "application/vnd.docker.distribution.manifest.v1+prettyjws";

    /**
     * https://github.com/distribution/distribution/blob/main/docs/spec/manifest-v2-2.md
     */
    public static String DOCKER_MANIFEST_V2_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    /**
     * https://github.com/opencontainers/image-spec/blob/master/manifest.md
     */
    public static String OCI_IMAGE_MANIFEST_V1 = "application/vnd.oci.image.manifest.v1+json";

    public static String OCI_IMAGE_CONFIG_V1 = "application/vnd.oci.image.config.v1+json";

    public static String OCI_IMAGE_INDEX_V1 = "application/vnd.oci.image.index.v1+json";

    public static String OCI_IMAGE_TAR_GZIP = "application/vnd.oci.image.layer.v1.tar+gzip";
}
