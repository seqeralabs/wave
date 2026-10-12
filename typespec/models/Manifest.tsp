import "./ManifestLayer.tsp";

@doc("Manifest details of a container.")
@example(#{
    config: #{
        digest: "sha256:6c084f2e43f86a78",
        mediaType: "application/vnd.docker.container.image.v1+json",
        size: 1234
    },
    layers: #[
        #{
            digest: "sha256:6c084f2e43f86a78",
            mediaType: "application/vnd.docker.container.image.v1+json",
            size: 1234
        }
    ],
    mediaType: "application/vnd.docker.container.image.v1+json",
    schemaVersion: 2
})
model Manifest {
  config: {
    digest: string;
    mediaType: string;
    size: int64;
  };
  layers: ManifestLayer[];
  mediaType: string;
  schemaVersion: int32;
}
