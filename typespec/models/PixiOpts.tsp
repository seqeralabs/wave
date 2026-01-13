@doc("Options for Pixi environments.")
@example(#{
    basePackages: "python=3.8",
    commands: #["RUN pip install numpy", "RUN pip install pandas"],
    pixiImage: "ghcr.io/prefix-dev/pixi:latest",
    baseImage: "ubuntu:24.04"
})
model PixiOpts {
  @doc("Names of base packages.")
  basePackages: string;
  @doc("Valid Dockerfile instructions to be included in the container (e.g., RUN, CMD, ENTRYPOINT, USER, WORKDIR, ENV, COPY, ADD). Each command must start with a valid Dockerfile instruction keyword.")
  commands: string[];
  @doc("Name of the docker image used to build Pixi containers.")
  pixiImage: string;
  @doc("Base image for the final stage of multi-stage builds.")
  baseImage: string;
}