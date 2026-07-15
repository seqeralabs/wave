---
title: Verify your installation
description: Confirm a self-hosted Wave service is live and functional after installation.
---

After installing Wave with any method, confirm the service is live and provisions containers. Run the liveness check first, then the functional checks for your configuration.

## Check the service is live

Wave exposes `/service-info`. A healthy service returns its version and commit ID:

```bash
curl -s https://wave.example.com/service-info
```

```json
{
  "serviceInfo": {
    "version": "<wave-version>",
    "commitId": "<commit-id>"
  }
}
```

If this returns a connection error, check that the service is running and that your ingress or load balancer routes to it. If it returns the wrong version, confirm the deployed image tag.

## Install the Wave CLI

The functional checks use the Wave CLI.

```bash
# Homebrew
brew install seqeralabs/tap/wave-cli

# Or download a binary from the releases page
# https://github.com/seqeralabs/wave-cli/releases
```

Point the CLI at your service with the `--wave-endpoint` flag or the `WAVE_ENDPOINT` environment variable:

```bash
export WAVE_ENDPOINT=https://wave.example.com
```

:::note
If you disabled anonymous access in [Configure Wave](configure-wave.md#require-authentication), the CLI checks need a Seqera Platform access token. Pass it with the `--tower-token` flag or the `TOWER_ACCESS_TOKEN` environment variable.
:::

## Functional checks

### Wave Lite

Augment an existing image:

```bash
wave -i ubuntu:22.04
```

A successful augmentation prints a Wave image reference pointing at your service host.

### Wave

In addition to the augmentation check, verify the build features you enabled.

Build a container from a Conda package:

```bash
wave --conda-package bcftools
```

Build from a Dockerfile and freeze it to a persistent repository:

```bash
wave --containerfile Dockerfile --freeze --build-repo <build-repo> --tower-token <platform-token>
```

A successful build returns a reference in your configured build repository.

:::note
Freeze builds push to the repository you name. They always need a Platform access token, and the associated Platform workspace must hold registry credentials with push access to `<build-repo>`. See [Freeze and user-supplied build repositories](aws-build.md#freeze-and-user-supplied-build-repositories).
:::

## If a check fails

If a check does not pass, match the symptom to one of these common causes:

- **Augmentation or build fails with an authentication error**: Wave cannot authenticate to the target registry. See [Registry push and authentication failures](../troubleshoot.md#registry-push-and-authentication-failures).
- **Build fails partway through a push**: The target repository may not exist or may lack push scope. See [per-registry pre-creation rules](aws-build.md#create-the-ecr-repositories).
- **Freeze is rejected even with a build repository set**: The repository sits inside an operator-reserved prefix. See [Freeze and user-supplied build repositories](aws-build.md#freeze-and-user-supplied-build-repositories).

## Next step

When the checks pass, continue to [Configure Wave](configure-wave.md#harden-for-production) to harden the deployment for production.
