---
title: Augment a container with a directory
---

The Wave CLI supports container augmentation with a specified directory. You can use container augmentation to dynamically add a layer to your container, so you can inject scripts or configuration files as a new layer.

The following limitations apply:

- A file must be no larger than 1 MB each.
- A directory must be no larger than 10 MB, inclusive of all files.
- A base image must be specified.

## Related CLI arguments

The following arguments are used for a directory build:

- `--layer`: A directory that contains layer content.
- `--image` or `-i`: An existing container image. The default image registry is `docker.io`. Specify an image name such as `alpine:latest` or an image URL such as `public.ecr.aws/docker/library/busybox`.

## Example usage

Create a new context directory:

```
mkdir -p new-layer/usr/local/bin
printf 'echo Hello world!' > new-layer/usr/local/bin/hello.sh
chmod +x new-layer/usr/local/bin/hello.sh
```

Use the CLI to build the image and run the result with Docker:

```
docker run $(wave -i alpine --layer new-layer) sh -c hello.sh
```
