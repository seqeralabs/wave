# Wave registry 

Ephemeral container registry that injects custom payloads during an arbitrary image pull.

The main goal is to have a custom registry server to append a layer into any Linux
container including a FUSE driver to access a S3 bucket.

### How it works 

This project acts like a proxy server in between the Docker client, putting an 
arbitrary container image and the target registry i.e. docker.io hosting the 
image to be downloaded. 

When an image is pulled, the proxy server forwards the request to the target registry, 
fetches the image manifest, and appends a new layer to the layer configuration.
This provides the FUSE client required access to the AWS S3 storage.

It also changes the entry point of the container setting the script [entry.sh](.layer/opt/fusion/entry.sh)
which takes care to automatically mount FusionFS in the container when the env variable 
`$NXF_FUSION_BUCKETS` is defined. It mounts 
the path `/fusion/s3`, and then any S3 bucket is available as `/fusion/s3/<bucket_name>`.

### Get started 

1. Clone this repo and change into the project root.


2. Assemble a container layer using this command:

   
        make clean all

3. Prepare a new layer (this creates a new `pack` directory with the layer to inject)

         make pack

4. To create a `dev` configuration, copy `config.yml` into `src/main/resources/application-dev.yml`
and set the user/pwd for at least 1 registry.

5. Compile and run the registry service:  

        bash run.sh

6. Use the reverse proxy service to expose the registry with public name, e.g. 

        ngrok http 9090 -subdomain reg

    **NOTE**: To use the service without ngrok, you can access the local server using `docker pull localhost:9090/library/busybox`.

7. Pull a container using the docker client: 

        docker pull reg.ngrok.io/library/busybox
        
    **NOTE**: replace the registry name `reg.ngrok.io` with the name used in step 6.

8. The pulled image contains the files from the appended layer. Check image contents using this command:

        docker run \
          --rm \
          --platform linux/amd64 \
          reg.ngrok.io/library/busybox \
          cat foo.txt

8a. List the content of a bucket using `ls`:

        docker run \
          --rm \
          --platform linux/amd64 \
          -e AWS_REGION=eu-west-1 \
          -e AWS_ACCESS_KEY_ID \
          -e AWS_SECRET_ACCESS_KEY \
          -e NXF_FUSION_BUCKETS=nextflow-ci \
          --cap-add SYS_ADMIN \
          --device /dev/fuse  \
          reg.ngrok.io/library/busybox \
          ls -la /fusion/s3/nextflow-ci
  
### K8s one-liners 

```
 kubectl run busybox \
   --image reg.staging-tower.xyz/library/busybox \
   --image-pull-policy Always \
   --restart Never \
   --attach=true \
   cat foo.txt
```

```
kubectl run busybox \
  --env "AWS_REGION=eu-west-1" \
  --env "AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID" \
  --env "AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY" \
  --env "NXF_FUSION_BUCKETS=nextflow-ci" \
  --privileged \
  --image reg.staging-tower.xyz/library/busybox \
  --restart Never \
  --attach=true \
  -- \
  ls -la /fusion/s3/nextflow-ci 
```
### Credentials validation 


```
curl \
  -H "Content-Type: application/json" \
  -X POST http://localhost:9090/validate-creds \
  -d '{"userName":"<USERNAME>", "password":"PASSWORD", "registry": "docker.io"}' 
```

### Container token
  
Acquire a container token:  
```
curl \
  -H "Content-Type: application/json" \
  -X POST http://localhost:9090/container-token \
  -d '{"containerImage":"quay.io/nextflow/bash:latest", "towerAccessToken":"eyJ0aWQiOiAxfS40ZGE4ZDBmMTQ3YzliMWJkOGVkMDNlYjY1ZWRiZmU1OWQxZjEyZGU3", "towerWorkspaceId": null}' 
```

Example payload: 

```
cat <<EOT > container-request.json
{
  "containerImage":"quay.io/nextflow/bash:latest",
  "containerConfig": {
    "layers": [
      {
        "location": "https://s3.eu-west-1.amazonaws.com/nf-tower.dev/wave-layer.tar.gzip",
        "gzipDigest": "sha256:e9d6f29eecde29c12f0d32a1bc81aa9f4fcb76f8d813b8f1a37e8af5ee6b7657",
        "gzipSize": 13219265,
        "tarDigest": "sha256:2648cf125a9a4c4c5c9a2d3799b3a57983b5297442d7995746273c615bc4e316"
      }
    ]
  }
}
EOT
```

