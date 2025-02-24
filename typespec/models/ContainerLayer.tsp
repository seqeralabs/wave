@doc("Represents a layer in a container image.")
@example(#{
  gzipDigest: "sha256:1234567890abcdef",
  gzipSize: "123456",
  location: "https://example.com/image.tar.gz",
  skipHashing: false,
  tarDigest: "sha256:abcdef1234567890"
})
model ContainerLayer {
  gzipDigest: string;
  gzipSize: string;
  location: string;
  skipHashing: boolean;
  tarDigest: string;
}
