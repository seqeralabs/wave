---
title: Enable Wave builds
description: Add on-demand container builds, mirroring, and scanning to a Wave Lite deployment on Amazon EKS.
---

The full Wave configuration is a Wave Lite deployment on Amazon EKS with on-demand builds, freeze, mirroring, and security scanning enabled. Provision the AWS build infrastructure those features need, then turn them on by extending your existing Wave Lite deployment.

Build, mirror, and scan are independent toggles. Enable any subset, for example mirror without build. Scan and freeze depend on the build pipeline. With `build.enabled: false`, both are unavailable.

:::tip
The Kubernetes manifests in this guide assemble into a single file. Save each YAML block into `wave-build.yaml` in the order shown, separated by `---`, then apply the file once at the end. The AWS CLI steps (ECR, S3, and IRSA) run on their own and are not part of this file.
:::

## Infrastructure requirements

Builds run as Kubernetes jobs on EKS and need infrastructure beyond the base Wave Lite deployment.

In addition to the EKS cluster, managed database, and Redis that your Wave Lite deployment already uses, you need:

| Component | Purpose |
| --- | --- |
| Amazon Elastic File System (EFS) and its CSI driver | ReadWriteMany build workspace shared across build pods. |
| Amazon Elastic Container Registry (ECR) repositories | One for built images, one for the BuildKit layer cache. |
| Amazon S3 bucket | Build logs, build lock files, and scan reports. |
| IAM OpenID Connect (OIDC) provider and IAM Roles for Service Accounts (IRSA) role | Wave's AWS identity, used for S3 access and STS role assumption from the cluster. |
| Dedicated build node group | Isolates build workloads. Label its nodes `service=wave-build` (and `service=wave-build-arm64` for ARM) to match the node selector. |

:::info[**Prerequisites**]

You need the following:

- A Wave Lite deployment running on an Amazon EKS cluster (see [Install Wave Lite on Kubernetes](kubernetes-lite.md)).
- Permission to create EFS, ECR, S3, IAM, and node-group resources in the cluster's AWS account.
- The AWS CLI and `kubectl`, authenticated against the account.

:::

## Set the shell variables

The AWS CLI commands in this guide use the following variables. Set them for your environment before you run anything else:

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT=<aws-account-id>
export AWS_EKS_CLUSTER_NAME=<eks-cluster-name>
export WAVE_S3_BUCKET=<s3-bucket>
export WAVE_CONFIG_NAME=wave            # reused as the IAM policy and role name
```

## Create the build namespace

Build, scan, and mirror pods run in a dedicated namespace:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: "wave-build"
  labels:
    app: wave-app
```

## Create the ECR repositories

Create two repositories with a shared prefix, one for built images and one for the BuildKit layer cache:

```bash
aws --region "$AWS_REGION" ecr create-repository --repository-name wave/build
aws --region "$AWS_REGION" ecr create-repository --repository-name wave/cache
```

ECR does not auto-create repositories, so both must exist before Wave's first push. If you point builds or mirroring at a registry other than ECR, check its rules first: see [Registry pre-creation](reference.md#registry-pre-creation) for the per-registry matrix, and [Registry push and authentication failures](../troubleshoot.md#registry-push-and-authentication-failures) when a push fails partway through.

## Create the S3 bucket

Wave writes build logs, build lock files, and scan reports to S3:

```bash
aws --region "$AWS_REGION" s3 mb "s3://$WAVE_S3_BUCKET"
```

For naming rules and bucket options, see [Creating a bucket](https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html).

## Grant Wave access to AWS APIs with IRSA

