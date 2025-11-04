import "./CondaOpts.tsp";
import "./CranOpts.tsp";

@doc("Specification for packages to be installed in a container.")
@example(#{
    type: "CONDA",
    entries: #["salmon", "bwa"],
    channels: #["conda-forge", "bioconda"]
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
  @doc("CRAN build options (when type is CRAN).")
  cranOpts?: CranOpts;
  @doc("Channels or repositories used for downloading packages.")
  channels?: string[];
}
