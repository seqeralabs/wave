import "./ContainerPlatform.tsp";
import "./Status.tsp";

@doc("Response payload for container mirroring.")
@example(#{
    mirrorId: "6c084f2e43f86a78_1",
    digest: "sha256:1234567890abcdef",
    sourceImage: "docker.io/alpine:latest",
    targetImage: "docker.io/alpine:latest",
    platform: #{
                os: "LINUX",
                arch: "AMD64",
                variant: "v1"
              },
    creationTime: "2024-04-09T20:31:35.355423Z",
    status: Status.COMPLETED,
    duration: "123.914989000",
    exitCode: 0,
    logs: "Successfully mirrored image."
})
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
