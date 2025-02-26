import "./CondaOpts.tsp";

@doc("Package configurations for container builds.")
model CondaPackages {
  @doc("Conda channels to search for packages.")
  channels: string[];
  condaOpts?: CondaOpts;
  @doc("Conda packages to install.")
  entries: string[];
  @doc("The package environment file encoded as a base64 string.")
  environment?: string;
  @doc("This represents the type of package builder. Use `CONDA`.")
  type: "CONDA";
}
