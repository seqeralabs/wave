import "./ContainerInspectConfig.tsp";
import "./Manifest.tsp";

@doc("Response payload for inspecting a container.")
@example(#{
 Container: #{
  registry: "docker.io",
  hostName: "docker.io",
  imageName: "alpine",
  reference: "latest",
  digest: "sha256:1234567890abcdef",
  config: #{
   architecture: "linux/amd64",
   config: #{
    attachStdin: false,
    attachStdout: true,
    attachStderr: true,
    tty: false,
    env: #["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],
    cmd: #["sh"],
    image: "alpine:latest"
   },
   container: "docker.io/alpine:latest",
   created: "2021-06-10T15:00:00.000000000Z",
   rootfs: #{
    diff_ids: #["sha256:1234567890abcdef"],
    type: "layers"
   }
  },
  manifest: #{
   schemaVersion: 2,
   mediaType: "application/vnd.docker.distribution.manifest.v2+json",
   config: #{
    mediaType: "application/vnd.docker.container.image.v1+json",
    size: 123456,
    digest: "sha256:1234567890abcdef"
   },
   layers: #[
    #{
     mediaType: "application/vnd.docker.image.rootfs.diff.tar.gzip",
     size: 123456,
     digest: "sha256:1234567890abcdef"
    }
   ]
  },
  v1: false,
  v2: true,
  oci: false
 }
})
model ContainerInspectResponse {
 Container: {
  registry: string;
  hostName: string;
  imageName: string;
  reference: string;
  digest: string;
  config: ContainerInspectConfig;
  manifest: Manifest;
  v1: boolean;
  v2: boolean;
  oci: boolean;
  }
}
