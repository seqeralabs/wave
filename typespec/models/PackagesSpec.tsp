import "./CondaOpts.tsp";
import "./CranOpts.tsp";
import "./PixiOpts.tsp";

@doc("Specification for packages to be installed in a container.")
@example(#{
    type: "CONDA",
    entries: #["salmon", "bwa"],
    channels: #["conda-forge", "bioconda"]
})
@example(#{
    type: "CRAN",
    entries: #["dplyr", "ggplot2"],
    channels: #["cran"],
    cranOpts: #{
        rImage: "rocker/r-ver:4.4.1",
        basePackages: "littler r-cran-docopt",
        commands: #["apt-get update && apt-get install -y libcurl4-openssl-dev"]
    }
})
model PackagesSpec {
  @doc("The type of package manager: CONDA or CRAN.")
  type: "CONDA" | "CRAN";
  @doc("The package environment file encoded as a base64 string.")
  environment?: string;
  @doc("A list of one or more package names.")
  entries?: string[];
  @doc("Conda build options (when type is CONDA).")
  condaOpts?: CondaOpts;
  @doc("Pixi build options (when type is CONDA).")
  pixiOpts?: PixiOpts;
  @doc("CRAN build options (when type is CRAN).")
  cranOpts?: CranOpts;
  @doc("Channels or repositories used for downloading packages.")
  channels?: string[];
}
