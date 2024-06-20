@doc("Response payload for container token creation.")
model ContainerResponse {
  containerToken: string;
  targetImage: string;
  expiration: string;
  buildId: string;
  cached: boolean;
}