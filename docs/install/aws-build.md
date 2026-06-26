---
title: Enable Wave builds
description: Add on-demand container builds, mirroring, and scanning to a Wave Lite deployment on Amazon EKS.
---

The full Wave configuration is a Wave Lite deployment on Amazon EKS with on-demand builds, freeze, mirroring, and security scanning enabled. This guide provisions the AWS build infrastructure those features need, then turns them on by extending your existing Wave Lite deployment.

Build, mirror, and scan are independent toggles. You can enable any subset, for example mirror without build. Scan and freeze depend on the build pipeline. With `build.enabled: false`, both are unavailable.

## Infrastructure requirements

Builds run as Kubernetes jobs on EKS and need infrastructure beyond the base Wave Lite deployment.

In addition to the EKS cluster, managed database, and Redis that your Wave Lite deployment already uses, you need:

| Component | Purpose |
| --- | --- |
| Amazon Elastic File System (EFS) and its CSI driver | ReadWriteMany build workspace shared across build pods. |
| Amazon Elastic Container Registry (ECR) repositories | One for built images, one for the BuildKit layer cache. |
| IAM OpenID Connect (OIDC) provider and IAM Roles for Service Accounts (IRSA) role | Wave's access to ECR and S3 from the cluster. |
| Dedicated build node group | Isolates build workloads. Label its nodes `service=wave-build` (and `service=wave-build-arm64` for ARM) to match the node selector. |

:::info[**Prerequisites**]

You need the following:

- A Wave Lite deployment running on an Amazon EKS cluster (see [Install Wave Lite on Kubernetes](kubernetes-lite.md)).
- Permission to create EFS, ECR, IAM, and node-group resources in the cluster's AWS account.
- The AWS CLI and `kubectl`, authenticated against the account.

:::

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

ECR requires repositories to exist before push. For other registries, see [Registry prerequisites](registry-prerequisites.md).

## Grant Wave access to AWS APIs with IRSA

