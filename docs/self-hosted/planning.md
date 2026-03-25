# Information architecture for self-hosted Wave

- Self-hosted Wave
	- Prerequisites
	- Deployment
	- Configuration
	- Configuration reference

- FAQ
- Troubleshooting 

#### Self-hosted Wave

```
Index page to describe Self hosting, Wave lite, then what is on each page in this section
```

#### Prerequisites

```
# Prerequisites

[NEW] Explain this page describes infrastructure requirements for Wave deployment types (Docker Compose and/or Kubernetes).

## PostgreSQL

[MOVE from kubernetes.md + docker-compose.md]

- Kubernetes and Docker
- Supported versions
- Required SQL setup block (single canonical copy)

## Redis

[MOVE from kubernetes.md + docker-compose.md]

- Kubernetes and Docker
- Supported versions
	- Kubernetes deployments: Redis 6.0+
	- Docker Compose deployments: Redis 6.2+

Include an explanation of why

## Network connectivity

[MOVE + CONSOLIDATE from kubernetes.md and docker-compose.md]

- Outbound access requirements (registry endpoints, etc.)
- Consolidated list (currently, each file has a partial list)
  
## System requirements

[MOVE from kubernetes.md] Resource sizing table
```

#### Deployment

```
# Deployment

[NEW] Intro: this page covers deployment for both Docker Compose and Kubernetes.

[REF] "Before deploying, complete all steps in Prerequisites."

---

## Docker Compose

[NEW] Short intro for Docker Compose deployment path.

### Full manifest

[NEW] Complete, ready-to-use docker-compose.yml shown in full at the top, with all services (Wave, PostgreSQL, Redis) preconfigured.

Operators can copy this and adjust values in the sections below.

### Configure Wave

[MOVE from docker-compose.md] wave-config.yml setup

[KEEP] Cross-reference note to configuration.md

### Deploy

[MOVE from docker-compose.md] Run instructions (docker compose up, etc.)

### Verify

[NEW] Verification steps for Docker Compose deployment

---

## Kubernetes

[NEW] Short intro for Kubernetes deployment path.

States this page covers both Lite and Build modes; operators can follow

[REF] "For PostgreSQL, Redis, and network requirements, see Prerequisites."

### Wave Lite

[NEW] Complete set of Lite manifests shown in full at the top:

Namespace, ConfigMap, Deployment, Service, Ingress — all in one block.

Operators can copy and adjust values using the sections below.

#### Configure namespace

[KEEP] Namespace manifest explanation

#### Configure ConfigMap

[KEEP] Lite ConfigMap fields and values explained

#### Configure Deployment

[KEEP] Lite Deployment manifest fields explained — this is the canonical base; the Build section will show a patch against this, not a duplicate.

#### Configure Service

[KEEP] Service manifest explanation

#### Configure Ingress

[MOVE from kubernetes.md "Next Steps"] Ingress manifest explanation

#### ECR IAM permissions — Lite

[NEW] Short intro: Lite mode requires read-only ECR access.

If you are deploying Build mode, you will also need the Build policy below.

[UPDATE/SPLIT from kubernetes.md "Next Steps" combined policy]

Lite-only policy (read-only):

- ecr:GetAuthorizationToken
- ecr:BatchGetImage
- ecr:GetDownloadUrlForLayer

#### Verify Lite deployment

[KEEP/MOVE] Verification commands

### Wave with Build

[NEW] Introductory paragraph: explains the two-phase approach — deploy Lite first, then apply these additional resources to enable the build service.

#### Full manifest

[NEW] Complete set of Build-mode additions shown in full at the top: Build namespace, RBAC, EFS StorageClass, PVCs, updated ConfigMap, Deployment patch.

Operators can copy and adjust values using the sections below.

#### Configure Build namespace

[NEW] wave-build namespace explanation

#### Configure RBAC

[MOVE from configure-wave-build.md]

ServiceAccount, ClusterRole, ClusterRoleBinding explanation

#### Configure EFS storage

[MOVE from configure-wave-build.md] StorageClass explanation

[NEW] warning EFS provisioning requirements:

- Access point mode must be used (not legacy mode)
- directoryPerms must be set to "700" (or specify correct value)
- EFS mount target must be in the same VPC as the EKS nodes
- Security group on the EFS mount target must allow NFS (port 2049) inbound from the node security group

[MOVE from configure-wave-build.md] PersistentVolume + PersistentVolumeClaim for the `wave` namespace

[NEW] PersistentVolume + PersistentVolumeClaim for the `wave-build` namespace (functional deployment blocker — currently missing from docs entirely)

#### Configure S3 cache authentication (IRSA)

[MOVE from configuration.md] IRSA setup, service account YAML, IAM policy JSON, Kubernetes and Docker

#### Configure ConfigMap for Build

[MOVE from configure-wave-build.md] Changes to ConfigMap required to enable build mode explained

#### Configure Deployment patch

[NEW — replaces full duplicated manifest in configure-wave-build.md] Show only what changes from the Lite Deployment:

- serviceAccountName (add)
- MICRONAUT_ENVIRONMENTS (update value)
- EFS volumeMount (add)
- EFS volume (add)

Note that all other fields are unchanged from the Lite Deployment above.

#### ECR IAM permissions — Build

[UPDATE/SPLIT from kubernetes.md "Next Steps" combined policy]
[NEW] Short intro: in addition to the Lite policy above, Build mode requires write permissions for publishing built images to ECR.

Build-mode additional policy:

- ecr:InitiateLayerUpload
- ecr:UploadLayerPart
- ecr:CompleteLayerUpload
- ecr:PutImage
- ecr:DescribeRepositories
- ecr:CreateRepository
- ecr:ListImages
  
#### Verify Build deployment

[MOVE from configure-wave-build.md] Verification commands

### Production considerations

[CONSOLIDATE from configure-wave-build.md and kubernetes.md]

#### Pod Disruption Budgets

[KEEP/MOVE]

#### Horizontal Pod Autoscaler

[KEEP/MOVE]

#### Anti-affinity rules

[KEEP/MOVE]

#### Dedicated node pools for build workloads

[MOVE from configure-wave-build.md]

#### Resource quotas

[MOVE from configure-wave-build.md]

#### Monitoring and alerting

[KEEP/MOVE]

### Bottlerocket support

[MOVE from configure-wave-build.md] DaemonSet and setup steps

```

