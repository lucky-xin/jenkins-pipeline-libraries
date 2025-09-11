#!/bin/bash

# protobuf-c 快速构建脚本
# 基于 xin8/protobuf-c-builder:latest 镜像

set -e

DOCKER_IMAGE="xin8/protobuf-c-builder:latest"
PROJECT_DIR="$(pwd)"

echo "=== protobuf-c 快速构建 ==="
echo "使用镜像: ${DOCKER_IMAGE}"
echo "项目目录: ${PROJECT_DIR}"

# 检查 Docker
if ! command -v docker >/dev/null 2>&1; then
    echo "错误: Docker 未安装"
    exit 1
fi

# 检查镜像
if ! docker image inspect "${DOCKER_IMAGE}" >/dev/null 2>&1; then
    echo "拉取镜像: ${DOCKER_IMAGE}"
    docker pull "${DOCKER_IMAGE}"
fi

echo "开始构建..."

# 使用 Docker 容器构建
docker run --rm \
    -v "${PROJECT_DIR}:/workspace" \
    -w /workspace \
    "${DOCKER_IMAGE}" \
    bash -c "
        echo '=== 生成构建系统 ==='
        ./autogen.sh
        
        echo '=== 配置构建 ==='
        ./configure --prefix=/workspace/install
        
        echo '=== 编译项目 ==='
        make -j\$(nproc)
        
        echo '=== 运行测试 ==='
        make check
        
        echo '=== 安装 ==='
        make install
        
        echo '=== 构建完成 ==='
        echo '安装文件:'
        find /workspace/install -type f | head -10
    "

echo "构建完成！"
echo "安装文件位于: ./install/"
