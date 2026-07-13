@doc("Response payload for a scan submission.")
model ScanSubmitResponse {
  @doc("Identifier of the submitted scan.")
  scanId: string;
  @doc("Target container image being scanned.")
  targetImage?: string;
}
