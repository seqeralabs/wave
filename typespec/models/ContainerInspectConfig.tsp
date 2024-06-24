import "./RootFS.tsp";

@doc("Configuration details of a container.")
model Config {
  architecture: string;
  config: {
    attachStderr: boolean;
    attachStdin: boolean;
    attachStdout: boolean;
    cmd: string[];
    env: string[];
    image: string;
    tty: boolean;
  };
  container: string;
  created: string;
  rootfs: RootFS;
}