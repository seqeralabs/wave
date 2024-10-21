@doc("Manifest layer details of a container.")
model ManifestLayer {
  digest: string;
  mediaType: string;
  size: int64;
}