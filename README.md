# tower-reg 

Ephemeral container registry that injects 
a custom payloads during an arbitrary image pull.

The main goal is to have a custom registry server how append a layer into any Linux
container including a FUSE driver to access a S3 bucket.

### How it works 

This project act like a proxy server in between the Docker client, putting an 
arbitrary container image and the target registry i.e. docker.io hosting the 
image to be downloaded. 

When an imaged is pulled the proxy server forward the request to the target registry, 
fetch the image manifest, and appended to the layer configuration a new layer 
which provides the FUSE client required access the AWS S3 storage.

It also changes the entry point of the container setting the script [entry.sh](.layer/opt/fusion/entry.sh)
which takes care to automatically mount FusionFS when the env variable 
`$NXF_FUSION_BUCKETS` is defined into the container. It mounts 
the path `/fusion/s3`, and then any S3 bucket it is available as `/fusion/s3/<bucket_name>`.

### Get started 

1. Clone this repo and change into the project root


2. Assemble a container layer using this command:

   
        make clean all

3. Prepare a new layer (will create a new `pack` directory with the layer to inject)

         make pack

4. Create a `dev` configuration: copy `config.yml` into `src/main/resources/application-dev.yml`
and set the user/pwd for at least 1 registry

5. Compile and run the registry service:  

        bash run.sh

6. Use reverse proxy service to expose the registry with public name, e.g. 

        ngrok http 9090 -subdomain reg


7. Pull a container using the docker client: 

        docker pull reg.ngrok.io/library/busybox
        
    **NOTE**: replace the registry name `reg.ngrok.io` with the one provided by the reverse proxy command in the previous step.

8. The pulled images contains the files from the appended layer. Check it with the following command:

        docker run \
          --rm \
          --platform linux/amd64 \
          reg.ngrok.io/library/busybox \
          cat foo.txt

1. List the content of a bucket using `ls`

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
