import "./ContainerStatus.tsp";

@doc("Response payload for container token creation.")
@example(#{
    containerToken:"732b73aa17c8",
    targetImage:"wave.seqera.io/wt/732b73aa17c8/build/dev:salmon_bwa--5e49881e6ad74121",
    expiration:"2024-04-09T21:19:01.715321Z",
    buildId:"5e49881e6ad74121_1",
    cached:false,
    freeze:false,
    mirror:false,
    requestId:"5e49881e6ad74121",
    scanId:"5e49881e6ad74121",
    containerImage:"docker.io/build/dev:salmon_bwa--5e49881e6ad74121",
    status:ContainerStatus.PENDING
    })
model ContainerResponse {
  buildId: string;
  cached: boolean;
  containerImage: string;
  containerToken: string;
  expiration: string;
  freeze: boolean;
  mirror: boolean;
  requestId: string;
  scanId: string;
  status: ContainerStatus;
  targetImage: string;
}
