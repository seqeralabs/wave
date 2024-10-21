@doc("Represents a layer in a container image.")
model ContainerLayer {
  gzipDigest: string;
  gzipSize: string;
  location: string;
  skipHashing: boolean;
  tarDigest: string;
}
