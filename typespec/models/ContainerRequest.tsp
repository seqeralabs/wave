import "./ContainerConfig.tsp";
import "./Packages.tsp";
import "./ScanMode.tsp";
import "./ScanLevel.tsp";
@doc("Request payload for creating a container token.")
model ContainerRequest {
  buildContext: ContainerLayer;
  buildRepository?: string;
  cacheRepository?: string;
  containerConfig: ContainerConfig;
  containerFile?: string;
  containerImage: string;
  containerIncludes: string[];
  containerPlatform: string;
  dryRun: boolean;
  fingerprint?: string;
  format: "sif" | "docker";
  freeze?: boolean;
  nameStrategy?: "none" | "tagPrefix" | "imageSuffix";
  mirrorRegistry?: string;
  packages?: Packages;
  scanMode?: ScanMode;
  scanLevels?: ScanLevel[];
  timestamp: string;
  towerAccessToken?: string;
  towerEndpoint?: string;
  towerRefreshToken?: string;
  towerWorkspaceId?: int32;
  workflowId: string;
}
