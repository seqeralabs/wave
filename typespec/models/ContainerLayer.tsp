@doc("Represents a layer in a container image.")
model Layer {
  location: string;
  gzipDigest: string;
  gzipSize: string;
  tarDigest: string;
}