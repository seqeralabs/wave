---
title: Configure Wave build
description: Enable Wave build on Kubernetes and AWS EFS storage
tags: [kubernetes, install, wave, wave build]
---

Wave's full build capabilities require specific integrations with Kubernetes and AWS Elastic File System (EFS) Storage. Amazon Elastic Kubernetes Service (EKS) and AWS hard dependencies for full-featured deployments that support container build capabilities.

This page describes how to extend your [Kubernetes installation](./kubernetes) to support container build capabilities. It includes:

- Configure Kubernetes service account and RBAC policies
- Configure EFS storage
- Update Wave configuration
- Update Wave deployments
- Deploy updates
- Verify build functionality
- Configure production enhancements
- Optimize for production environments
- Configure advanced options

:::info[**Prerequisites**]
You will need the following to get started:

- An existing [Wave Lite Kubernetes installation](./kubernetes.md)
- An AWS EKS cluster
- An EFS filesystem configured and accessible from your EKS cluster
- Cluster admin permissions to create RBAC policies and storage resources
:::

## Configure Kubernetes service account and RBAC policies

To configure your Kubernetes service account and RBAC policies:

1. Create `wave-rbac.yaml` with the following configuration:

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

1. Apply the RBAC configuration:

    ```bash
    kubectl apply -f wave-rbac.yaml
    ```

## Configure EFS storage

Wave builds require shared storage accessible across multiple pods. Configure EFS with the AWS EFS CSI driver to provide persistent, shared storage for build artifacts and caching.

:::note
EFS must be in the same Virtual Private Cloud (VPC) as your EKS cluster. Ensure EFS security groups allow NFS traffic from EKS worker nodes.
:::

### Storage class

To configure your EFS storage class:

1. Create `wave-storage.yaml` with the following configuration:

    ```yaml
    ---
    apiVersion: storage.k8s.io/v1
    kind: StorageClass
    metadata:
      name: efs-wave-sc
    provisioner: efs.csi.aws.com
    parameters:
      provisioningMode: efs-ap
      fileSystemId: "<EFS_ID>"
      directoryPerms: "0755"
    ```

    Replace `<EFS_ID>` with your Amazon EFS File System ID.

1. Apply the storage configuration:

    ```bash
    kubectl apply -f wave-storage.yaml
    ```

### Persistent volume

To configure your persistent volume:

1. Create `wave-persistent.yaml` with the following configuration:

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
        volumeHandle: "<EFS_ID>"
    ```

    Replace `<EFS_ID>` with your Amazon EFS File System ID.

1. Apply the persistent volume:

    ```bash
    kubectl apply -f wave-persistent.yaml
    ```

### Persistent volume claim

To configure your persistent volume claim:

1. Create `wave-persistent-claim.yaml` with the following configuration:

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

1. Apply the persistent volume claim:

    ```bash
    kubectl apply -f wave-persistent-claim.yaml
    ```

## Update Wave configuration

Update your existing Wave ConfigMap to enable build features and configure storage paths:

1. Open `wave-config.yaml` and update the configuration with the following:

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
            workspace: '/build/workspace'
            # Optional: Retain failed builds to gather logs & inspect
            cleanup: "OnSuccess"
            # Optional: Configure build timeouts
            timeout: '15m'
            # Example additional kubernetes configuration for wave-build
            k8s:
              dns:
                servers:
                  - "1.1.1.1"
                  - "8.8.8.8"
              namespace: "wave-build"
              storage:
                mountPath: "/build"
                # Relevant volume claim name should match the
                claimName: "wave-build-pvc"
              serviceAccount: "wave-build-sa"
              resources:
                requests:
                  memory: '1800Mi'
              nodeSelector:
                # This node selector binds the build pods to a separate cluster node group
                linux/amd64: 'service=wave-build'
                linux/arm64: 'service=wave-build-arm64'
          # Enable other build-dependent features
          mirror:
            enabled: true
          scan:
            enabled: true
          blobCache:
            enabled: true

          # Existing database, redis, and platform configuration...
          db:
            uri: "jdbc:postgresql://<POSTGRES_HOST>:5432/wave"
            user: "wave_user"
            password: "<SECURE_PASSWORD>"

          redis:
            uri: "redis://<REDIS_HOST>:6379"

          tower:
            endpoint:
              url: "<PLATFORM_SERVER>"
    ```

    Replace the following:

    - `<POSTGRES_HOST>`: your Postgres service endpoint
    - `<SECURE_PASSWORD>`: your secure password for the database user
    - `<REDIS_HOST>`: your Redis service endpoint
    - `<PLATFORM_SERVER>`: your Platform endpoint URL (_optional_)

1. Apply the Wave configuration:

    ```bash
    kubectl apply -f wave-config.yaml
    ```

## Update Wave deployments

To update your Wave deployment, follow these steps:

1. Modify your existing `wave-deployment.yaml` to include the service account and EFS storage:

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
            - image: <REGISTRY_ADDRESS>/wave:<WAVE_IMAGE_TAG>
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
                - name: build-storage
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

    Replace the following:

    - `<REGISTRY_ADDRESS>`: your registry image address
    - `<WAVE_IMAGE_TAG>`: your Wave image tag version

1. Apply the updated deployment:

    ```
    kubectl apply -f wave-deployment.yaml
    ```

## Verify the deployment

To verify your deployment, follow these steps:

1. Check the Wave pods and logs::

    ```bash
    kubectl get pods -n wave
    kubectl logs -f deployment/wave -n wave
    ```

1. Check that EFS is mounted correctly:

    ```bash
    kubectl exec -it deployment/wave -n wave -- df -h /build
    ```

## Verify build functionality

Test that Wave build capabilities are working:

1. Check Wave health endpoint for build service status
1. Monitor logs for build service initialization messages
1. **Test a simple build** through the Wave API or Platform integration

    ```bash
    curl http://wave-service.wave.svc.cluster.local:9090/health
    kubectl logs -f deployment/wave -n wave | grep -i build
    ```

## Configure production enhancements

The following sections describe recommended enhancements for production deployments:

### Dedicated node pools

Create dedicated node pools for Wave build workloads to isolate build processes and optimize resource allocation.

### Build pod resource management

Control resource usage and prevent build pods from overwhelming your cluster:

- Configure resource quotas and limits for build pods.

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

### Monitoring and alerting

Set up monitoring for build operations to track performance and identify issues:

- Build success/failure rates
- Build duration metrics
- EFS storage usage
- Node resource utilization
- Build queue length

## Security considerations

Implement these security measures to protect your Wave deployment and build environment:

- Use EFS access points to isolate build workspaces
- Restrict network access for build pods
- Apply appropriate security contexts to build pods
- Enable security scanning for built images
- Regularly review and minimize Wave's cluster permissions
