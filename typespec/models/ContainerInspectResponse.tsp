import "./ContainerInspectConfig.tsp";
import "./Manifest.tsp";

@doc("Response payload for inspecting a container.")
model ContainerInspectResponse {
 Container: {
  registry: string;
  hostName: string;
  imageName: string;
  reference: string;
  digest: string;
  config: Config;
  manifest: Manifest;
  v1: boolean;
  v2: boolean;
  oci: boolean;
  }
}
