import "./Vulnerability.tsp";

@doc("Response Payload for wave scan")
model WaveScanRecord {
    buildId: string;
    duration: int64;
    containerImage: string;
    id: string;
    startTime: string;
    status: string;
    vulnerabilities: Vulnerability[];
  }
