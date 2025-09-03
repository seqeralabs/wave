---
title: Configure Wave build
---

This guide covers extending your existing Wave installation on Kubernetes to support container build capabilities. This enables Wave's full feature set including container building, freezing, and advanced caching.

## Prerequisites

Before extending Wave for build support, ensure you have:

- **Existing Wave installation** - Basic Wave deployment already running in augmentation-only mode
- **AWS EKS cluster** - Build capabilities require AWS-specific integrations
- **EFS filesystem** - Configured and accessible from your EKS cluster for shared build storage
- **Cluster admin permissions** - Required to create RBAC policies and storage resources

## Create Kubernetes Service Account & RBAC Policies

Wave's build service needs permissions to create and manage build pods. Create the necessary RBAC configuration:

```yaml
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: wave-sa
  namespace: wave
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

## Configure EFS Storage

Wave builds require shared storage accessible across multiple pods. Configure EFS with the AWS EFS CSI driver:

### Storage Class

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-wave-sc
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: "REPLACE_ME_EFS_ID"
  directoryPerms: "0755"
```

### Persistent Volume

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
    volumeHandle: "REPLACE_ME_EFS_ID"
```

### Persistent Volume Claim

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

**Configuration Notes:**
- Replace `REPLACE_ME_EFS_ID` with your actual EFS filesystem ID
- EFS must be in the same VPC as your EKS cluster
- Ensure EFS security groups allow NFS traffic from EKS worker nodes

## Update Wave Configuration

Update your existing Wave ConfigMap to enable build features and configure storage paths:

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
      # Enable build service
      build:
        enabled: true
        workspace: '/build/work'
        # Optional: Configure build timeouts
        timeout: '1h'
        # Optional: Configure resource limits for build pods
        resources:
          requests:
            memory: '1Gi'
            cpu: '500m'
          limits:
            memory: '4Gi'
            cpu: '2000m'

      # Enable other build-dependent features
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
        password: "your_secure_password_here"

      redis:
        uri: "redis://your-redis-host:6379"

      tower:
        endpoint:
          url: "https://your-platform-instance.com/api"

      # Kubernetes-specific configuration for builds
      k8s:
        namespace: wave
        serviceAccount: wave-sa
        # Optional: Configure build pod settings
        buildPod:
          image: 'quay.io/buildah/stable:latest'
          nodeSelector:
            wave-builds: "true"
```

## Update Wave Deployment

Modify your existing Wave deployment to include the service account and EFS storage:

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
      serviceAccountName: wave-sa  # Add service account
      containers:
        - image: your-registry.com/wave:latest
          name: wave-app
          ports:
            - containerPort: 9090
              name: http
          env:
            - name: MICRONAUT_ENVIRONMENTS
              value: "postgres,redis,k8s"  # Add k8s environment
            - name: WAVE_JVM_OPTS
              value: "-Xmx3g -Xms1g -XX:+UseG1GC"
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
            - name: build-storage  # Add EFS mount
              mountPath: /build
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
        - name: build-storage  # Add EFS volume
          persistentVolumeClaim:
            claimName: wave-build-pvc
      restartPolicy: Always
```

## Deploy the Updates

Apply the configuration changes to enable build support:

```bash
# Apply RBAC configuration
kubectl apply -f wave-rbac.yaml

# Apply storage configuration
kubectl apply -f wave-storage.yaml

# Update the ConfigMap
kubectl apply -f wave-configmap.yaml

# Update the deployment
kubectl apply -f wave-deployment.yaml

# Verify the deployment
kubectl get pods -n wave
kubectl logs -f deployment/wave -n wave

# Check that EFS is mounted correctly
kubectl exec -it deployment/wave -n wave -- df -h /build
```

## Verify Build Functionality

Test that Wave build capabilities are working:

1. **Check Wave health endpoint** for build service status
2. **Monitor logs** for build service initialization messages
3. **Test a simple build** through the Wave API or Platform integration

```bash
curl http://wave-service.wave.svc.cluster.local:9090/health

kubectl logs -f deployment/wave -n wave | grep -i build
```

## Recommended Production Enhancements

### Dedicated Node Pools

Create dedicated node pools for Wave build workloads to isolate build processes and optimize resource allocation:


### Build Pod Resource Management

Configure resource quotas and limits for build pods:

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: wave-build-quota
  namespace: wave
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    limits.cpu: "20"
    limits.memory: 40Gi
    pods: "10"
```

### Monitoring and Alerting

Set up monitoring for build operations:

- **Build success/failure rates**
- **Build duration metrics**
- **EFS storage usage**
- **Node resource utilization**
- **Build queue length**

## Security Considerations

- **EFS Access Points** - Use EFS access points to isolate build workspaces
- **Network Policies** - Restrict network access for build pods
- **Pod Security Standards** - Apply appropriate security contexts to build pods
- **Image Scanning** - Enable security scanning for built images
- **RBAC Minimization** - Regularly review and minimize Wave's cluster permissions

## Troubleshooting

**Common issues and solutions:**

- **EFS mount failures** - Check security groups and VPC configuration
- **Build pod creation failures** - Verify RBAC permissions and node selectors
- **Storage access issues** - Ensure EFS access points are configured correctly
- **Build timeouts** - Adjust build timeout settings based on workload requirements

For additional configuration options and advanced features, see [Configuring Wave](./configure-wave.md).

### Bottlerocket support

Bottlerocket sets `user.max_user_namespaces=0` by default for security. To use Buildkit with Bottlerocket, enable user namespaces for container builds by setting `user.max_user_namespaces=N` on your host nodes, where `N` is a positive integer, such as `63359`.

You can configure this setting in two ways:

#### Recommended: Node startup configuration

Configure the user namespace setting in your node group's startup script or user data. This approach applies the configuration at boot time and doesn't require privileged containers in your cluster.

#### Alternative: DaemonSet approach

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
