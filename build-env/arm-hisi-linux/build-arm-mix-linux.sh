DOCKER_REPO=xin8
docker buildx build -t $DOCKER_REPO/build/arm-mix-linux:latest -f DockerfileBaseBuild \
 --platform linux/amd64,linux/arm64/v8 --load .