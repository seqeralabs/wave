import "./RootFS.tsp";

@doc("Configuration details of a container.")
model Config {
  architecture: string;
  config: {
    attachStdin: boolean;
    attachStdout: boolean;
    attachStderr: boolean;
    tty: boolean;
    env: string[];
    cmd: string[];
    image: string;
  };
  container: string;
  created: string;
  rootfs: RootFS;
}