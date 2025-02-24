@doc("Request payload for inspecting a container.")
@example(#{
    containerImage: "docker.io/alpine:latest",
    towerAccessToken: "1234567890abcdef",
    towerEndpoint: "https://api.cloud.seqera.io",
    towerWorkspaceId: 1234567890
})
model ContainerInspectRequest {
  containerImage: string;
  towerAccessToken: string;
  towerEndpoint: string;
  towerWorkspaceId: int64;
}
