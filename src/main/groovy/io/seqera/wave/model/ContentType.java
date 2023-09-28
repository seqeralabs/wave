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
