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
  @doc("The launch command to be used by the Wave container, e.g., `['echo', 'Hello world']` (optional).")
  cmd: string[];
  @doc("The container entrypoint command, e.g., `['/bin/bash']`.")
  entrypoint: string[];
  @doc("The environment variables to be defined in the Wave container, e.g., `['FOO=one','BAR=two']` (optional).")
  env: string[];
  layers: ContainerLayer[];
  @doc("The work directory to be used in the Wave container, e.g., `/some/work/dir` (optional).")
  workingDir: string;
}
