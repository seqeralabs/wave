import "./CondaOpts.tsp";

@doc("Package configurations for container builds.")
model Packages {
  channels: string[];
  condaOpts?: CondaOpts;
  entries: string[];
  environment: string;
  type: "CONDA";
}
