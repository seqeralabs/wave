import "./ContainerConfig.tsp";
import "./CondaPackages.tsp";
import "./ScanMode.tsp";
import "./ScanLevel.tsp";

@doc("Request payload for creating a container token.")
@example(#{
    packages:#{
            type: "CONDA",
            entries: #["salmon", "bwa"],
            channels: #["conda-forge", "bioconda"]
        },
        format: "docker",
        containerPlatform:"linux/amd64"
})
model ContainerRequest {
  buildContext?: ContainerLayer;
  buildRepository?: string;
  cacheRepository?: string;
  containerConfig?: ContainerConfig;
  containerFile?: string;
  containerImage?: string;
  containerIncludes?: string[];
  containerPlatform: string;
  dryRun?: boolean;
  fingerprint?: string;
  format: "sif" | "docker";
  freeze?: boolean;
  nameStrategy?: "none" | "tagPrefix" | "imageSuffix";
  mirror?: boolean;
  packages?: CondaPackages;
  scanMode?: ScanMode;
  scanLevels?: ScanLevel[];
  timestamp?: string;
  towerAccessToken?: string;
  towerEndpoint?: string;
  towerRefreshToken?: string;
  towerWorkspaceId?: int32;
  workflowId?: string;
}
