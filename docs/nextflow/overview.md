---
title: Nextflow integration
---

You can use Wave directly from your Nextflow pipelines. Nextflow integration with Wave supports private repositories, container freezing, and conda packages.

:::note
Nextflow integration with Wave requires Nextflow 22.10.0, or later.
:::

## Get started

To enable Wave in your Nextflow pipeline, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

Using a Seqera access token is optional but provides additional capabilities:

- Access to private repositories
- Higher API request limits than anonymous users

For all Wave configuration options, see [Reference](./reference.md).
