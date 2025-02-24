@doc("Options for Conda environments.")
@example(#{
    basePackages: "python=3.8",
    commands: #["pip install bwa", "pip install salmon"],
    mambaImage: "mambaorg/micromamba:0.15.3"
})
model CondaOpts {
  basePackages: string;
  commands: string[];
  mambaImage: string;
}
