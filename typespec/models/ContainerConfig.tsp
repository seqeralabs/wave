import "./ContainerLayer.tsp";

@doc("Configuration details for a container.")
model ContainerConfig {
  entrypoint: string[];
  cmd: string[];
  env: string[];
  workingDir: string;
  layers: Layer[];
}