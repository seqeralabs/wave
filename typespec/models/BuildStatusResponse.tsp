import "./Status.tsp";

@doc("Response payload for build status.")
@example(#{
    id:"6c084f2e43f86a78_1",
    status:Status.COMPLETED,
    startTime:"2024-04-09T20:31:35.355423Z",
    duration: "123.914989000",
    succeeded: true
}
)
model BuildStatusResponse {
  duration: string;
  id: string;
  startTime: string;
  status: Status;
  succeeded: boolean;
}
