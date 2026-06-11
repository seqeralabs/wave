@doc("Wave service information.")
model ServiceInfo {
  @doc("Application version, e.g. `1.33.5`.")
  version: string;
  @doc("Build commit id (short SHA).")
  commitId: string;
}

@doc("Response payload for the service-info endpoint.")
model ServiceInfoResponse {
  serviceInfo: ServiceInfo;
}
