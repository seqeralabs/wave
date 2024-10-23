import "./User.tsp";

@doc("Wave container details")
model WaveContainerRecord {
  user: User;
  workspaceId: int64;
  containerImage?: string;
  containerFile?: string;
  containerConfig: ContainerConfig;
  condaFile?: string;
  platform?: string;
  towerEndpoint?: string;
  buildRepository?: string;
  cacheRepository?: string;
  fingerprint?: string;
  timestamp: string;
  zoneId?: string;
  ipAddress?: string;
  sourceImage?: string;
  sourceDigest?: string;
  waveImage?: string;
  waveDigest?: string;
  expiration: string;
  buildId?: string;
  buildNew?: boolean;
  freeze?: boolean;
  fusionVersion?: string;
}
