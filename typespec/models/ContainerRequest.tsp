import "./ContainerConfig.tsp";
import "./PackagesSpec.tsp";
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
@example(#{
    packages:#{
            type: "CRAN",
            entries: #["dplyr", "ggplot2", "bioc::GenomicRanges"],
            channels: #["cran", "bioconductor"],
            cranOpts: #{
                rImage: "rocker/r-ver:4.4.1",
                basePackages: "littler r-cran-docopt",
                commands: #["apt-get update", "apt-get install -y libcurl4-openssl-dev"]
            }
        },
        format: "docker",
        containerPlatform:"linux/amd64"
})
model ContainerRequest {
  buildContext?: ContainerLayer;
  @doc("Container repository where container builds should be pushed, e.g., `docker.io/user/my-image` (optional).")
  buildRepository?: string;
  @doc("Container repository used to cache build layers `docker.io/user/my-cache` (optional).")
  cacheRepository?: string;
  containerConfig?: ContainerConfig;
  @doc("Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` must be omitted.")
  containerFile?: string;
  @doc("Name of the container to be served, e.g., `docker.io/library/ubuntu:latest` (optional). If omitted, the `containerFile` must be provided.  ")
  containerImage?: string;
  @doc("List of container images to include in the built container (optional).")
  containerIncludes?: string[];
  @doc("Target container architecture of the built container, e.g., `linux/amd64` (optional). Currently only supporting amd64 and arm64.")
  containerPlatform: string;
  @doc("Request to build the container in a dry-run mode.")
  dryRun?: boolean;
  @doc("Request unique fingerprint.")
  fingerprint?: string;
  @doc("The format of the container to be built. Its values can be `sif` for singularity or `docker` as default. ")
  format: "sif" | "docker";
  @doc("Freeze requires buildRepository to push the build container to a user-defined repository. This provides the container URL from the user-defined repository, not the Wave generated URL. This URL won't change.")
  freeze?: boolean;
  @doc("The name strategy to be used to create the name of the container built by Wave. Its values can be `none`, `tagPrefix`, or `imageSuffix`. ")
  nameStrategy?: "none" | "tagPrefix" | "imageSuffix";
  mirror?: boolean;
  @doc("Packages to be installed in the container.")
  packages?: PackagesSpec;
  scanMode?: ScanMode;
  scanLevels?: ScanLevel[];
  @doc("Request submission timestamp using ISO-8601.")
  timestamp?: string;
  @doc("Access token of the user account granting access to the Seqera Platform service specified via `towerEndpoint` (optional).")
  towerAccessToken?: string;
  @doc("Seqera Platform service endpoint from where container registry credentials are retrieved (optional). Default `https://api.cloud.seqera.io`.")
  towerEndpoint?: string;
  @doc("Token to refresh ``towerAccessToken` after it become invalid (optional).")
  towerRefreshToken?: string;
  @doc("ID of the Seqera Platform workspace from where the container registry credentials are retrieved (optional). When omitted the personal workspace is used.")
  towerWorkspaceId?: int64;
  @doc("ID of the Seqera Platform workspace from which this container request originates (optional).")
  workflowId?: string;
}
