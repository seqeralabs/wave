import "./ContainerLayer.tsp";

@doc("Configuration details for a container.")
model ContainerConfig {
  cmd: string[];
  entrypoint: string[];
  env: string[];
  layers: ContainerLayer[];
  workingDir: string;
}
