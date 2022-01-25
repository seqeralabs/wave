# tower-reg
Sandbox for tower container registry 


### Reverse engineering of image pull 

Run the command 

    docker pull hello-world

The following https requests are made 


1. HEAD https://registry-1.docker.io/v2/library/hello-world/manifests/latest

        HTTP/1.1 200 OK
        content-length: 2562
        content-type: application/vnd.docker.distribution.manifest.list.v2+json
        docker-content-digest: sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800
        docker-distribution-api-version: registry/2.0
        etag: "sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800"
        date: Tue, 25 Jan 2022 12:06:55 GMT
        strict-transport-security: max-age=31536000
        docker-ratelimit-source: fa8b5f22-74c0-11e4-bea4-0242ac11001b
        connection: close
        
        
 2. GET https://registry-1.docker.io/v2/library/hello-world/manifests/sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800

        HTTP/1.1 200 OK
        content-length: 2562
        content-type: application/vnd.docker.distribution.manifest.list.v2+json
        docker-content-digest: sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800
        docker-distribution-api-version: registry/2.0
        etag: "sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800"
        date: Tue, 25 Jan 2022 12:06:56 GMT
        strict-transport-security: max-age=31536000
        docker-ratelimit-source: fa8b5f22-74c0-11e4-bea4-0242ac11001b
        connection: close

        {"manifests":[{"digest":"sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"amd64","os":"linux"},"size":525},{"digest":"sha256:7b8b7289d0536a08eabdf71c20246e23f7116641db7e1d278592236ea4dcb30c","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"arm","os":"linux","variant":"v5"},"size":525},{"digest":"sha256:f130bd2d67e6e9280ac6d0a6c83857bfaf70234e8ef4236876eccfbd30973b1c","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"arm","os":"linux","variant":"v7"},"size":525},{"digest":"sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"arm64","os":"linux","variant":"v8"},"size":525},{"digest":"sha256:251bb7a536c7cce3437758971aab3a31c6da52fb43ff0654cff5b167c4486409","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"386","os":"linux"},"size":525},{"digest":"sha256:c2f204d26b4ea353651385001bb6bc371d8c4edcd9daf61d00ad365d927e00c0","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"mips64le","os":"linux"},"size":525},{"digest":"sha256:b836bb24a270b9cc935962d8228517fde0f16990e88893d935efcb1b14c0017a","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"ppc64le","os":"linux"},"size":525},{"digest":"sha256:98c9722322be649df94780d3fbe594fce7996234b259f27eac9428b84050c849","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"riscv64","os":"linux"},"size":525},{"digest":"sha256:c7b6944911848ce39b44ed660d95fb54d69bbd531de724c7ce6fc9f743c0b861","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"s390x","os":"linux"},"size":525},{"digest":"sha256:8ad7c869546021c7af55ebb2c376dca59c3829ea4588e7fd22a4a1c8f5bcb472","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"amd64","os":"windows","os.version":"10.0.20348.469"},"size":1125},{"digest":"sha256:2ace993493fbbae9a0e44cff1c09589f59a8306fae084fbf3cf82dbef8b28442","mediaType":"application\/vnd.docker.distribution.manifest.v2+json","platform":{"architecture":"amd64","os":"windows","os.version":"10.0.17763.2452"},"size":1125}],"mediaType":"application\/vnd.docker.distribution.manifest.list.v2+json","schemaVersion":2}



 3. GET https://registry-1.docker.io/v2/library/hello-world/manifests/sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4

        HTTP/1.1 200 OK
        content-length: 525
        content-type: application/vnd.docker.distribution.manifest.v2+json
        docker-content-digest: sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4
        docker-distribution-api-version: registry/2.0
        etag: "sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4"
        date: Tue, 25 Jan 2022 12:06:56 GMT
        strict-transport-security: max-age=31536000
        docker-ratelimit-source: fa8b5f22-74c0-11e4-bea4-0242ac11001b
        connection: close

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
        
        
 4. GET https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412

        HTTP/1.1 307 Temporary Redirect
        content-type: application/octet-stream
        docker-distribution-api-version: registry/2.0
        location: https://production.cloudflare.docker.com/registry-v2/docker/registry/v2/blobs/sha256/fe/feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412/data?verify=1643115417-fSfYak%2BkTuLboNjQHqbYXKJkeys%3D
        date: Tue, 25 Jan 2022 12:06:57 GMT
        content-length: 0
        strict-transport-security: max-age=31536000
        connection: close
        


5. GET https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54

        HTTP/1.1 307 Temporary Redirect
        content-type: application/octet-stream
        docker-distribution-api-version: registry/2.0
        location: https://production.cloudflare.docker.com/registry-v2/docker/registry/v2/blobs/sha256/2d/2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54/data?verify=1643115417-%2Fv5fneaPH%2Fjj9Iw3LR1GuV7HnF8%3D
        date: Tue, 25 Jan 2022 12:06:57 GMT
        content-length: 0
        strict-transport-security: max-age=31536000
        connection: close


### Readble flow 

1. https://registry-1.docker.io/v2/library/hello-world/manifests/latest
  
        sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800

