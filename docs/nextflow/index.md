---
title: Nextflow integration
description: Learn about integrating Wave with Nextflow
tags: [nextflow, wave, get started]
---

Wave's container provisioning service seamlessly integrates with Nextflow pipelines to streamline container management and deployment. When used with Nextflow, Wave automatically handles container building, provisioning, and optimization, eliminating the complexity of manual container management in your workflow execution.

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

## Get started

To enable Wave in your Nextflow pipeline, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
tower.accessToken = '<tower-access-token>'
```

Replace `<tower-access-token>` with your Seqera access token.

:::note

Using a Seqera access token is optional but provides additional capabilities:

- Access to private repositories
- Higher API request limits than anonymous users

:::

## Configuration

Wave provides extensive configuration options to customize container provisioning behavior, scanning behavior, caching strategies, and repository settings.

For detailed configuration options and advanced settings, see [Configuration reference](./configuration.md).
