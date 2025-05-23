---
title: Kubernetes Installation
---

On this page you will find instructions for running and installing Wave on Kubernetes using Kubernetes manifests for the setup. 

Wave allows provisioning container images on-demand, removing the need to build and upload them manually to a container registry.

Containers provisioned by Wave can be both disposable, i.e. ephemeral containers only accessible for a short period of time, and regular long-term registry-persisted container images.

Waves build capabilities such rely on a specific integrations with Kubernetes and AWS EFS Storage making EKS & AWS a hard dependancy for a fully featuered deployment. 

Wave also supports Augmentation only mode commonly referred to as "wave-lite" which does not require these cloud provider-specific integrations as such this installation guide will initially focus on the installation on generic Kubernetes in Augmentation only-mode which can be used across all Kubernetes distributions.

see ../configuring-wave-build.md for details on how to extend your installation. 
 
## Prerequesites

An up to date kubernetes cluster
postgres instance 
Redis instance

## Assumptions

You have already deployed Seqera Platform
You will be deploying the application into to the wave namespace
you have appropriate permissions to perform these actions. 


## Create namespace

```
---
apiVersion: v1
kind: Namespace
metadata:
  name: "wave"
  labels:
    app: wave-app
```

## Configure Wave

The following configuration example is a base please see the the wave configuration reference for all configuration options and examples. 

Change the following values to match your environment. 

database url 
username 
password

redis password

Seqera Platform API Endpoint commonly your platform instance followed by `/api` depending on your deployment you may have choosen to expose this on a subdomain such as `api.platforminstance.example`

```
kind: ConfigMap
apiVersion: v1
metadata:
  name: wave-cfg
  namespace: "wave"
  labels:
    app: wave-cfg
data:
  config.yml:
    wave:
      build:
        enabled: false
      mirror:
        enabled: false
      scan:
        enabled: false
      blobCache:
        enabled: false
      db:
        uri: "jdbc:postgresql://localhost:5432/wave"
        user: "postgres"
        password: "postgres"
    redis:
      uri: "rediss://REPLACE_ME_WAVE_REDIS_URL:6379"
    tower:
      endpoint:
        url: REPLACE_ME_TOWER_SERVER_URL
    micronaut:
      executors:
        stream-executor:
          type: FIXED
          number-of-threads: 16
      netty:
        event-loops:
          default:
            num-threads: 64
          stream-pool:
            executor: stream-executor
      http:
        services:
          stream-client:
            read-timeout: 30s
            read-idle-timeout: 5m
            event-loop-group: stream-pool
    loggers:
      env:
        enabled: false
      bean:
        enabled: false
      caches:
        enabled: false
      refresh:
        enabled: false
      loggers:
        enabled: false
      info:
        enabled: false
      metrics:
        enabled: true
      health:
        enabled: true
        disk-space:
          enabled: false
        jdbc:
          enabled: false
```

## Create deployment


```
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wave
  namespace: "wave"
  labels:
    app: wave-app
spec:
  selector:
    matchLabels:
      app: wave-app
  template:
    metadata:
      labels:
        app: wave-app
    spec:
      containers:
        - image: REPLACE_ME_AWS_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/nf-tower-enterprise/wave:REPLACE_ME_WAVE_IMAGE_TAG
          name: wave-app
          ports:
            - containerPort: 9090
          env:
            - name: MICRONAUT_ENVIRONMENTS
              value: "postgres,redis,lite"
            - name: WAVE_JVM_OPTS
          resources:
            requests:
              memory: "4000Mi"
            limits:
              memory: "4000Mi"
          workingDir: "/work"
          volumeMounts:
            - name: wave-cfg
              mountPath: /work/config.yml
              subPath: "config.yml"
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
      restartPolicy: Always
```


## Next Steps


### Configuring Wave Platform to integrate with Wave

You need to set the config value on your platform deployment to talk to the wave server. 

### Networking.

The Wave service needs to be addressable and reachable from compute instances

Configure a DNS entry 

Configure connectivity as per your requirements using technologies such as 

- AWS Loadbalancer Controller
- Ingress NGINX
- 

### TLS 

Wave does not handle TLS connectivity - this should be setup and configured on your loadbalancer / ingress etc. 


### Kubernetes Improvements

You may wish to investigate additional items to improve the robustness of the deployment. 

- Disruption budgets
- Horrizontal pod auto-scaler
- Node Selectors
- Annotation & Labels. 

### Configuring Wave

See ../configuring-wave.md for configuring specific wave functionality and features 





