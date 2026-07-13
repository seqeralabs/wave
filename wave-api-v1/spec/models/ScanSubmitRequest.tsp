@doc("Request payload for submitting a container scan.")
model ScanSubmitRequest {
  @doc("Container image to scan, e.g. `docker.io/library/ubuntu:latest`. Mutually exclusive with mirrorId and buildId.")
  containerImage?: string;
  @doc("Existing mirror id to scan. Mutually exclusive with containerImage and buildId.")
  mirrorId?: string;
  @doc("Existing build id to scan. Mutually exclusive with containerImage and mirrorId.")
  buildId?: string;
  @doc("Target platform of the image (optional, e.g. `linux/amd64`).")
  containerPlatform?: string;
  @doc("Seqera Platform access token granting registry access (optional).")
  towerAccessToken?: string;
  @doc("Seqera Platform endpoint (optional).")
  towerEndpoint?: string;
  @doc("Seqera Platform workspace id (optional).")
  towerWorkspaceId?: int64;
}
