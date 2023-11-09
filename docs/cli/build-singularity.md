---
title: Build a Singularity container
---

The Wave CLI supports building a [Singularity]. A target build repository, specified with the `--build-repo` argument, is required to build a Singularity container. You can build a Singularity container from several sources:

- A [SingularityCE] def file
- A Docker container image with an optional local context directory
- Conda packages
- Spack packages

The following limitations apply:

- The `linux/arm64` platform is not currently supported

## Related CLI arguments

The following arguments are used to build a Singularity container:

- `--build-repo`: A target repository to save the built container to.
- `--freeze`: Enable a container freeze.
- `--singularity` and `-s`: Build a Singularity container.
- `--tower-token`: A Seqera Platform auth token so that Wave can access your private registry credentials. Not required if the `TOWER_ACCESS_TOKEN` environment variable is set.
- `--tower-workspace-id`: A Seqera Platform workspace ID, such as `1234567890`, where credentials may be stored.

## Example usage

In the following example, a Docker base imagine is augmented:

```
wave -i alpine --layer context-dir/ --build-repo docker.io/user/repo
```

In the following example, a SingularityCE def file is specified:

```
wave -f hello-world.def --singularity --freeze --build-repo docker.io/user/repo
```

In the following example, two Conda packages are specified:

```
wave --conda-package bamtools=2.5.2 --conda-package samtools=1.17 --freeze --singularity --build-repo docker.io/user/repo
```

[Singularity]: https://docs.sylabs.io/guides/latest/user-guide/introduction.html
[SingularityCE]: https://docs.sylabs.io/guides/latest/user-guide/definition_files.html
