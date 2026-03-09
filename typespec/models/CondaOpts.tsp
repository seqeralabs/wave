@doc("Options for Conda environments.")
@example(#{
    basePackages: "python=3.8",
    commands: #["RUN pip install bwa", "RUN pip install salmon"],
    mambaImage: "mambaorg/micromamba:0.15.3",
    baseImage: "ubuntu:24.04"
})
model CondaOpts {
  @doc("Names of base packages.")
  basePackages: string;
  @doc("Valid Dockerfile instructions to be included in the container (e.g., RUN, CMD). Each command must start with a valid Dockerfile instruction keyword.")
  commands: string[];
  @doc("Name of the docker image used to build Conda containers.")
  mambaImage: string;
  @doc("Base image for the final stage of multi-stage builds.")
  baseImage: string;
}
