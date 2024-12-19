import "./ContainerStatus.tsp";

@doc("Response payload for container token creation.")
model ContainerResponse {
  buildId: string;
  cached: boolean;
  containerImage: string;
  containerToken: string;
  expiration: string;
  freeze: boolean;
  mirror: boolean;
  requestId: string;
  scanId: string;
  status: ContainerStatus;
  targetImage: string;
}
