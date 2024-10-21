model WaveBuildRecord {
    buildId: string;
    condaFile: string;
    digest: string;
    dockerFile: string;
    duration: int64;
    exitStatus: int32;
    format: "docker" | "sif";
    offsetId: string;
    platform: string;
    requestIp: string;
    scanId: string;
    startTime: string;
    succeeded: boolean;
    targetImage: string;
    userEmail: string;
    userId: int64;
    userName: string;
  }
