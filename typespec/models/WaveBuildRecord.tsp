model WaveBuildRecord {
    buildId: string;
    dockerFile: string;
    condaFile: string;
    spackFile: string;
    targetImage: string;
    userName: string;
    userEmail: string;
    userId: int64;
    requestIp: string;
    startTime: string;
    offsetId: string;
    duration: int64;
    exitStatus: int32;
    platform: string;
    scanId: string;
    format: "docker" | "sif";
    digest: string;
    succeeded: boolean;
  }
  