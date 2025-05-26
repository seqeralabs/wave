import "./RootFS.tsp";

@doc("Configuration details of a container.")
@example(#{
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
})
model ContainerInspectConfig {
  architecture: string;
  config: {
    attachStdin: boolean;
    attachStdout: boolean;
    attachStderr: boolean;
    tty: boolean;
    env: string[];
    cmd: string[];
    image: string;
  };
  container: string;
  created: string;
  rootfs: RootFS;
}
