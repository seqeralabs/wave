@doc("Options for Spack environments. Spack support will be removed in future releases")
model SpackOpts {
  basePackages: string;
  commands: string[];
}
