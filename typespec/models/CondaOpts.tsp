@doc("Options for Conda environments.")
model CondaOpts {
  mambaImage: string;
  commands: string[];
  basePackages: string;
}