Wave authenticates to AWS APIs (ECR and S3) through IRSA. This requires an IAM OIDC provider for the cluster. To check whether one exists or create it, see [Creating an IAM OIDC provider](https://docs.aws.amazon.com/eks/latest/userguide/enable-iam-roles-for-service-accounts.html).

The commands in this section use the following variables. Set them for your environment first:

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT=<aws-account-id>
export AWS_EKS_CLUSTER_NAME=<eks-cluster-name>
export WAVE_CONFIG_NAME=wave            # reused as the IAM policy and role name
```

Find the cluster's OIDC issuer URL:

```bash
aws --region "$AWS_REGION" eks describe-cluster \
  --name "$AWS_EKS_CLUSTER_NAME" \
  --query "cluster.identity.oidc.issuer" \
  --output text
```

Create an IAM policy and role, then attach them. The two documents referenced below (`seqera-wave-policy.json` and `seqera-wave-role.json`) are templates you author for your account, shown after the commands:

```bash
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

`seqera-wave-policy.json` grants access to ECR (for built and cached images) and S3 (for the build logs and locks buckets). Scope each resource to your repository and bucket ARNs:

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
      "Sid": "BuildLogsAndLocks",
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

Create the Wave service account, annotated with the role ARN so the Wave pod assumes the role:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: wave-sa
  namespace: wave
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<aws-account-id>:role/<wave-config-name>
```

For the choice between IRSA and other identity models, and the registry-credential precedence rules, see [Identity and credentials](identity-and-credentials.md).

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

### Persistent volume

Create a persistent volume bound to your EFS file system:

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
  csi:
    driver: efs.csi.aws.com
    volumeHandle: "<efs-id>"
```

### Persistent volume claim

Claim the volume for the build workspace:

```yaml
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
```

Configuration notes:

- Replace `<efs-id>` with your EFS filesystem ID.
- EFS must be in the same VPC as your EKS cluster.
- Ensure the EFS security group allows inbound and outbound NFS traffic (port `2049`) from the EKS worker nodes.

## Create the build RBAC

Wave's build service creates and manages build pods. Grant its service account the role-based access control (RBAC) permissions it needs:

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

## Enable build features

Update the `wave-cfg` ConfigMap from your Wave Lite install to enable build, mirror, and scan and to configure the build subsystem. Keep the existing database, Redis, and Platform settings. Set the build repositories to the ECR repositories you created, and point the workspace at the EFS mount:

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
            claimName: "wave-build-pvc"
            mountPath: "/efs/wave/build"
          node-selector:
            # Keys are container platforms. Values are 'label=value' applied to your build node groups.
            'linux/amd64': 'service=wave-build'
            'linux/arm64': 'service=wave-build-arm64'
        logs:
          path: "s3://<s3-bucket>/wave/build-logs"
        locks:
          path: "s3://<s3-bucket>/wave/build-locks"
      # Independent toggles. Enable only what you need.
      mirror:
        enabled: true
      scan:
        enabled: true
      blobCache:
        enabled: false   # Enabling blob cache needs S3. See Configure Wave.
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
```

Wave ships working defaults for the build tool images and timeout. You do not need to set them. The current defaults are:

- `wave.build.buildkit-image`: `public.cr.seqera.io/wave/buildkit:v0.25.2-rootless`
- `wave.build.singularity-image`: `public.cr.seqera.io/wave/singularity:v4.2.1-r4`
- `wave.blobCache.s5cmdImage`: `public.cr.seqera.io/wave/s5cmd:v2.3.0`
- `wave.build.timeout`: `900s` (15 minutes)

To build ARM (Graviton) images, route `linux/arm64` builds to an ARM node group with the `node-selector` shown above. For deeper build and blob cache tuning, see [Configure Wave](../configure-wave.md).

## Update the Wave deployment

Update your Wave Lite deployment so it uses the IRSA service account, pulls the Wave image with the `seqera-reg-creds` secret, mounts the EFS workspace, and runs with the build Micronaut environments:

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
        - image: cr.seqera.io/<wave-image-path>:<tag>
          name: wave-app
          ports:
            - containerPort: 9090
              name: http
          env:
            - name: MICRONAUT_ENVIRONMENTS
              value: "postgres,redis,k8s"   # Switched from the Lite set (lite,postgres,redis). k8s enables the in-cluster build client.
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

Apply the changes and confirm the build subsystem starts:

```bash
kubectl apply -f wave-rbac.yaml
kubectl apply -f wave-configmap.yaml
kubectl apply -f wave-deployment.yaml
kubectl logs -f deployment/wave -n wave | grep -i build
```

## Freeze and user-supplied build repositories

In freeze mode, a pipeline sets `wave.build.repository` (the Nextflow-side setting) to choose its own push target. Wave's validator, [`ValidationServiceImpl.isCustomRepo()`](https://github.com/seqeralabs/wave/blob/master/src/main/groovy/io/seqera/wave/service/validation/ValidationServiceImpl.groovy#L109), treats the value as custom only if it sits outside the operator's `wave.build.repo`, `wave.build.public-repo`, and `wave.build.cache` prefixes. If it starts with one of those prefixes, Wave rejects the freeze with a `must be specified when using freeze mode` error (with a numbered suffix such as `[1]`), even though the pipeline did supply a value.

To allow user freeze, reserve a registry namespace **outside** your operator prefixes and distribute push credentials through Platform workspaces. See [Registry prerequisites](registry-prerequisites.md) and [Identity and credentials](identity-and-credentials.md).

## Bottlerocket support

BuildKit requires user namespaces, but Bottlerocket sets `user.max_user_namespaces=0` by default. Enable user namespaces on your build nodes by setting `user.max_user_namespaces=N` to a sufficiently high value (for example, `62000`). Values that are too low limit concurrent build capacity and can cause build failures.

Set this at boot through your node group's startup script or user data (preferred, with no privileged containers required). If you cannot control node configuration directly, apply it with a DaemonSet that runs a privileged container on build nodes only:

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  labels:
    app: sysctl-userns
  name: sysctl-userns
spec:
  selector:
    matchLabels:
      app: sysctl-userns
  template:
    metadata:
      labels:
        app: sysctl-userns
    spec:
      containers:
        - name: sysctl-userns
          image: busybox
          command: ["sh", "-euxc", "sysctl -w user.max_user_namespaces=63359 && sleep infinity"]
          securityContext:
            privileged: true
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
              - matchExpressions:
                  - key: service
                    operator: In
                    values: ["wave-build", "wave-build-arm64"]
```

For more on Bottlerocket, see the [Bottlerocket FAQs](https://bottlerocket.dev/en/faq/).

## Production enhancements

For production build deployments, consider:

- **Dedicated build node pools** to isolate build workloads.
- **A ResourceQuota** on the build namespace, paired with `wave.job-manager.max-running-jobs`, to cap concurrent build resource usage.
- **NetworkPolicies** restricting build-pod egress to registries and S3 only. Build pods run user-submitted Dockerfiles.
- **EFS access points** to isolate build workspaces.
- **Monitoring** of build success/failure rates, duration, EFS usage, and queue length.

## Verify your installation

Run the build smoke tests in [Verify your installation](post-install.md), then continue to [Configure for production](production.md).
