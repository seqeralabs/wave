@doc("Request payload for inspecting a container.")
model ContainerInspectRequest {
  containerImage: string;
  towerAccessToken: string;
  towerEndpoint: string;
  towerWorkspaceId: int64;
}