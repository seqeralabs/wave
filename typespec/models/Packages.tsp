import "./CondaOpts.tsp";
import "./SpackOpts.tsp";

@doc("Package configurations for container builds.")
model Packages {
  channels: string[];
  condaOpts?: CondaOpts;
  entries: string[];
  environment: string;
  spackOpts?: SpackOpts;
  type: "CONDA" | "SPACK";
}