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

4. Create a `dev` configuration: copy `application-prod-example.yml` into `src/main/resources/application-dev.yml`
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
          -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
          -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
          -e NXF_FUSION_BUCKETS=s3://nextflow-ci \
          --cap-add SYS_ADMIN \
          --device /dev/fuse  \
          reg.ngrok.io/library/busybox \
          ls -la /fusion/s3/nextflow-ci
  

### Next goals 

* ~~Implements the core functionality using Java + Micronaut + native compilation (Graal)~~
* ~~Add support to use an arbitrary target container registry other than docker.io~~ 
* ~~Add support for private registry authentication, at least quay.io, AWS ECR, Google Artifact Registry, Azure registry~~
* ~~Implement layer caching mechanism to store layers binary into a S3 bucket~~ 
* Add support for Tower users [token propagation](https://micronaut-projects.github.io/micronaut-guides-poc/latest/micronaut-token-propagation-gradle-java.html) 
* Inject multiple layers
