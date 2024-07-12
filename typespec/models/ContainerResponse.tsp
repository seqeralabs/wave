@doc("Response payload for container token creation.")
model ContainerResponse {
  buildId: string;
  cached: boolean;
  containerImage: string;
  containerToken: string;
  expiration: string;
  freeze?: boolean;
  targetImage: string;
}