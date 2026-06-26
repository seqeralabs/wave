---
title: Identity and credentials
description: How Wave authenticates to cloud APIs and registries, and how registry credentials are resolved.
---

Wave needs two kinds of identity: one for the Wave process itself to call cloud APIs (S3, STS for ECR, SDK calls), and registry credentials to pull and push images. Choose each as described below. For the property syntax, see [Configure Wave](../configure-wave.md). For registry pre-creation rules, see [Registry prerequisites](registry-prerequisites.md).

## Wave's cloud identity

Wave runs on Amazon EKS and authenticates to AWS APIs (S3, STS, and ECR). Prefer IRSA over long-lived credentials:

| Option | How |
| --- | --- |
| IRSA (preferred) | Create an IAM role with the required S3, STS, and ECR policies, then annotate the Wave service account with `eks.amazonaws.com/role-arn`. |
| Instance profile | If IRSA is unavailable, attach an IAM instance profile to the EKS node group. |

The same identity applies to the build, scan, mirror, and blob-transfer pods: they can inherit Wave's service account or use a dedicated, more tightly scoped one.

For the step-by-step AWS IRSA setup, see [Enable Wave builds](aws-build.md).

## Registry credentials

Wave resolves registry credentials in a precedence order:

1. **Platform workspace credentials**: credentials a user configures in their Seqera Platform workspace. Used for user-owned targets, such as a user's own registry namespace in freeze mode.
2. **Server-side static configuration**: `wave.registries.<host>.username` and `.password` set by the operator. Used for operator-owned targets such as the build repository.
3. **Wave's cloud-native identity**: IRSA, as above. Used for cloud registries like ECR where the role grants push and pull directly.

Operator-owned targets (such as `wave.build.repo`) generally use server-side static config or cloud-native identity, because users do not configure workspace credentials for infrastructure they do not own.

## Anonymous access

Wave allows anonymous pulls by default (`wave.allowAnonymous: true`). Set `wave.allowAnonymous: false` in production so every request must carry a Platform-issued token. See [Configure for production](production.md).
