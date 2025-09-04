---
title: Reference
tags: [nextflow, reference, wave]
---

| Method                       | Description                                                                                                                                                              |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `wave.enabled`               | Enable/disable the execution of Wave containers.                                                                                                                         |
| `wave.endpoint`              | The Wave service endpoint (default: `https://wave.seqera.io`).                                                                                                           |
| `wave.build.repository`      | The container repository where images built by Wave are uploaded. You must provide corresponding credentials in your Platform account.                                   |
| `wave.build.cacheRepository` | The container repository used to cache image layers built by the Wave service. You must provide corresponding credentials in your Platform account.                      |
| `wave.conda.mambaImage`      | The Mamba container image used to build conda-based containers. This should be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) image.              |
| `wave.conda.commands`        | One or more commands to add to the Dockerfile used to build a conda-based image.                                                                                         |
| `wave.mirror`                | Enable Wave container mirroring (default: false).                                                                                                                        |
| `wave.strategy`              | The strategy used when resolving ambiguous Wave container requirements (default: `'container,dockerfile,conda'`).                                                        |
| `wave.freeze`                | When `freeze` mode is enabled, containers provisioned by Wave are stored permanently in the repository specified by `wave.build.repository`.                             |
