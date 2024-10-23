#!/bin/bash

set -e

echo "Compiling TypeSpec..."
tsp compile

echo "Installing TypeSpec packages..."
tsp install .

IMAGE_NAME="your-docker-repo/openapi-server"
echo "Building Docker image: $IMAGE_NAME..."
docker build -t "$IMAGE_NAME" .

echo "Logging in to Docker registry..."
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

echo "Pushing Docker image to the registry..."
docker push "$IMAGE_NAME"

echo "Docker image pushed successfully: $IMAGE_NAME"
