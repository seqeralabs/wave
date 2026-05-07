---
title: Nextflow and Wave
description: Learn about integrating Wave with Nextflow
tags: [nextflow, wave, get started]
---

Wave's container provisioning service seamlessly integrates with Nextflow pipelines to streamline container management and deployment. When a process runs, Wave automatically handles container building, provisioning, and optimization, eliminating the complexity of manual container management in your workflow execution.

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
