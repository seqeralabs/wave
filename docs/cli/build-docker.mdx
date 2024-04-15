---
title: Build a container from a Dockerfile
---

The Wave CLI supports building a container from a `Dockerfile`. Specifying an optional build context allows the use of `ADD` and `COPY` commands in a Dockerfile.

:::note
Building a Dockerfile that requires `--build-arg` for build time variables isn't currently supported.
:::

## Related CLI arguments

- `--containerfile` or `-f`: A Dockerfile to build. Build args aren't currently supported.
- `--context`: A directory that contains the context for the build.

## Example usage

In the following example `Dockerfile`, several packages are installed:

```
cat << EOF > ./Dockerfile
FROM alpine

RUN apk update && apk add bash cowsay \
        --update-cache \
        --repository https://alpine.global.ssl.fastly.net/alpine/edge/community \
        --repository https://alpine.global.ssl.fastly.net/alpine/edge/main \
        --repository https://dl-3.alpinelinux.org/alpine/edge/testing
EOF
```

Build and run the container based on the Dockerfile in the previous example by running the following command:

```
container=$(wave --containerfile ./Dockerfile)
docker run --rm $container cowsay "Hello world"
```

In the following example `Dockerfile`, a local context is used:

```
cat << EOF > ./Dockerfile
FROM alpine
ADD hello.sh /usr/local/bin/
EOF
```

Create the shell script referenced in the previous example by running the following commands in your terminal:

```
mkdir -p build-context/
printf 'echo Hello world!' > build-context/hello.sh
chmod +x build-context/hello.sh
```

Build and run the container based on the Dockerfile in the previous example by running the following command:

```
docker run $(wave -f Dockerfile --context build-context) sh -c hello.sh
```
