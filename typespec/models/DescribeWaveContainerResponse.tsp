@doc("Response payload describing a Wave container request.")
model DescribeWaveContainerResponse {
  token: string;
  expiration: string;
  request: DescribeWaveContainerRequestInfo;
  build: DescribeWaveContainerBuildInfo;
  source: DescribeWaveContainerInfo;
  wave: DescribeWaveContainerInfo;
}

@doc("Sanitized Wave container request details.")
model DescribeWaveContainerRequestInfo {
  user?: UserInfo;
  workspaceId?: int64;
  containerImage?: string;
  containerConfig?: ContainerConfig;
  platform?: string;
  towerEndpoint?: string;
  fingerprint?: string;
  timestamp?: string;
  zoneId?: string;
  ipAddress?: string;
}

@doc("Sanitized user identity details.")
model UserInfo {
  id: int64;
}

@doc("Wave container build details.")
model DescribeWaveContainerBuildInfo {
  containerFile?: string;
  condaFile?: string;
  buildRepository?: string;
  cacheRepository?: string;
  buildId?: string;
  buildNew?: boolean;
  freeze?: boolean;
}

@doc("Container image details.")
model DescribeWaveContainerInfo {
  image?: string;
  digest?: string;
}