Example request: 

```
curl \
  -H "Content-Type: application/json" \
  -X POST https://reg.staging-tower.xyz/container-token \
  -d @container-request.json 
```

Example response:

```
{
  "containerToken": "bbc389039bf0",
  "targetImage": "reg.staging-tower.xyz/wt/bbc389039bf0/nextflow/bash:latest"
}
```


Example container run using the resulting token: 

```
docker run \
  --rm \
  --platform linux/amd64 \
  reg.staging-tower.xyz/wt/<TOKEN>/nextflow/bash:latest \
  cat foo.txt 
```


== EC2 profile

To run the application using the AWS ParameterStore configuration, you need to activate the `ec2` profile.

In `dev`, run this command:

`./gradlew run -Penvs=dev,h2,mail,ec2`

In `prod`, profiles are activated via MICRONAUT_ENVIRONMENTS (i.e. `MICRONAUT_ENVIRONMENTS=mysql,mail,ec2`)


== Surrealdb

Use statistics are stored in a SurrealDB database (if the Micronaut environment `surrealdb` is provided)

```
DATA="INFO FOR DB;"

curl --request POST \
    --header "Accept: application/json" \
    --header "NS:seqera" \
    --header "DB:wave" \
    --user "root:root" --data "${DATA}" \
    http://surrealdb:8000/sql 
```

`[{"time":"46.215µs","status":"OK","result":{"dl":{},"dt":{},"sc":{},"tb":{}}}]`

After a build we can request statistics

```
curl --request GET \
    --header "Accept: application/json" \
    --header "NS:seqera" \
    --header "DB:wave" \
    --user "root:root" \
    http://surrealdb:8000/key/build_wave 
```

```
[
   {
      "time":"86.91µs",
      "status":"OK",
      "result":[
         {
            "dockerFile":"FROM docker.io/ubuntu\n\tRUN echo \"Look ma' building on the fly at Wed Oct 26 12:43:52 CEST 2022\" > /hello.txt\n   ",
            "duration":"10.356435285",
            "exitStatus":0,
            "id":"build_wave:jkihg4lqpy4b42s3hpr8",
            ....
```

== Debugging 

* To debug http requests made proxy client add the following Jvm setting `'-Djdk.httpclient.HttpClient.log=requests,headers'` 


=== Test local K8s build  

To test the image build with a local K8s cluster use the following setting: 

```
wave:
  debug: true
  build:
    workspace: "/<YOUR_HOME>/wave/build-workspace"
    k8s:
      configPath: "${HOME}/.kube/config"
      context: 'docker-desktop'
      namespace: 'wave-local'
      storage:
        mountPath: "/<YOUR_HOME>/wave/build-workspace"

```
     
Replace in the above setting the `context` and `namespace` with the ones in the local K8s cluster.


== Skaffold, develop in kubernetes

1. install k3d and skaffold
2. create a registry `k3d registry create registry.localhost --port 5000`
4. create a cluster `k3d cluster create  -p "8081:80@loadbalancer" --registry-use k3d-registry.localhost:5000 --registry-config k8s/dev/registries.yml`
5. configure the cluster `kubectl label node k3d-k3s-default-server-0 service=wave-build`
6. apply all required infra

```
kubectl apply -f k8s/dev/accounts-k3d.yml
kubectl apply -f k8s/dev/volumes-k3d.yml
kubectl apply -f k8s/dev/redis-k3d.yml
kubectl apply -f k8s/dev/surrealdb-k3d.yml
```
7. configure skaffold to trust in our local registry `skaffold config set --global insecure-registries localhost:5000`
8. create a config `cp k8s/dev/config-k3d-example.yml k8s/dev/config-k3d.yml` and set your credentials
9. run skaffold `skaffold dev --default-repo localhost:5000`
10. or debug skaffold `skaffold debug --default-repo localhost:5000`
