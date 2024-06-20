import "./CondaOpts.tsp";
import "./SpackOpts.tsp";

@doc("Package configurations for container builds.")
model Packages {
  type: string;
  environment: string;
  entries: string[];
  channels: string[];
  condaOpts?: CondaOpts;
  spackOpts?: SpackOpts;
}