@doc("Response payload for build status.")
model BuildStatusResponse {
  id: string;
  status: string;
  startTime: string;
  duration: string;
  succeeded: boolean;
}