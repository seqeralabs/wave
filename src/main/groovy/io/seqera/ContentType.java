package io.seqera;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContentType {

    public static String DOCKER_MANIFEST_LIST_V2 = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static String BLOB_MIME = "application/vnd.docker.image.rootfs.diff.tar.gzip";
    public static String DOCKER_IMAGE_V1 = "application/vnd.docker.container.image.v1+json";

    @Deprecated public static String DOCKER_MANIFEST_V1_TYPE = "application/vnd.docker.distribution.manifest.v1+json";
    public static String DOCKER_MANIFEST_V1_JWS_TYPE = "application/vnd.docker.distribution.manifest.v1+prettyjws";

    public static String DOCKER_MANIFEST_V2_TYPE = "application/vnd.docker.distribution.manifest.v2+json";


}
