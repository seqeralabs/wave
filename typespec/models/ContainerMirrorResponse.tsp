import "./ContainerPlatform.tsp";
import "./Status.tsp";

@doc("Response payload for container mirroring.")
model ContainerMirrorResponse {
  mirrorId: string;
  digest: string;
  sourceImage: string;
  targetImage: string;
  platform: ContainerPlatform;
  creationTime: string;
  status: Status;
  duration: string;
  exitCode: int32;
  logs: string;
}
