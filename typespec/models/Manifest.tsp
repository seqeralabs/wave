import "./ManifestLayer.tsp";

@doc("Manifest details of a container.")
model Manifest {
  schemaVersion: int32;
  mediaType: string;
  config: {
    mediaType: string;
    digest: string;
    size: int64;
  };
  layers: ManifestLayer[];
}