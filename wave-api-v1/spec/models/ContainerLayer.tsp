@doc("Represents a layer in a container image.")
@example(#{
  gzipDigest: "sha256:1234567890abcdef",
  gzipSize: "123456",
  location: "https://example.com/image.tar.gz",
  skipHashing: false,
  tarDigest: "sha256:abcdef1234567890"
})
model ContainerLayer {
  @doc("The SHA256 checksum of the provided layer tar gzip file, e.g., `sha256:a7c724b02...`.")
  gzipDigest: string;
  @doc("The size in bytes of the the provided layer tar gzip file.")
  gzipSize: string;
  @doc("Specifies a container image layer stored as a tar.gz file (optional). Either a HTTP URL to the file or a base64 encoded string prefixed with `data:`.")
  location: string;
  @doc("If true, the layer tar file will not be hashed.")
  skipHashing: boolean;
  @doc("The SHA256checksum of the provided tar file, e.g., `sha256:a7c724b02...`.")
  tarDigest: string;
}
