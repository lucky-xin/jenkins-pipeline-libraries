#!/bin/bash

# protobuf-c Docker 镜像构建脚本
# 用于构建包含所有依赖的 Docker 镜像，提高 Jenkins 构建效率

set -e

# 配置变量
IMAGE_NAME="xin8/dev-env/cplus"
IMAGE_TAG="latest"
DOCKERFILE="Dockerfile"

echo "=== 构建 protobuf-c Docker 镜像 ==="

# 检查 Dockerfile 是否存在
if [ ! -f "$DOCKERFILE" ]; then
    echo "错误: $DOCKERFILE 不存在"
    exit 1
fi

# 构建 Docker 镜像
echo "开始构建 Docker 镜像: ${IMAGE_NAME}:${IMAGE_TAG}"
docker buildx build -f "$DOCKERFILE" -t "${IMAGE_NAME}:${IMAGE_TAG}" \
  --platform linux/amd64,linux/arm64/v8 --load .

# 验证镜像构建成功
if [ $? -eq 0 ]; then
    echo "=== Docker 镜像构建成功 ==="
    
    # 显示镜像信息
    echo "镜像信息:"
    docker images | grep "$IMAGE_NAME"
    
    # 测试镜像
    echo "=== 测试 Docker 镜像 ==="
    docker run --rm "${IMAGE_NAME}:${IMAGE_TAG}" /bin/bash -c "
        echo 'Docker 镜像测试:'
        autoconf --version | head -1
        automake --version | head -1
        libtool --version | head -1
        protoc --version
        cmake --version | head -1
        echo '镜像测试完成'
    "
    
    echo "=== 构建完成 ==="
    echo "使用方法:"
    echo "1. 在 Jenkinsfile 中使用: image '${IMAGE_NAME}:${IMAGE_TAG}'"
    echo "2. 本地测试: docker run -it ${IMAGE_NAME}:${IMAGE_TAG}"
    
else
    echo "错误: Docker 镜像构建失败"
    exit 1
fi
