import "./ContainerStatus.tsp";

@doc("Response payload for container status.")
model ContainerStatusResponse {
  id: string;
  status: ContainerStatus;
  buildId: string;
  mirrorId: string;
  scanId: string;
  vulnerabilities: Record<vulnerability>;
  succeeded: boolean;
  reason: string;
  detailsUri: string;
  creationTime: string;
  duration: string;
}

model vulnerability {
  severity: string;
  count: int32
}
