@doc("Options for Conda environments.")
@example(#{
    basePackages: "python=3.8",
    commands: #["pip install bwa", "pip install salmon"],
    mambaImage: "mambaorg/micromamba:0.15.3",
    baseImage: "ubuntu:24.04"
})
model CondaOpts {
  @doc("Names of base packages.")
  basePackages: string;
  @doc("Command to be included in the container.")
  commands: string[];
  @doc("Name of the docker image used to build Conda containers.")
  mambaImage: string;
  @doc("Base image for the final stage of multi-stage builds.")
  baseImage: string;
}
