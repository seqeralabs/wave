---
title: Configure Wave build
---

This guide covers extending your existing Wave installation on Kubernetes to support container build capabilities. This enables Wave's full feature set, including container building, freezing, security scanning, mirroring, and advanced caching.

Wave builds use a **two-namespace architecture**: the main Wave application runs in one namespace (e.g., `wave`) and spawns ephemeral build, scan, and mirror pods in a dedicated build namespace (e.g., `wave-build`). Both namespaces share a single EFS filesystem so that build artifacts are accessible to both the app and the build pods.

```
┌──────────────────────────┐     ┌───────────────────────────────┐
│  Namespace: wave         │     │  Namespace: wave-build        │
│                          │     │                               │
│  Wave app (Deployment)   │────►│  Build pods (ephemeral)       │
│  Schedules build pods    │     │  Scan pods (ephemeral)        │
│  via K8s API             │     │  Mirror pods (ephemeral)      │
│                          │     │                               │
│  PVC: wave-app-fs  ──┐   │     │  PVC: wave-build-fs  ──┐      │
└──────────────────────┼───┘     └────────────────────────┼──────┘
                       │                                  │
                       └──────────┬───────────────────────┘
                                  ▼
                       ┌─────────────────────┐
                       │  AWS EFS filesystem  │
                       │  (shared storage)    │
                       └─────────────────────┘
```

## Prerequisites

Before extending Wave for build support, ensure you have:

