@doc("Response payload for container token creation.")
model ContainerResponse {
  buildId: string;
  cached: boolean;
  containerToken: string;
  expiration: string;
  targetImage: string;
}