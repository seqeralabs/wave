@doc("Options for CRAN/R environments.")
@example(#{
    rImage: "rocker/r-ver:4.4.1",
    basePackages: "littler r-cran-docopt",
    commands: #["RUN apt-get update", "RUN apt-get install -y libcurl4-openssl-dev"]
})
model CranOpts {
  @doc("Name of the R Docker image used to build CRAN containers.")
  rImage: string;
  @doc("Names of base system packages to install.")
  basePackages: string;
  @doc("Valid Dockerfile instructions to be included in the container (e.g., RUN, CMD). Each command must start with a valid Dockerfile instruction keyword.")
  commands: string[];
}
