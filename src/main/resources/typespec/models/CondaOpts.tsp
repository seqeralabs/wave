@doc("Options for Conda environments.")
model CondaOpts {
  basePackages: string;
  commands: string[];
  mambaImage: string;
}