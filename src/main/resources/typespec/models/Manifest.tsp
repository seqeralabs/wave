import "./ManifestLayer.tsp";

@doc("Manifest details of a container.")
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