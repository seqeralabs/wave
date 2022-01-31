package io.seqera

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class Mock {
    public static String MANIFEST_LIST_MIME = 'application/vnd.docker.distribution.manifest.list.v2+json'
    public static String MANIFEST_LIST_CONTENT = "{\"manifests\":[{\"digest\":\"sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"amd64\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:7b8b7289d0536a08eabdf71c20246e23f7116641db7e1d278592236ea4dcb30c\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"arm\",\"os\":\"linux\",\"variant\":\"v5\"},\"size\":525},{\"digest\":\"sha256:f130bd2d67e6e9280ac6d0a6c83857bfaf70234e8ef4236876eccfbd30973b1c\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"arm\",\"os\":\"linux\",\"variant\":\"v7\"},\"size\":525},{\"digest\":\"sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"arm64\",\"os\":\"linux\",\"variant\":\"v8\"},\"size\":525},{\"digest\":\"sha256:251bb7a536c7cce3437758971aab3a31c6da52fb43ff0654cff5b167c4486409\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"386\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:c2f204d26b4ea353651385001bb6bc371d8c4edcd9daf61d00ad365d927e00c0\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"mips64le\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:b836bb24a270b9cc935962d8228517fde0f16990e88893d935efcb1b14c0017a\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"ppc64le\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:98c9722322be649df94780d3fbe594fce7996234b259f27eac9428b84050c849\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"riscv64\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:c7b6944911848ce39b44ed660d95fb54d69bbd531de724c7ce6fc9f743c0b861\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"s390x\",\"os\":\"linux\"},\"size\":525},{\"digest\":\"sha256:8ad7c869546021c7af55ebb2c376dca59c3829ea4588e7fd22a4a1c8f5bcb472\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"amd64\",\"os\":\"windows\",\"os.version\":\"10.0.20348.469\"},\"size\":1125},{\"digest\":\"sha256:2ace993493fbbae9a0e44cff1c09589f59a8306fae084fbf3cf82dbef8b28442\",\"mediaType\":\"application\\/vnd.docker.distribution.manifest.v2+json\",\"platform\":{\"architecture\":\"amd64\",\"os\":\"windows\",\"os.version\":\"10.0.17763.2452\"},\"size\":1125}],\"mediaType\":\"application\\/vnd.docker.distribution.manifest.list.v2+json\",\"schemaVersion\":2}"
    public static String MANIFEST_DIGEST = 'sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800'

    public static String BLOB_MIME = 'application/vnd.docker.image.rootfs.diff.tar.gzip'
    public static String IMAGE_CONFIG_MIME = 'application/vnd.docker.container.image.v1+json'


    public static String MANIFEST_MIME = 'application/vnd.docker.distribution.manifest.v2+json'
    public static String MANIFEST_CONTENT = '''\
{
   "schemaVersion": 2,
   "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
   "config": {
      "mediaType": "application/vnd.docker.container.image.v1+json",
      "size": 1469,
      "digest": "sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412"
   },
   "layers": [
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 2479,
         "digest": "sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54"
      }
   ]
}
'''
}
