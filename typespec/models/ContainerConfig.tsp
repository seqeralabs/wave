import "./ContainerLayer.tsp";

@doc("Configuration details for a container.")
@example(#{
    cmd: #["echo", "hello"],
    entrypoint: #["/bin/sh"],
    env: #["FOO=bar"],
    layers: #[
      #{
        gzipDigest: "sha256:1234567890abcdef",
        gzipSize: "1234",
        location: "https://seqera.io/layer.tar.gz",
        skipHashing: false,
        tarDigest: "sha256:abcdef1234567890"
      }
      ],
      workingDir: "/app"
})
model ContainerConfig {
  cmd: string[];
  entrypoint: string[];
  env: string[];
  layers: ContainerLayer[];
  workingDir: string;
}