- **Existing Wave installation** — Basic Wave deployment already running in augmentation-only mode (see [Kubernetes installation](./kubernetes.md))
- **AWS EKS cluster** — Build capabilities require AWS-specific integrations (EFS, IRSA, ECR)
- **[AWS EFS CSI Driver](https://docs.aws.amazon.com/eks/latest/userguide/efs-csi.html)** — Installed on the cluster. Verify with: `kubectl get pods -n kube-system | grep efs`
- **AWS EFS filesystem** — Provisioned in the same VPC as your EKS cluster (see [Create the EFS filesystem](#create-the-efs-filesystem))
- **IAM role for IRSA** — With permissions for ECR, S3, and SES (see [Configure IAM role](#configure-iam-role))
- **Cluster admin permissions** — Required to create namespaces, RBAC policies, and storage resources
- **CNI with NetworkPolicy support** (optional) — Such as Calico or Cilium, required if you want to enforce [build pod network policies](../configure-wave.md#build-pod-network-policies)

## Architecture overview

### Why two namespaces?

Build pods execute user-supplied Dockerfiles, which means they run arbitrary code. Separating them into a dedicated namespace enables:

- **Network isolation** — NetworkPolicies block build pods from accessing internal infrastructure (databases, Redis, K8s API, EC2 metadata)
- **Resource isolation** — Resource quotas on the build namespace prevent builds from starving the app
- **RBAC scoping** — Build pods get minimal permissions via their own ServiceAccount
- **Audit clarity** — Easy to distinguish app activity from build activity

### Why shared EFS?

The Wave app writes build context (Dockerfiles, configuration) to a workspace directory. Build pods read from that same directory, execute the build, and write artifacts back. Both namespaces must mount the **same** EFS filesystem at the **same** path for this to work.

:::warning
Do not use EFS dynamic provisioning (`provisioningMode: efs-ap`) for Wave storage. Dynamic provisioning creates a separate EFS Access Point per PVC, giving each namespace an isolated root directory. This breaks the shared storage requirement — app pods and build pods would not be able to see each other's files.

Instead, create the EFS filesystem externally (Terraform, AWS Console, or CLI) and use static PV binding with the raw filesystem handle.
:::

## Create the EFS filesystem

Create the EFS filesystem outside of Kubernetes using Terraform, the AWS Console, or the AWS CLI. The EFS CSI driver mounts an existing filesystem — it does not create one.

**Requirements:**

- **VPC** — Must be in the same VPC as your EKS cluster
- **Mount targets** — Create one in each availability zone where EKS worker nodes run
- **Security groups** — Allow inbound NFS traffic (TCP port 2049) from EKS worker node security groups
- **Encryption** — Enable encryption at rest (recommended for production)
- **Performance mode** — `generalPurpose` is sufficient for most workloads

After creation, note the filesystem ID (e.g., `fs-0123456789abcdef0`). You will use this as the `volumeHandle` in the PersistentVolume manifests.

## Create the build namespace

The build namespace is where Wave creates ephemeral pods for container builds, security scans, and mirror operations. Keeping these workloads in a separate namespace from the Wave app allows you to apply independent resource quotas, RBAC policies, and network policies — so that user-triggered builds can't interfere with Wave itself or access internal infrastructure.

```yaml
---
apiVersion: v1
kind: Namespace
metadata:
    name: wave-build
    labels:
        app: wave-app
```

## Configure IAM role

Both the Wave app and the build pods need AWS permissions. Use [IRSA (IAM Roles for Service Accounts)](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html) to map Kubernetes ServiceAccounts to an IAM role.

Create an IAM role with the following permissions:

- **ECR** — Push/pull container images (`ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, etc.)
- **S3** — Read/write build logs, layer cache, conda locks, and scan reports to your Wave S3 bucket
- **SES** — Send email notifications (if email features are enabled)

See the [base installation guide](./kubernetes.md#aws-credentials-to-access-ecr) for the minimum ECR IAM policy.

## Create Kubernetes service accounts

Wave needs AWS permissions in both namespaces — the app namespace (to push built images to ECR, write logs to S3, and call the K8s API to schedule build pods) and the build namespace (so build pods can pull base images from ECR and write layer cache to S3). Each namespace gets its own ServiceAccount, and both use [IRSA](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html) to assume an AWS IAM role without static credentials.

### App namespace ServiceAccount

The app ServiceAccount is used by the Wave Deployment. The IRSA annotation grants it the IAM role's AWS permissions at runtime. The accompanying Secret is a legacy-style ServiceAccount token that Wave uses to authenticate with the Kubernetes API when creating and managing build pods in the `wave-build` namespace.

```yaml
---
apiVersion: v1
kind: ServiceAccount
metadata:
    namespace: wave
    name: wave-sa
    annotations:
        eks.amazonaws.com/role-arn: "arn:aws:iam::ACCOUNT_ID:role/YOUR_WAVE_ROLE"
---
# Legacy token secret — Wave uses this to authenticate K8s API calls
# when scheduling build pods across namespaces
apiVersion: v1
kind: Secret
metadata:
    namespace: wave
    name: wave-sa-secret
    annotations:
        kubernetes.io/service-account.name: wave-sa
type: kubernetes.io/service-account-token
```

### Build namespace ServiceAccount

Build pods need their own ServiceAccount in the `wave-build` namespace. This is what Wave references in the `wave.build.k8s.serviceAccount` config setting. The IRSA annotation gives build pods the same AWS permissions (ECR pull/push, S3 access) without embedding credentials in the pod spec.

```yaml
---
apiVersion: v1
kind: ServiceAccount
metadata:
    namespace: wave-build
    name: wave-build-sa
    annotations:
        eks.amazonaws.com/role-arn: "arn:aws:iam::ACCOUNT_ID:role/YOUR_WAVE_ROLE"
```

Replace `arn:aws:iam::ACCOUNT_ID:role/YOUR_WAVE_ROLE` with your actual IAM role ARN. Both ServiceAccounts can use the same IAM role, or you can create separate roles with different permission scopes if you want tighter control over what build pods can access.

## Create RBAC policies

The Wave app dynamically creates and manages pods in the `wave-build` namespace at runtime — it schedules build pods, monitors their status, reads their logs, and cleans them up when builds complete or fail. These operations require Kubernetes API permissions that don't exist by default. The ClusterRole below grants the minimum set of permissions Wave needs, and the ClusterRoleBinding assigns them to the `wave-sa` ServiceAccount in the app namespace.

```yaml
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

## Configure EFS storage

Wave uses static PV provisioning — both PersistentVolumes reference the same EFS filesystem ID so that app pods and build pods share a single filesystem.

### StorageClass

The StorageClass serves as a binding label that links PersistentVolumes to PersistentVolumeClaims. When a PVC requests `storageClassName: efs-wave-sc`, Kubernetes matches it to a PV with the same class. Because Wave uses static provisioning (pre-created PVs pointing to an existing EFS filesystem), the StorageClass itself does not dynamically create any volumes — it only provides the label for binding.

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
    name: efs-wave-sc
provisioner: efs.csi.aws.com
```

### PersistentVolumes

PersistentVolumes are cluster-scoped resources that represent actual storage. You need two PVs because each will bind to a PVC in a different namespace — but both must point to the **same** EFS filesystem. This is the mechanism that gives pods in both `wave` and `wave-build` access to the same underlying files (build artifacts, Dockerfiles, diagnostic dumps).

Replace `REPLACE_ME_EFS_ID` with your EFS filesystem ID (e.g., `fs-0123456789abcdef0`). Both PVs must use the same value.

```yaml
---
# PV for the app namespace
apiVersion: v1
kind: PersistentVolume
metadata:
    name: wave-app-pv
spec:
    capacity:
        storage: 50Gi
    volumeMode: Filesystem
    accessModes:
        - ReadWriteMany
    persistentVolumeReclaimPolicy: Retain
    storageClassName: efs-wave-sc
    csi:
        driver: efs.csi.aws.com
        volumeHandle: "REPLACE_ME_EFS_ID"
---
# PV for the build namespace
apiVersion: v1
kind: PersistentVolume
metadata:
    name: wave-build-pv
spec:
    capacity:
        storage: 50Gi
    volumeMode: Filesystem
    accessModes:
        - ReadWriteMany
    persistentVolumeReclaimPolicy: Retain
    storageClassName: efs-wave-sc
    csi:
        driver: efs.csi.aws.com
        volumeHandle: "REPLACE_ME_EFS_ID"
```

**Key points:**

- `ReadWriteMany` — Required because multiple pods read/write concurrently
- `Retain` — Data persists if a PV is released (prevents accidental deletion)
- `storage: 50Gi` — A label for quota purposes. EFS is elastic and grows automatically
- Both PVs use the **same** `volumeHandle` — This is what makes shared storage work

### PersistentVolumeClaims

PVCs are namespace-scoped requests for storage. Each namespace needs its own PVC because pods can only mount PVCs from their own namespace. The PVCs bind to their corresponding PVs via `storageClassName` matching — Kubernetes pairs each PVC with an available PV that has the same storage class, access mode, and sufficient capacity.

Since both PVs reference the same EFS filesystem handle, the end result is that pods in both namespaces access the same underlying files despite using separate PVC objects.

```yaml
---
# PVC in the app namespace
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
    namespace: wave
    name: wave-app-fs
    labels:
        app: wave-app
spec:
    accessModes:
        - ReadWriteMany
    resources:
        requests:
            storage: 50Gi
    storageClassName: efs-wave-sc
---
# PVC in the build namespace
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
    namespace: wave-build
    name: wave-build-fs
    labels:
        app: wave-app
spec:
    accessModes:
        - ReadWriteMany
    resources:
        requests:
            storage: 50Gi
    storageClassName: efs-wave-sc
```

After applying, verify both PVCs are bound:

```bash
kubectl get pvc -n wave
kubectl get pvc -n wave-build
# Both should show STATUS: Bound
```

### Mount path summary

Both the app pods and the build pods mount EFS at the same path:

| Namespace    | PVC             | Mounted at  | Used for                                  |
| ------------ | --------------- | ----------- | ----------------------------------------- |
| `wave`       | `wave-app-fs`   | `/efs/wave` | Build workspace, thread dumps, heap dumps |
| `wave-build` | `wave-build-fs` | `/efs/wave` | Build execution, reading build context    |

Directory structure created at runtime:

```
/efs/wave/
├── build/              # Build workspace — Dockerfiles, context, artifacts
│   └── <build-id>/    # One directory per build
└── dump/               # Diagnostic outputs
    ├── threads-dump.txt           # Thread dump from trade monitor
    └── java-<pod-name>.hprof     # Heap dump on OutOfMemoryError
```

## Build pod network isolation

Build pods execute user-supplied Dockerfiles, which means they run arbitrary code. For production deployments, you should restrict build pod network access to prevent them from reaching internal infrastructure.

See [Build pod network policies](../configure-wave.md#build-pod-network-policies) in the advanced configuration guide for a recommended NetworkPolicy and DNS isolation configuration.

## Update Wave configuration

With the infrastructure in place (namespaces, storage, service accounts, RBAC), you now need to tell Wave about it. The ConfigMap below enables the build service and configures Wave to schedule build pods in the `wave-build` namespace using the storage and service accounts you created above.

This replaces the `wave.build.enabled: false` setting from the [base installation](./kubernetes.md). The key settings that connect to the infrastructure resources above are annotated with inline comments:

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
        # Build workspace directory — must be under the EFS mount path
        workspace: "/efs/wave/build"
        # Only clean up build artifacts on successful builds
        cleanup: "OnSuccess"
        # Build timeouts
        timeout: "15m"
        trusted-timeout: "25m"
        # Kubernetes configuration for build pods
        k8s:
          # Build pods run in the dedicated build namespace
          namespace: "wave-build"
          # EFS storage configuration for build pods
          storage:
            # Must match the mount path used by the app pods
            mountPath: "/efs/wave"
            # Must match the PVC name in the wave-build namespace
            claimName: "wave-build-fs"
          # ServiceAccount for build pods (in wave-build namespace)
          serviceAccount: "wave-build-sa"
          # Build pod resource allocation
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          # Node selectors for multi-architecture builds
          nodeSelector:
            linux/amd64: 'service=wave-build'
            linux/arm64: 'service=wave-build-arm64'
          # Custom DNS to bypass cluster DNS (security isolation)
          dns:
            servers:
              - "1.1.1.1"
              - "8.8.8.8"
            policy: "None"

      # Enable build-dependent features
      mirror:
        enabled: true
      scan:
        enabled: true
      blobCache:
        enabled: true

      # Existing database, redis, and platform configuration...
      db:
        uri: "jdbc:postgresql://your-postgres-host:5432/wave"
        user: "wave_user"
      redis:
        uri: "redis://your-redis-host:6379"
      tower:
        endpoint:
          url: "https://your-platform-instance.com/api"
```

:::warning
Store the database password in a Kubernetes Secret and inject it via an environment variable, not in the ConfigMap. See the [base installation guide](./kubernetes.md#configure-wave) for an example.
:::

## Update Wave deployment

The base Wave installation runs without a service account or shared storage. To support builds, you need to update the Deployment to:

- Attach the `wave-sa` ServiceAccount, so the pod gets AWS permissions via IRSA and can call the Kubernetes API to create build pods
- Mount the EFS volume at `/efs/wave`, so the app can write build context that build pods will read
- Add environment variables for the database password (from a Secret), JVM heap dump configuration (writing to EFS so dumps survive pod restarts), and the `k8s` Micronaut profile (which enables the Kubernetes build scheduler)

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
      containers:
        - image: REPLACE_ME_REGISTRY/wave:REPLACE_ME_TAG
          name: wave-app
          ports:
            - containerPort: 9090
              name: http
          env:
            - name: WAVE_DB_PASSWORD
              valueFrom:
                secretKeyRef:
                    name: wave-secret
                    key: POSTGRES_DB_PASSWORD
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: MICRONAUT_ENVIRONMENTS
              value: "postgres,redis,k8s"
            - name: WAVE_JVM_OPTS
              value: "-Xmx3g -Xms1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/efs/wave/dump/java-$(POD_NAME).hprof"
          resources:
            requests:
              memory: "4Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
          workingDir: "/work"
          volumeMounts:
            # ConfigMap mounted as a single file at /work/config.yml
            - name: wave-cfg
              mountPath: /work/config.yml
              subPath: "config.yml"
            # Shared EFS filesystem
            - name: vol-efs
            mountPath: /efs/wave
          readinessProbe:
            httpGet:
              path: /health
              port: 9090
            initialDelaySeconds: 5
            timeoutSeconds: 3
          livenessProbe:
            httpGet:
              path: /health
              port: 9090
            initialDelaySeconds: 5
            timeoutSeconds: 3
            failureThreshold: 10
      volumes:
        - name: wave-cfg
          configMap:
            name: wave-cfg
      - name: vol-efs
          persistentVolumeClaim:
            claimName: wave-app-fs
      restartPolicy: Always
```

**Changes from the base installation:**

| Change                        | What                            | Why                                                            |
| ----------------------------- | ------------------------------- | -------------------------------------------------------------- |
| `serviceAccountName: wave-sa` | Added                           | IRSA for AWS permissions + K8s API access to create build pods |
| `MICRONAUT_ENVIRONMENTS`      | Changed to `postgres,redis,k8s` | `k8s` profile enables Kubernetes build scheduling              |
| `vol-efs` volume mount        | Added at `/efs/wave`            | Shared EFS for build workspace, dumps                          |
| `WAVE_JVM_OPTS`               | Added                           | Heap dump on OOM written to EFS for persistence                |
| `POD_NAME` env var            | Added                           | Used in heap dump filename for pod identification              |
| `WAVE_DB_PASSWORD` env var    | Added                           | Inject DB password from Secret instead of ConfigMap            |

## Deployment order

Apply resources in dependency order:

```bash
# 1. Build namespace
kubectl apply -f wave-build-namespace.yaml

# 2. StorageClass (cluster-scoped)
kubectl apply -f wave-storage-class.yaml

# 3. PersistentVolumes (cluster-scoped, must exist before PVCs)
kubectl apply -f wave-pvs.yaml

# 4. PVCs (one per namespace)
kubectl apply -f wave-pvcs.yaml

# 5. ServiceAccounts (both namespaces)
kubectl apply -f wave-serviceaccounts.yaml

# 6. RBAC (cluster-scoped)
kubectl apply -f wave-rbac.yaml

# 7. ConfigMap (app namespace)
kubectl apply -f wave-configmap.yaml

# 8. Deployment (app namespace)
kubectl apply -f wave-deployment.yaml

# 9. (Optional) NetworkPolicy for build pod isolation
# See: Configure Wave > Build pod network policies
kubectl apply -f wave-networkpolicy.yaml
```

## Verify the deployment

```bash
# Verify PVCs are bound
kubectl get pvc -n wave
kubectl get pvc -n wave-build
# Both should show STATUS: Bound

# Verify app pods are running
kubectl get pods -n wave

# Verify EFS is mounted
kubectl exec -n wave deployment/wave -- df -h /efs/wave

# Check health endpoint
kubectl exec -n wave deployment/wave -- curl -s localhost:9090/health

# Check logs for build service initialization
kubectl logs -n wave deployment/wave | grep -i build

# Trigger a test build and verify build pods appear
kubectl get pods -n wave-build --watch
```

## Recommended production enhancements

### Dedicated node pools

Create dedicated node pools for Wave build workloads to isolate build processes from other cluster workloads. Label build nodes to match the `nodeSelector` values in your Wave configuration:

```bash
# Example: label nodes for AMD64 builds
kubectl label node <node-name> service=wave-build

# Example: label nodes for ARM64 builds
kubectl label node <node-name> service=wave-build-arm64
```

### Build pod resource management

Without resource quotas, a burst of concurrent builds could consume all available cluster resources and starve other workloads. Apply a ResourceQuota to the `wave-build` namespace to cap the total CPU, memory, and pod count that build pods can collectively use. Adjust these values based on your expected build concurrency and node pool capacity:

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: wave-build-quota
  namespace: wave-build
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    limits.cpu: "20"
    limits.memory: 40Gi
    pods: "10"
```

### Monitoring and alerting

Set up monitoring for build operations:

- **Build success/failure rates**
- **Build duration metrics**
- **EFS storage usage**
- **Node resource utilization**
- **Build queue length**

## Troubleshooting

### PVC stuck in Pending

```bash
kubectl describe pvc wave-build-fs -n wave-build
```

Common causes:

- **EFS CSI driver not installed** — Verify with `kubectl get pods -n kube-system | grep efs`
- **StorageClass name mismatch** — The `storageClassName` must match between PV and PVC
- **Incorrect EFS filesystem ID** — The `volumeHandle` in the PV must match your EFS filesystem ID
- **No EFS mount targets** — Mount targets must exist in each AZ where worker nodes run

### Build pods can't access EFS

```bash
kubectl describe pod <build-pod> -n wave-build
```

Common causes:

- **Security group misconfiguration** — EFS security group must allow inbound NFS (port 2049) from worker node security groups
- **PVC not bound** — Check `kubectl get pvc -n wave-build`
- **claimName mismatch** — The `wave.build.k8s.storage.claimName` in the ConfigMap must match the PVC name in the build namespace

### Build pods can't reach the internet

Common causes:

- **NetworkPolicy too restrictive** — If build pods need to reach an internal container registry, add an egress rule for that specific IP
- **Custom DNS servers unreachable** — Verify `1.1.1.1` and `8.8.8.8` are reachable from your VPC (check NACLs and route tables)
- **NAT Gateway missing** — Build nodes in private subnets need a NAT Gateway for internet access

### App pods crash with OOM

Heap dumps are written to `/efs/wave/dump/java-<pod-name>.hprof` on EFS, so they persist after the pod restarts:

```bash
kubectl exec -n wave <pod-name> -- ls -lh /efs/wave/dump/
# Or copy locally:
kubectl cp wave/<pod-name>:/efs/wave/dump/java-<pod-name>.hprof ./heapdump.hprof
```

### Build pods not created

```bash
kubectl logs -n wave deployment/wave | grep -i "build\|error\|fail"
```

Common causes:

- **RBAC misconfigured** — The `wave-sa` ServiceAccount needs ClusterRole permissions to create pods in the `wave-build` namespace
- **ServiceAccount missing** — `wave-build-sa` must exist in the `wave-build` namespace
- **Node selector mismatch** — No nodes match the labels in `wave.build.k8s.nodeSelector`

## Bottlerocket support

Buildkit requires user namespaces. However, Bottlerocket sets `user.max_user_namespaces=0` by default for security.

To use Buildkit with Bottlerocket, enable user namespaces for container builds by setting `user.max_user_namespaces=N` on your host nodes, where `N` is a positive integer. Use a sufficiently high value (e.g., `62000`) to avoid build failures. Values that are too low (e.g., `10`) will limit concurrent build capacity and may cause build failures.

You can configure this setting in two ways:

#### Recommended: Node startup configuration

Configure the user namespace setting in your node group's startup script or user data. This approach applies the configuration at boot time and doesn't require privileged containers in your cluster.

#### Alternative: DaemonSet

If you can't control the node configuration directly, use a DaemonSet. This approach requires running a privileged container. We recommend you deploy it only on wave-build nodes and use a dedicated namespace for isolation.

**Example manifest:**

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

#### Additional resources

For more information about Bottlerocket, see:

- [Bottlerocket FAQs](https://bottlerocket.dev/en/faq/)
- [Amazon Bottlerocket FAQs](https://aws.amazon.com/bottlerocket/faqs/)