#### Configuration

```
# Configuration

[NEW] Short intro: explains this page covers optional operational configuration.

Use the table below to identify which sections apply to your deployment.


## Scope reference

[NEW] Table:

| Section | Applies to |
|----------------------|-----------------------------------------|
| Email — SMTP | All deployments |
| Email — SES | AWS deployments only |
| Security scanning | Build-enabled deployments only |
| ECR cache repository | AWS + Build-enabled deployments only |

---

## Email

New link to sections

### SMTP

[KEEP] SMTP configuration block and options table

### SES (AWS only)

[KEEP] SES configuration block

[NEW] :::note "This section applies to AWS deployments only."

[KEEP] SES IAM permissions JSON

[UPDATE] Fix broken relative path reference in IAM permissions link

[KEEP] SES setup requirements list

---

## Security scanning

[NEW] :::note Security scanning requires the Wave build service to be enabled.

See [Deployment — Wave with Build] for setup."

[KEEP] Security scanning prerequisites

[KEEP] Security scanning config block

---

## ECR cache repository (AWS + Build only)

[NEW] :::note admonition:

"This section applies to AWS deployments with the build service enabled."

[KEEP] ECR cache config block and options table
[KEEP] ECR lifecycle policy JSON
[UPDATE] Fix broken cross-reference link to ECR IAM section
```

#### Configuration reference

```
Improved descriptions and values and defaults checked
```
