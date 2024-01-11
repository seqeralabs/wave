---
title: User guide
---

## Getting started

Wave containers can be used with any container runtime supporting the [Docker Registry API v2](https://docs.docker.com/registry/spec/api/) and it's smoothly integrated with [Nextflow](https://www.nextflow.io/) and [Seqera Platform](https://cloud.tower.nf/).

This feature requires Nextflow `22.10.0` or later.

### Nextflow installation

If you've already installed Nextflow, update to the latest version using this command::

```bash
nextflow -self-update
```

If you don't have Nextflow already installed, install it with the command below:

```bash
curl get.nextflow.io | bash
```

### Wave configuration

Wave can be used in any Nextflow pipeline by adding the following snippet to your `nextflow.config` file:

```groovy
wave {
    enabled = true
}

tower {
    accessToken = '<your access token>'
}
```

:::tip
The use of the Seqera access token is not mandatory, however, it's required to enable access to private repositories and it allows higher service rate limits compared to anonymous users.
:::

## API limits

The Wave service implements API rate limits for API calls. Authenticated users have higher rate limits than anonymous users.

If an access token is provided, the following rate limits apply:

- 100 container images per hour
- 1,000 container images per minute

If an access token isn't provided, the following rate limits apply:

- 25 container builds per day
- 250 container pulls per hour

## Tutorials

### Authenticate private repositories

Wave allows the use of private repositories in your Nextflow pipelines. The repository access keys must be provided via [Seqera credentials](https://help.tower.nf/23.1/credentials/overview/).

When you have created the credentials, you only need to specify your [Seqera access token](https://help.tower.nf/23.1/api/overview/#authentication) in your pipeline configuration file. If the credentials were created in a Seqera organization workspace, specify the workspace ID as well in the config file as shown below:

```groovy
tower {
    accessToken = '<your access token>'
    workspaceId = '<your workspace id>'
}
```

That's it. When launching the pipeline execution, Wave will allow Nextflow to access the private container repositories defined in your pipeline configuration, using the credentials stored in the Seqera Platform credentials manager.

### Build Nextflow module containers

Wave can build and provision container images on-demand for your Nextflow pipelines.

To enable this feature, add the `Dockerfile` of the container to be built in the [module directory](https://www.nextflow.io/docs/latest/dsl2.html#module-binaries) where the pipeline process is defined. When Wave is enabled, it automatically uses the Dockerfile to build the required container, upload to the registry, and uses the container to carry out the tasks defined in the module.

:::tip
Make sure the process does not declare a `container` directive, otherwise it will take precedence over the Dockerfile definition.
:::

If a process uses a `container` directive and you still want to build the container using the `Dockerfile` provided in the module directory, add the following setting to the pipeline config file:

```groovy
wave.strategy = ['dockerfile','container']
```

The above line instructs Wave to give the module `Dockerfile` priority over process `container` directives.

:::warning
Wave currently does not support `ADD`, `COPY` and other Dockerfile commands that access files in the host file system.
:::

### Build Conda based containers

Wave allows the provisioning of containers based on the [`conda` directive](https://www.nextflow.io/docs/latest/process.html#conda) used by the processes in your Nextflow pipeline. This is a quick alternative to building Conda packages in the local computer. Moreover, this enables the use of Conda packages in your pipeline when deploying it in cloud-native platforms such as AWS Batch and Kubernetes, which do not allow the (easy) use of the Conda package manager.

Having Wave enabled in your pipeline, there's nothing else to do other than define the `conda` requirements in the pipeline processes provided the same process does not also specify a `container` directive or a Dockerfile.

In the latter case, add the following setting to your pipeline configuration:

```groovy
wave.strategy = ['conda']
```

The above setting instructs Wave to only use the `conda` directive to provision the pipeline containers, ignoring the use of the `container` directive and any `Dockerfile`(s).

### Store container images into a private repository

Containers built by Wave are uploaded to the Wave default repository hosted on AWS ECR with name `195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build`. The images in this repository are automatically deleted 1 week from the date of their push.

If you want to store Wave containers in your own container repository use the following settings in the Nextflow configuration file:

```groovy
wave.build.repository = 'example.com/your/build-repo'
wave.build.cacheRepository = 'example.com/your/cache-repo'
```

The first repository is used to store the built container images. The second one is used to store the individual image layers for caching purposes.

The repository access keys need to be specified using the Seqera Platform credentials manager as specified in the [Authenticate private repositories](#Authenticate private repositories) section.

## Advanced settings

The following configuration options are available:

| Method                       | Description                                                                                                                                                              |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `wave.enabled`               | Enable/disable the execution of Wave containers                                                                                                                          |
| `wave.endpoint`              | The Wave service endpoint (default: `https://wave.seqera.io`)                                                                                                            |
| `wave.build.repository`      | The container repository where image built by Wave needs to be uploaded (note: the corresponding credentials need to be provided in your Seqera Platform account).       |
| `wave.build.cacheRepository` | The container repository used to cache image layers build by the Wave service (note: the corresponding credentials need to be provided in your Seqera Platform account). |
| `wave.conda.mambaImage`      | The Mamba container image is used to build Conda based container. This is expected to be [micromamba-docker](https://github.com/mamba-org/micromamba-docker) image.      |
| `wave.conda.commands`        | One or more commands to be added to the Dockerfile used by build a Conda based image.                                                                                    |
| `wave.strategy`              | The strategy to be used when resolving ambiguous Wave container requirement (default: `'container,dockerfile,conda'`)                                                    |
| `wave.freeze`                | When `freeze` mode is enabled containers provisioned by Wave are stored permanently in the repository specified via the setting `wave.build.repository`.                 |

## More examples

Check out the [Wave showcase repository](https://github.com/seqeralabs/wave-showcase) for more examples how to use Wave containers.
