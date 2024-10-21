import "./Status.tsp";

@doc("Response payload for build status.")
model BuildStatusResponse {
  duration: string;
  id: string;
  startTime: string;
  status: Status;
  succeeded: boolean;
}
