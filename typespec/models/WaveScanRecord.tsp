import "./Vulnerability.tsp";

@doc("Response Payload for wave scan")
model WaveScanRecord {
    id: string;
    buildId: string;
    startTime: string; // Assuming ISO8601 format for simplicity
    duration: int64; // Approximation, consider using seconds or milliseconds
    status: string;
    vulnerabilities: Vulnerability[]; // Assuming a list of Vulnerabilities
  }