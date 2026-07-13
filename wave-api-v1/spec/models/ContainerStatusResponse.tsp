import "./ContainerStatus.tsp";

@doc("Response payload for container status.")
@example(#{
    id:"6c084f2e43f86a78",
    buildId:"6c084f2e43f86a78_1",
    status:ContainerStatus.DONE,
    creationTime:"2024-04-09T20:31:35.355423Z",
    detailsUri:"https://wave.seqera.io/view/builds/6c084f2e43f86a78_1",
    duration:"123.914989000",
    succeeded:true,
    scanId:"6c084f2e43f86a78_1",
})
model ContainerStatusResponse {
  id: string;
  status: ContainerStatus;
  buildId: string;
  mirrorId?: string;
  scanId: string;
  vulnerabilities?: Record<vulnerability>;
  succeeded: boolean;
  reason?: string;
  detailsUri: string;
  creationTime: string;
  duration: string;
}

model vulnerability {
  severity: string;
  count: int32
}
