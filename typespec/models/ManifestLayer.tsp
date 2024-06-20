@doc("Manifest layer details of a container.")
model ManifestLayer {
  mediaType: string;
  digest: string;
  size: int64;
}