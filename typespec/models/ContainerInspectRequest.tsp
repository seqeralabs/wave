@doc("Request payload for inspecting a container.")
@example(#{
    containerImage: "docker.io/alpine:latest",
    towerAccessToken: "1234567890abcdef",
    towerEndpoint: "https://api.cloud.seqera.io",
    towerWorkspaceId: 1234567890
})
model ContainerInspectRequest {
  @doc("Name of the container to be inpected, e.g., `docker.io/library/ubuntu:latest`")
  containerImage: string;
  @doc("Access token of the user account granting the access to the Seqera Platform service specified via `towerEndpoint` (optional).  ")
  towerAccessToken: string;
  @doc("Seqera Platform service endpoint from where container registry credentials are retrieved (optional). Default `https://api.cloud.seqera.io`. ")
  towerEndpoint: string;
  @doc("ID of the Seqera Platform workspace from where the container registry credentials are retrieved (optional). When omitted the personal workspace is used.")
  towerWorkspaceId: int64;
}
