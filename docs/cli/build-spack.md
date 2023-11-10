---
title: Build a container from Spack packages
---

The Wave CLI supports building a container from a list of [Spack] packages.

:::warning
Support for Spack packages is currently experimental.
:::

## Related CLI arguments

Spack builds support following arguments:

- `--spack-file`: A Spack YAML file that specifies packages and their dependencies.
- `--spack-package`: A Spack package to install. Can be specified multiple times. Version expression are supported, such as `curl@7.42.1`.
- `--spack-run-command`: A Docker `RUN` command used when the container is built. Can be specified multiple times.

## Example usage

In the following example, a container with the `curl` package is built:

```
wave --spack-package curl@7.42.1
```

[Spack]: https://spack.io/
