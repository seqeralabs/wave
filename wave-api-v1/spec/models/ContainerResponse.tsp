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
  @doc("Unique identifier for the build.")
  buildId: string;
  @doc("Indicates if the build is cached.")
  cached: boolean;
  @doc("Container image to be used.")
  containerImage: string;
  @doc("Token to access the container.")
  containerToken: string;
  @doc("The expiration timestamp of the Wave container using ISO-8601 format.")
  expiration: string;
  @doc("Indicates if the build is pushed to user container registry.")
  freeze: boolean;
  @doc("Indicates if its a mirror request.")
  mirror: boolean;
  @doc("Unique identifier for the request.")
  requestId: string;
  @doc("Unique identifier for the scan.")
  scanId: string;
  @doc("Status of the container build.")
  status: ContainerStatus;
  @doc("The Wave container image name")
  targetImage: string;
}
