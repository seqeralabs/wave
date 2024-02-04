# Wave CLI

Command line tool for [Wave containers provisioning service](https://github.com/seqeralabs/wave).

### Summary 

Wave allows augmenting existing containers and building containers on demand so
that it can be used in your Docker (replace-with-your-own-fav-container-engine) workflow.

### Features

* Build container images on-demand for a given container file (aka Dockerfile);
* Build container images on-demand based on one or more [Conda](https://conda.io/) packages;
* Build container images on-demand based on one or more [Spack](https://spack.io/)  packages;
* Build container images for a specified target platform (currently linux/amd64 and linux/arm64);
* Push and cache built containers to a user-provided container repository;
* Build Singularity native containers both using a Singularity spec file, Conda package(s) and Spack package(s);
* Push Singularity native container images to OCI-compliant registries;
  
### Installation 


#### Binary download 

Download the Wave pre-compiled binary for your operating system from the 
[GitHub releases page](https://github.com/seqeralabs/wave-cli/releases/latest) and give execute permission to it.

#### Homebrew (Linux and macOS)

If you use [Homebrew](https://brew.sh/), you can install like this:

```bash
 brew install seqeralabs/tap/wave-cli
```

### Get started

1. Create a basic Dockerfile file (or use an existing one)
   
    ```bash
    cat << EOF > ./Dockerfile
    FROM alpine 

    RUN apk update && apk add bash cowsay \
            --update-cache \
            --repository https://alpine.global.ssl.fastly.net/alpine/edge/community \
            --repository https://alpine.global.ssl.fastly.net/alpine/edge/main \
            --repository https://dl-3.alpinelinux.org/alpine/edge/testing
    EOF
    ```

2. Run it provisioning the container on-the-fly


    ```bash
    docker run --rm $(wave -f ./Dockerfile) cowsay "Hello world"
    ```


### Examples 

#### Augment a container image 

1. Create a directory holding the files to be added to your container:

    ```bash
    mkdir -p new-layer/usr/local/bin
    printf 'echo Hello world!' > new-layer/usr/local/bin/hello.sh 
    chmod +x new-layer/usr/local/bin/hello.sh
    ```

2. Run the container via Wave 

    ```bash
    container=$(wave -i alpine --layer new-layer)
    docker run $container sh -c hello.sh
    ```

#### Build a container with Dockerfile 

1. Create a Dockerfile for your container image: 

    ```bash
    cat << EOF > ./Dockerfile
    FROM alpine 
    ADD hello.sh /usr/local/bin/
    EOF
    ```

2. Create the build context directory:

    ```bash
    mkdir -p build-context/
    printf 'echo Hello world!' > build-context/hello.sh 
    chmod +x build-context/hello.sh 
    ```

3. Build and run the container on the fly:

    ```bash
    container=$(wave -f Dockerfile --context build-context)
    docker run $container sh -c hello.sh
    ```

#### Build a Conda multi-packages container 

```bash
container=$(wave --conda-package bamtools=2.5.2 --conda-package samtools=1.17)
docker run $container sh -c 'bamtools --version && samtools --version'
```

#### Build a container by using a Conda environment file

1. Create the Conda environment file:

    ```bash
    cat << EOF > ./conda.yaml
    name: my-conda
    channels:
    - defaults
    - bioconda
    - conda-forge
    dependencies:
    - bamtools=2.5.2
    - samtools=1.17
    EOF
    ```

2. Build and run the container using the Conda environment:

    ```bash
    container=$(wave --conda-file ./conda.yaml)
    docker run $container sh -c 'bamtools --version'
    ```


#### Build a container by using a Conda lock file

```bash
container=$(wave --conda-package https://prefix.dev/envs/pditommaso/wave/6x60arx3od13/conda-lock.yml)
docker run $container cowpy 'Hello, world!'
```


#### Build a Conda package container arm64 architecture

```bash
container=$(wave --conda-package fastp --platform linux/arm64)
docker run --platform linux/arm64 $container sh -c 'fastp --version'
```

#### Build a Spack package container

```bash
container=$(wave --spack-package cowsay)
docker run $container sh -c 'cowsay Hello world!'
```

#### Build a Singularity container using a Conda package and pushing to a OCI registry

```bash
container=$(wave --singularity --conda-package bamtools=2.5.2 --build-repo docker.io/user/repo --freeze --await)
singularity exec $container bamtools --version
```

### Development

1. Install GraalVM-Java 21.0.1

    ```bash
    sdk install java 21.0.1-graal
    ```

    or if it's already installed

   ```bash
   sdk use java 21.0.1-graal
   ```

2. Compile & run tests 

    ```bash
    ./gradlew check
    ```

3. Native compile

    ```bash
    ./gradlew app:nativeCompile
    ```

4. Run the native binary 

    ```bash
    ./app/build/native/nativeCompile/wave --version
    ```
