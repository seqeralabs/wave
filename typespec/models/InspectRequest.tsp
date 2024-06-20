@doc("Request payload for inspecting a container.")
model InspectRequest {
  containerImage: string;
  towerAccessToken: string;
  towerEndpoint: string;
  towerWorkspaceId: string;
}