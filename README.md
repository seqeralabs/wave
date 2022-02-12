# tower-reg 

Proof-of-concept for an ephemeral container registry that injects 
a custom payloads during an arbitrary image pull.

The main goal is showing the possibility to append a layer into any Linux
container including a FUSE driver to access a S3 bucket.

### How it works 

This project act like a proxy server in between the Docker client, putting an 
arbitrary container image and the target registry i.e. docker.io hosting the 
image to be downloaded. 

When an imaged is pulled the proxy server forward the request to the target registry, 
fetch the image manifest, and appended to the layers configuration a new layer 
which provides the FUSE client required access the AWS S3 storage.

It also change the entry point of the container setting the script [entry.sh](.layer/opt/fusion/entry.sh)
which takes care to automatically mount the bucket names specified via the env variable 
`$NXF_FUSION_BUCKETS` into the container. For example if the variable value is `s3://foo` it mounts 
the bucket to the path `/fusion/s3/foo`. Multiple buckets can be specified separating them 
with a comma `,` character.

### Get started 

1. Clone this repo and change into the project root


2. Assemble a container layer using this command:

   
        make clean all


3. Compile and run the registry service:  

        bash run.sh

4. Use reverse proxy service to expose the registry with public name, e.g. 

        ngrok http 9090 -subdomain reg


5. Pull a container using the docker client: 

        docker pull reg.ngrok.io/library/busybox
        
    **NOTE**: replace the registry name `reg.ngrok.io` with the one provided by the reverse proxy command in the previous step.

6. The pulled images contains the files from the appended layer. Check it with the following command:

        docker run --rm reg.ngrok.io/library/busybox cat foo.txt

7. List the content of a bucket using `ls`

        docker run --rm \
          -e AWS_REGION=eu-west-1 \
          -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
          -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
          -e NXF_FUSION_BUCKETS=s3://nextflow-ci \
          --privileged  \
          reg.ngrok.io/library/busybox \
          ls -la /fusion/s3/nextflow-ci
  

### Next goals 

* Implements the core functionality using Java + Micronaut + native compilation (Graal)
* Add support to use an arbitrary target container registry other than docker.io 
* Add support for private registry authentication, at least quay.io, AWS ECR, Google Artifact Registry, Azure registry
* Implement layer caching mechanism to store layers binary into a S3 bucket 
* Add support for Tower users [token propagation](https://micronaut-projects.github.io/micronaut-guides-poc/latest/micronaut-token-propagation-gradle-java.html) 
