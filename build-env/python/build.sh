#!/bin/bash

# protobuf-c Docker 镜像构建脚本
# 用于构建包含所有依赖的 Docker 镜像，提高 Jenkins 构建效率

set -e

# 显示使用说明
show_usage() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  -r, --registry REGISTRY    设置 Docker 仓库地址 (默认: xin8)"
    echo "  -t, --tag TAG             设置镜像标签 (默认: latest)"
    echo "  -h, --help                显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -r myregistry -t v1.0.0"
    echo "  $0 --registry myregistry --tag v1.0.0"
}

# 默认配置变量
DOCKER_REGISTRY="xin8" # Docker 仓库地址
IMAGE_TAG="latest"
build_mode="local" # 默认推送到仓库

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -l|--local)
            build_mode="local"
            shift
            ;;
        -p|--push)
            build_mode="push"
            shift
            ;;
        -r|--registry)
            DOCKER_REGISTRY="$2"
            shift 2
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            echo "错误: 未知参数 '$1'"
            show_usage
            exit 1
            ;;
    esac
done

IMAGE_NAME="$DOCKER_REGISTRY/devops/python"
DOCKERFILE="Dockerfile"

echo "=== 构建 protobuf-c Docker 镜像 ==="

# 检查 Dockerfile 是否存在
if [ ! -f "$DOCKERFILE" ]; then
    echo "错误: $DOCKERFILE 不存在"
    exit 1
fi

# 构建 Docker 镜像
echo "开始构建 Docker 镜像: ${IMAGE_NAME}:${IMAGE_TAG}"
if [ "$build_mode" = "local" ]; then
    docker buildx build \
      -f "$DOCKERFILE" \
      -t "${IMAGE_NAME}:${IMAGE_TAG}" \
      --platform linux/amd64,linux/arm64/v8 \
      --load .

    if [ $? -eq 0 ]; then
        echo "=== Docker 本地镜像构建成功 ==="
        echo "镜像信息:"
        docker images | grep "$IMAGE_NAME"

        echo "=== 本地构建完成 ==="
        echo "使用方法:"
        echo "1. 在 Jenkinsfile 中使用: image '${IMAGE_NAME}:${IMAGE_TAG}'"
        echo "2. 本地测试: docker run -it ${IMAGE_NAME}:${IMAGE_TAG}"
    else
        echo "错误: Docker 本地镜像构建失败"
        exit 1
    fi
else
    docker buildx build \
      -f "$DOCKERFILE" \
      -t "${IMAGE_NAME}:${IMAGE_TAG}" \
      --platform linux/amd64,linux/arm64/v8 \
      --push .

    if [ $? -eq 0 ]; then
        echo "=== Docker 镜像构建并推送成功 ==="
        echo "镜像: ${IMAGE_NAME}:${IMAGE_TAG}"
        echo "可拉取命令: docker pull ${IMAGE_NAME}:${IMAGE_TAG}"
    else
        echo "错误: Docker 镜像构建或推送失败"
        exit 1
    fi
fi
