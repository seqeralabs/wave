---
title: Build a container from Conda packages
---

The Wave CLI supports building a container from a list of [Conda] packages.

## Related CLI arguments

Conda builds support the following arguments:

- `--conda-base-image`: A base image for installing Conda packages. The default value is `mambaorg/micromamba:1.5.1`.
- `--conda-channels`: One or more comma separated channels. The default value is ` seqera,bioconda,conda-forge,defaults`.
- `--conda-file`: A [Conda lock file][conda-lock]. Can be a local file or a URL to a remote file.
- `--conda-package`: A Conda package to install. Can be specified multiple times. Expression are supported, such as `bioconda::samtools=1.17` or `samtools>=1.0,<1.17`.
- ` --conda-run-command`: A Docker `RUN` command used when the container is built. Can be specified multiple times.

## Example usage

In the following example, a container with the `samtools` and `bamtools` packages is built:

```
wave \
  --conda-package bamtools=2.5.2 \
  --conda-package samtools=1.17
```

[Conda]: https://anaconda.org/anaconda/repo
[conda-lock]: https://github.com/conda/conda-lock
