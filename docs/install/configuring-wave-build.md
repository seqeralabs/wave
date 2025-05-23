---
title: Kubernetes Installation
---

Extending your wave installation on kubernetes.

# Prerequisites.

-   Wave installation
-   EFS volume


## Create kubernetes service account & Rbac policies

```yaml
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
    name: wave-role
rules:
    - apiGroups: ["", batch]
      resources: [pods, pods/status, pods/log, pods/exec, jobs, jobs/status]
      verbs: [get, list, watch, create, delete]
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
      namespace: wave-prod
```



## Add Persistent volume

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-wave-sc # Referenced by both PV and PVC
provisioner: efs.csi.aws.com # AWS EFS CSI driver provisioner
parameters:
  provisioningMode: efs-ap # Uses EFS Access Points for provisioning
```

```yaml
# Persistent Volume Definition
# Pre-provisioned storage volume that points to an existing EFS filesystem
apiVersion: v1
kind: PersistentVolume
metadata:
  name: wave-app-pv
spec:
  capacity:
    storage: 500Gi # Matches the PVC storage request
  volumeMode: Filesystem # Standard filesystem volume mode
  accessModes:
    - ReadWriteMany # Allows multiple pods to read/write simultaneously
  persistentVolumeReclaimPolicy: Retain # Keeps the volume after PVC deletion
  storageClassName: efs-wave-sc # Links to the StorageClass above
  csi:
    driver: efs.csi.aws.com # AWS EFS CSI driver
    # Must point to an existing EFS filesystem in AWS
    # This value must be updated before deploying the configuration.
    volumeHandle: "REPLACE_ME_EFS_ID" # AWS FS ID Can also be found via the AWS CLI
```

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  namespace: "wave" # Namespace where the storage will be used
  name: wave-app-fs # Name of the storage claim
  labels:
    app: wave-app # Associates this PVC with the Wave application
spec:
  accessModes:
    - ReadWriteMany # Must match PV access mode
  resources:
    requests:
      storage: 500Gi # Must match or be less than PV capacity
  storageClassName: efs-wave-sc # Links to the same StorageClass as PV
```


## Update Configmap



## Update Deployment

### Add Service account to deployment

```yaml

```

### Add Persistent volume to deployment

```yaml

```

### Enable Build Features

In your wave config.yaml set build.enabled to true

```yaml
  config.yml:
    wave:
      build:
        enabled: true
```

## Recommended Steps

Create Dedicated node pools for wave-builds
Create architecture specific node pools for ARM or AMD support.