2. https://registry-1.docker.io/v2/library/hello-world/manifests/sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800

        {
          "manifests":[
              {
                "digest":"sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"amd64",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:7b8b7289d0536a08eabdf71c20246e23f7116641db7e1d278592236ea4dcb30c",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"arm",
                    "os":"linux",
                    "variant":"v5"
                },
                "size":525
              },
              {
                "digest":"sha256:f130bd2d67e6e9280ac6d0a6c83857bfaf70234e8ef4236876eccfbd30973b1c",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"arm",
                    "os":"linux",
                    "variant":"v7"
                },
                "size":525
              },
              {
                "digest":"sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"arm64",
                    "os":"linux",
                    "variant":"v8"
                },
                "size":525
              },
              {
                "digest":"sha256:251bb7a536c7cce3437758971aab3a31c6da52fb43ff0654cff5b167c4486409",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"386",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:c2f204d26b4ea353651385001bb6bc371d8c4edcd9daf61d00ad365d927e00c0",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"mips64le",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:b836bb24a270b9cc935962d8228517fde0f16990e88893d935efcb1b14c0017a",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"ppc64le",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:98c9722322be649df94780d3fbe594fce7996234b259f27eac9428b84050c849",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"riscv64",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:c7b6944911848ce39b44ed660d95fb54d69bbd531de724c7ce6fc9f743c0b861",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"s390x",
                    "os":"linux"
                },
                "size":525
              },
              {
                "digest":"sha256:8ad7c869546021c7af55ebb2c376dca59c3829ea4588e7fd22a4a1c8f5bcb472",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"amd64",
                    "os":"windows",
                    "os.version":"10.0.20348.469"
                },
                "size":1125
              },
              {
                "digest":"sha256:2ace993493fbbae9a0e44cff1c09589f59a8306fae084fbf3cf82dbef8b28442",
                "mediaType":"application\/vnd.docker.distribution.manifest.v2+json",
                "platform":{
                    "architecture":"amd64",
                    "os":"windows",
                    "os.version":"10.0.17763.2452"
                },
                "size":1125
              }
          ],
          "mediaType":"application\/vnd.docker.distribution.manifest.list.v2+json",
          "schemaVersion":2
        }

 3. GET https://registry-1.docker.io/v2/library/hello-world/manifests/sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4

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

4. GET https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412

        {
          "architecture":"amd64",
          "config":{
              "Hostname":"",
              "Domainname":"",
              "User":"",
              "AttachStdin":false,
              "AttachStdout":false,
              "AttachStderr":false,
              "Tty":false,
              "OpenStdin":false,
              "StdinOnce":false,
              "Env":[
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
              ],
              "Cmd":[
                "/hello"
              ],
              "Image":"sha256:b9935d4e8431fb1a7f0989304ec86b3329a99a25f5efdc7f09f3f8c41434ca6d",
              "Volumes":null,
              "WorkingDir":"",
              "Entrypoint":null,
              "OnBuild":null,
              "Labels":null
          },
          "container":"8746661ca3c2f215da94e6d3f7dfdcafaff5ec0b21c9aff6af3dc379a82fbc72",
          "container_config":{
              "Hostname":"8746661ca3c2",
              "Domainname":"",
              "User":"",
              "AttachStdin":false,
              "AttachStdout":false,
              "AttachStderr":false,
              "Tty":false,
              "OpenStdin":false,
              "StdinOnce":false,
              "Env":[
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
              ],
              "Cmd":[
                "/bin/sh",
                "-c",
                "#(nop) ",
                "CMD [\"/hello\"]"
              ],
              "Image":"sha256:b9935d4e8431fb1a7f0989304ec86b3329a99a25f5efdc7f09f3f8c41434ca6d",
              "Volumes":null,
              "WorkingDir":"",
              "Entrypoint":null,
              "OnBuild":null,
              "Labels":{
                
              }
          },
          "created":"2021-09-23T23:47:57.442225064Z",
          "docker_version":"20.10.7",
          "history":[
              {
                "created":"2021-09-23T23:47:57.098990892Z",
                "created_by":"/bin/sh -c #(nop) COPY file:50563a97010fd7ce1ceebd1fa4f4891ac3decdf428333fb2683696f4358af6c2 in / "
              },
              {
                "created":"2021-09-23T23:47:57.442225064Z",
                "created_by":"/bin/sh -c #(nop)  CMD [\"/hello\"]",
                "empty_layer":true
              }
          ],
          "os":"linux",
          "rootfs":{
              "type":"layers",
              "diff_ids":[
                "sha256:e07ee1baac5fae6a26f30cabfe54a36d3402f96afda318fe0a96cec4ca393359"
              ]
          }
        }

5. GET https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54

    (the blob binary)

## Docs & Links 
* https://docs.docker.com/registry/spec/api/


## Open points 
* Eval layer injection vs on-fly build 
* ~~Understand how checksum is computed for manifest downloaded at request #3~~ (it was just a formatting problem, the checksum is just the sha256 of the json response body) 
* Benchmark Goofys performance operating in the container vs in the host 
