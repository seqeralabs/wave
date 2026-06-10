---
title: Nextflow and Wave
description: Learn about integrating Wave with Nextflow
tags: [nextflow, wave, get started]
---

Wave integrates with Nextflow to build and provision containers during pipeline execution. When a process runs, Wave resolves its container and returns an image URI to Nextflow. You don't pre-build or manage images by hand.

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

## Getting started

To enable Wave in a Nextflow pipeline, add the following to your Nextflow configuration file:

```groovy title="nextflow.config"
wave.enabled = true
tower.accessToken = '<tower-access-token>'
```

Replace `<tower-access-token>` with your Seqera access token. The token is optional. With a token you gain access to private repositories and higher API request limits than anonymous users.

## Next steps

- [Use cases](./use-cases.mdx): See common patterns for building, augmenting, and freezing containers from a Nextflow pipeline.
- [Configuration reference](./configuration.mdx): See the full list of `wave.*` options for tuning Wave behavior in Nextflow.
- [Nextflow and Wave tutorial](../tutorials/nextflow-wave.mdx): Follow a step-by-step walkthrough of running a pipeline with Wave.
