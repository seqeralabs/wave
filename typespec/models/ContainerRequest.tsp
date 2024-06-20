import "./ContainerConfig.tsp";
import "./Packages.tsp";

@doc("Request payload for creating a container token.")
model ContainerRequest {
  containerImage: string;
  containerFile?: string;
  containerConfig: ContainerConfig;
  condaFile?: string;
  spackFile?: string;
  containerPlatform: string;
  buildRepository?: string;
  cacheRepository?: string;
  timestamp: string;
  fingerprint?: string;
  freeze?: boolean;
  towerAccessToken?: string;
  towerRefreshToken?: string;
  towerEndpoint?: string;
  towerWorkspaceId?: int32;
  packages?: Packages;
  nameStrategy?: string;
}