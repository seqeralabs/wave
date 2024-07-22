# Wave containers

### Summary
Wave allows provisioning container images on-demand, removing the need
to build and upload them manually to a container registry.

Containers provisioned by Wave can be both disposable, i.e. ephemeral containers only
accessible for a short period of time, and regular long-term registry-persisted container
images.

### Features

* Authenticate the access to remote container registries;
* Augment container images i.e. dynamically add one or more container layers to existing images;
* Build container images on-demand for a given container file (aka Dockerfile);
* Build container images on-demand based on one or more Conda packages;
* Build container images on-demand based on one or more Spack packages, Spack support will be removed in future releases;
* Build container images for a specified target platform (currently linux/amd64 and linux/arm64);
* Push and cache built containers to a user-provided container repository;
* Build Singularity native containers both using a Singularity spec file, Conda package(s) and Spack package(s);
* Push Singularity native container images to OCI-compliant registries;


### How it works

Container provisioning requests are submitted via the endpoint `POST /container-token` specifying the target
container image or container file. The endpoint return the container name provisioned by Wave that can be accessed
by a regular Docker client or any other container client compliant via [Docker registry v2 API](https://docs.docker.com/registry/spec/api/).

When a disposable container is requested, Wave acts as a proxy server between the Docker client and the target container
registry. It instruments the container manifest, adding on-demand the new layers specified in the user submitted request.
Then the *pull* flow is managed by Docker client as in any other container, the base image layers are downloaded from the
container registry where the image is stored, while the instrumented layers are downloaded via the Wave service.


### Requirements

* Java 19 or later
* Linux or macOS
* Docker engine (for development)
* Kubernetes cluster (for production)

### Get started

1.  Clone the Wave repository from GitHub:

    ```bash
    git clone https://github.com/seqeralabs/wave && cd wave
    ```

2. Define one of more of those environment variable pairs depending the target registry you need to access:

    ```bash
    export DOCKER_USER="<Docker registry user name>"
    export DOCKER_PAT="<Docker registry access token or password>"
    export QUAY_USER="<Quay.io registry user name or password>"
    export QUAY_PAT="<Quay.io registry access token>"
    export AWS_ACCESS_KEY_ID="<AWS ECR registry access key>"
    export AWS_SECRET_ACCESS_KEY="<AWS ECR registry secret key>"
    export AZURECR_USER="<Azure registry user name>"
    export AZURECR_PAT="<Azure registry access token or password>"
    ```

3.  Setup a [local tunnel](https://github.com/localtunnel/localtunnel) to make the Wave service accessible to the Docker client (only needed if you are running on macOS):

    ```bash
    npx localtunnel --port 9090
    ```

    Then configure in your environment the following variable using the domain name return by Local tunnel, e.g.

    ```bash
    export WAVE_SERVER_URL="https://sweet-nights-report.loca.lt"
    ```

4. Run the service in your computer:

    ```
    bash run.sh
    ```

4.  Submit a container request to the Wave service using the `curl` tool

    ```bash
    curl \
        -H "Content-Type: application/json" \
        -X POST $WAVE_SERVER_URL/container-token \
        -d '{"containerImage":"ubuntu:latest"}' \
        | jq -r .targetImage
    ```

5. Pull the container image using the name returned in the previous command, for example

    ```bash
    docker pull sweet-nights-report.loca.lt/wt/617e7da1b37a/library/ubuntu:latest
    ```


> **Note**
> You can use the [Wave](https://github.com/seqeralabs/wave-cli) command line tool instead of `curl` to interact with
> the Wave service and submit more complex requests.

## Debugging

-   To debug http requests made proxy client add the following Jvm setting:

    ```bash
    '-Djdk.httpclient.HttpClient.log=requests,headers'
    ```

## TypeSpec API Specifications

- You can find the API specifications using (typespec)[https://github.com/microsoft/typespec] in typespec directory. Use following command to generate the API specifications.

    ```bash
    'cd typespec'
    'tsp install'
    'tsp compile .'
    ```

- Check `typespec/tsp-output` directory for the generated API specifications.

## Related links
* [Wave command line tool](https://github.com/seqeralabs/wave-cli)
* [Distribution (formerly Registry) API](https://distribution.github.io/distribution/spec/api/)