IRSA gives the Wave pod its AWS identity. Wave uses it directly for S3 (build logs and Conda lock files), and to sign the STS calls it makes when an IAM role ARN is configured as an ECR registry credential. Wave authenticates ECR pushes and pulls with the `wave.registries` credentials you configure in [Enable build features](#enable-build-features), which can be either:

- An IAM access key pair. Attach the ECR statements of the policy in this section to that IAM user.
- The ARN of an IAM role whose trust policy allows the Wave role to call `sts:AssumeRole`. Attach the ECR statements to that role.

IRSA requires an IAM OIDC provider for the cluster, and the mechanics of associating a role with a service account are AWS's, not Wave's: see [Assign IAM roles to Kubernetes service accounts](https://docs.aws.amazon.com/eks/latest/userguide/associate-service-account-role.html). What follows is the Wave-specific part — which permissions to grant, and which service account to bind them to.

Author the two policy documents shown below, then create and attach the role. The `describe-cluster` call prints the OIDC issuer URL that `seqera-wave-role.json` needs:

```bash
aws --region "$AWS_REGION" eks describe-cluster \
  --name "$AWS_EKS_CLUSTER_NAME" \
  --query "cluster.identity.oidc.issuer" \
  --output text

aws --region "$AWS_REGION" iam create-policy \
  --policy-name "$WAVE_CONFIG_NAME" \
  --policy-document file://seqera-wave-policy.json

aws --region "$AWS_REGION" iam create-role \
  --role-name "$WAVE_CONFIG_NAME" \
  --assume-role-policy-document file://seqera-wave-role.json

aws --region "$AWS_REGION" iam attach-role-policy \
  --role-name "$WAVE_CONFIG_NAME" \
  --policy-arn "arn:aws:iam::$AWS_ACCOUNT:policy/$WAVE_CONFIG_NAME"
```

`seqera-wave-policy.json` grants access to ECR (for built and cached images) and S3 (for build logs, lock files, and scan reports). Replace `<aws-region>`, `<aws-account-id>`, and `<s3-bucket>` with the values you exported earlier:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "CorePermissionsForBuildAndCache",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:CompleteLayerUpload",
        "ecr:GetDownloadUrlForLayer",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": ["arn:aws:ecr:<aws-region>:<aws-account-id>:repository/wave/*"]
    },
    {
      "Sid": "ExtraPermissionsForBuild",
      "Effect": "Allow",
      "Action": [
        "ecr:DescribeImageScanFindings",
        "ecr:DescribeImages",
        "ecr:DescribeRepositories",
        "ecr:GetLifecyclePolicy",
        "ecr:GetLifecyclePolicyPreview",
        "ecr:GetRepositoryPolicy",
        "ecr:ListImages",
        "ecr:ListTagsForResource"
      ],
      "Resource": ["arn:aws:ecr:<aws-region>:<aws-account-id>:repository/wave/*"]
    },
    {
      "Sid": "BuildLogsLocksAndScanReports",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::<s3-bucket>",
        "arn:aws:s3:::<s3-bucket>/*"
      ]
    }
  ]
}
```

`seqera-wave-role.json` is the trust policy that lets the `wave-sa` service account assume the role through the cluster's OIDC provider. Replace `<oidc-provider>` with the issuer host from the `describe-cluster` output (the URL without `https://`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<aws-account-id>:oidc-provider/<oidc-provider>"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "<oidc-provider>:sub": "system:serviceaccount:wave:wave-sa",
          "<oidc-provider>:aud": "sts.amazonaws.com"
        }
      }
    }
  ]
}
```

Create the Wave service account, annotated with the role ARN so the Wave pod assumes the role. Replace `<wave-config-name>` with the `$WAVE_CONFIG_NAME` value you exported earlier:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: wave-sa
  namespace: wave
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<aws-account-id>:role/<wave-config-name>
```

Only the Wave service pod uses this role, through the `wave-sa` service account. Build, scan, and mirror pods run as `wave-build-sa`, which needs no AWS identity — Wave writes registry credentials into the shared build workspace for them. If IRSA is unavailable, attach an EC2 instance profile carrying the same policy to the node group that runs the Wave service pod.

Two rules matter here, on top of the [credential sources](kubernetes-lite.md#registry-credentials) Wave Lite already uses:

- Operator-owned targets — the registry hosts of `wave.build.repo`, `wave.build.cache`, and `wave.build.public-repo` — always use the server-side `wave.registries.<host>` credentials, never a user's workspace credentials.
- The cloud identity does not authenticate to registries by itself. For ECR, Wave exchanges the configured `wave.registries` credentials (an access key pair, or a role ARN it assumes via STS) for an ECR auth token.

## Configure EFS storage

Builds and scans share a ReadWriteMany workspace on EFS. Create an EFS file system in the same VPC as the cluster, install the [AWS EFS CSI driver](https://docs.aws.amazon.com/eks/latest/userguide/efs-csi.html), then define the storage resources.

### Storage class

Define a storage class backed by the EFS CSI driver:

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-wave-sc
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: "<efs-id>"
  directoryPerms: "0755"
```

### Persistent volumes and claims

The Wave service pod and the build pods each mount the workspace, and they run in different namespaces. Persistent volume claims are namespaced, so the claim must exist in **both** `wave` and `wave-build` — a claim only in `wave` leaves every build, scan, and mirror pod stuck in `Pending`. Create one volume and one claim per namespace, all pointing at the same EFS file system:

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: wave-build-pv
spec:
  capacity:
    storage: 500Gi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  storageClassName: efs-wave-sc
  claimRef:
    namespace: wave
    name: wave-build-pvc
  csi:
    driver: efs.csi.aws.com
    volumeHandle: "<efs-id>"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  namespace: wave
  name: wave-build-pvc
  labels:
    app: wave-app
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 500Gi
  storageClassName: efs-wave-sc
  volumeName: wave-build-pv
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: wave-build-pv-jobs
spec:
  capacity:
    storage: 500Gi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  storageClassName: efs-wave-sc
  claimRef:
    namespace: wave-build
    name: wave-build-pvc
  csi:
    driver: efs.csi.aws.com
    volumeHandle: "<efs-id>"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  namespace: wave-build
  name: wave-build-pvc
  labels:
    app: wave-app
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 500Gi
  storageClassName: efs-wave-sc
  volumeName: wave-build-pv-jobs
```

Configuration notes:

- Replace `<efs-id>` with your EFS file system ID in both volumes. Both claims keep the name `wave-build-pvc`, because `wave.build.k8s.storage.claim-name` is a single value used in both namespaces.
- The EFS security group must allow inbound and outbound NFS traffic (port `2049`) from the EKS worker nodes.

## Create the build RBAC

Wave's build service creates and manages build pods. The following manifest creates two resources:

- `wave-build-sa`: the service account the build, scan, and mirror pods run as. It is referenced by `wave.build.k8s.service-account` in [Enable build features](#enable-build-features) and needs no Kubernetes API permissions of its own.
- A ClusterRole and binding that grant `wave-sa` (the Wave service, in the `wave` namespace) permission to create and monitor build jobs:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: wave-build-sa
  namespace: wave-build
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: wave-role
rules:
  - apiGroups: [""]
    resources: [pods, pods/status, pods/log, pods/exec]
    verbs: [get, list, watch, create, delete]
  - apiGroups: ["batch"]
    resources: [jobs, jobs/status]
    verbs: [get, list, watch, create, delete]
  - apiGroups: [""]
    resources: [configmaps, secrets]
    verbs: [get, list]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: wave-rolebind
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: wave-role
subjects:
  - kind: ServiceAccount
    name: wave-sa
    namespace: wave
```

:::note
The ClusterRole grants these permissions cluster-wide. For least privilege, replace it with a namespaced `Role` and `RoleBinding` in the `wave-build` namespace, because Wave only manages jobs there.
:::

## Enable build features

Update the `wave-cfg` ConfigMap from your Wave Lite install to enable build, mirror, and scan and to configure the build subsystem. Keep the existing database, Redis, and Platform settings. Set the build repositories to the ECR repositories you created, point the workspace at the EFS mount, and add a `wave.registries` entry for the ECR host so Wave can authenticate pushes to the build and cache repositories. Without this entry, builds fail with a `Missing credentials for container repository` error:

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: wave-cfg
  namespace: wave
  labels:
    app: wave-cfg
data:
  config.yml: |
    wave:
      build:
        enabled: true
        repo: "<aws-account-id>.dkr.ecr.<aws-region>.amazonaws.com/wave/build"
        cache: "<aws-account-id>.dkr.ecr.<aws-region>.amazonaws.com/wave/cache"
        workspace: "/efs/wave/build"
        k8s:
          namespace: "wave-build"
          service-account: "wave-build-sa"
          storage:
            claim-name: "wave-build-pvc"
            mount-path: "/efs/wave/build"
          node-selector:
            # Keys are container platforms. Values are 'label=value' applied to your build node groups.
            # 'noarch' is required: mirror and blob cache pods are architecture-independent and
            # get no node selector at all without it.
            'linux/amd64': 'service=wave-build'
            'linux/arm64': 'service=wave-build-arm64'
            'noarch': 'service=wave-build'
        logs:
          path: "s3://<s3-bucket>/wave/build-logs"
        locks:
          path: "s3://<s3-bucket>/wave/build-locks"
      # Independent toggles. Enable only what you need.
      mirror:
        enabled: true
      scan:
        enabled: true
        # Required whenever scan is enabled: Wave fails to start without it.
        reports:
          path: "s3://<s3-bucket>/wave/scan-reports"
      blobCache:
        enabled: false   # Enabling blob cache needs S3. See Configure Wave.
      # ECR registry credentials. Wave exchanges these for ECR auth tokens using
      # its IRSA identity. See "Grant Wave access to AWS APIs with IRSA".
      registries:
        <aws-account-id>.dkr.ecr.<aws-region>.amazonaws.com:
          username: "<aws-access-key-id or iam-role-arn>"
          password: "<aws-secret-access-key or external-id>"
      # Database, Redis, and Platform settings (unchanged from the Wave Lite install).
      server:
        url: "https://wave.example.com"
      db:
        uri: "jdbc:postgresql://postgres.example.com:5432/wave"
        user: "wave_user"
        password: "<db-password>"
    redis:
      uri: "rediss://redis.example.com:6379"
    tower:
      endpoint:
        url: "https://platform.example.com/api"
    endpoints:
      health:
        enabled: true
        disk-space:
          enabled: false
        jdbc:
          enabled: false
```

Wave ships working defaults for the build tool images and the build timeout, so the ConfigMap does not set them. To override one, or to tune the build subsystem further, see [Container build process](reference.md#container-build-process).

To build ARM (Graviton) images, route `linux/arm64` builds to an ARM node group with the `node-selector` shown earlier. For cache setup (ECR cache repository, S3 cache authentication), see [Configure Wave](configure-wave.md).

:::note
If your build nodes run Bottlerocket, BuildKit needs user namespaces enabled before any build succeeds. See [Builds fail on Bottlerocket nodes](../troubleshoot.md#builds-fail-on-bottlerocket-nodes).
:::

## Update the Wave deployment

Update your Wave Lite deployment so it uses the IRSA service account, pulls the Wave image with the `seqera-reg-creds` secret created during the Wave Lite install, mounts the EFS workspace, and runs with the build Micronaut environments:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wave
  namespace: wave
  labels:
    app: wave-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: wave-app
  template:
    metadata:
      labels:
        app: wave-app
    spec:
      serviceAccountName: wave-sa
      imagePullSecrets:
        - name: seqera-reg-creds
      containers:
        - image: cr.seqera.io/<wave-image-path>:<tag>   # Use the image path and tag provided by Seqera.
          name: wave-app
          ports:
            - containerPort: 9090
              name: http
          env:
            - name: MICRONAUT_ENVIRONMENTS
              # lite is dropped from the Lite set; k8s enables the in-cluster build client.
              value: "postgres,redis,k8s,rate-limit"
            # The image defaults to an 850 MB heap regardless of the container limit.
            # Setting this variable replaces the whole default option set, so keep the
            # flags below alongside your own.
            - name: WAVE_JVM_OPTS
              value: >-
                -XX:+UseG1GC
                -Xms1g
                -Xmx3g
                -XX:MaxDirectMemorySize=100m
                -Dio.netty.maxDirectMemory=0
                -Dio.netty.allocator.type=pooled
          resources:
            requests:
              memory: "4Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          workingDir: "/work"
          volumeMounts:
            - name: wave-cfg
              mountPath: /work/config.yml
              subPath: "config.yml"
            - name: build-storage
              mountPath: /efs/wave/build
          readinessProbe:
            httpGet:
              path: /health
              port: 9090
            initialDelaySeconds: 30
            timeoutSeconds: 10
          livenessProbe:
            httpGet:
              path: /health
              port: 9090
            initialDelaySeconds: 60
            timeoutSeconds: 10
      volumes:
        - name: wave-cfg
          configMap:
            name: wave-cfg
        - name: build-storage
          persistentVolumeClaim:
            claimName: wave-build-pvc
      restartPolicy: Always
```

Apply the assembled manifest and confirm the build subsystem starts:

```bash
kubectl apply -f wave-build.yaml
kubectl logs -f deployment/wave -n wave | grep -i build
```

## Freeze and user-supplied build repositories

In freeze mode, a pipeline sets `wave.build.repository` (the Nextflow-side setting) to choose its own push target. Wave treats the value as custom only if it sits outside the operator's `wave.build.repo`, `wave.build.public-repo`, and `wave.build.cache` prefixes. If it starts with one of those prefixes, Wave rejects the freeze with a `must be specified when using freeze mode` error (with a numbered suffix such as `[1]`), even though the pipeline did supply a value.

To let users freeze to their own repositories, reserve a registry namespace outside your operator prefixes and distribute push credentials through Platform workspaces. See [Registry pre-creation](reference.md#registry-pre-creation) for whether the target registry needs the repository to exist first.

## Verify your installation

Run the build, mirror, and scan functional checks in [Verify your installation](post-install.md), then continue to the [production checklist](configure-wave.md#production-checklist) to prepare the deployment for production.
