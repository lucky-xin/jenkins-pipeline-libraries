#!/bin/bash

# 构建多平台 SonarQube 扫描器镜像脚本
# 支持 linux/arm64 和 linux/amd64 平台

set -euo pipefail

# 配置变量
IMAGE_NAME="xin8/sonar-scanner-cli"
IMAGE_TAG="latest"
PLATFORMS="linux/arm64,linux/amd64"
DOCKERFILE="Dockerfile_ARM64"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查必要文件是否存在
check_files() {
    log_info "检查必要文件..."
    
    if [[ ! -f "$DOCKERFILE" ]]; then
        log_error "Dockerfile 不存在: $DOCKERFILE"
        exit 1
    fi
    
    if [[ ! -f "entrypoint.sh" ]]; then
        log_error "entrypoint.sh 不存在"
        exit 1
    fi
    
    log_success "所有必要文件检查通过"
}

# 检查 Docker 和 buildx 是否可用
check_docker() {
    log_info "检查 Docker 环境..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装或不在 PATH 中"
        exit 1
    fi
    
    # 检查 Docker buildx 是否可用
    if ! docker buildx version &> /dev/null; then
        log_error "Docker buildx 不可用，请确保 Docker 版本 >= 19.03"
        exit 1
    fi
    
    log_success "Docker 环境检查通过"
}

# 构建多平台镜像
build_image() {
    log_info "开始构建多平台镜像..."
    log_info "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}"
    log_info "支持平台: ${PLATFORMS}"
    
    # 构建并推送镜像
    docker buildx build \
        --platform "${PLATFORMS}" \
        --tag "${IMAGE_NAME}:${IMAGE_TAG}" \
        --push \
        --file "${DOCKERFILE}" \
        .
    
    log_success "多平台镜像构建并推送完成"
}

# 构建本地镜像（不推送）
build_local() {
    log_info "开始构建本地镜像..."
    log_info "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}"
    
    # 构建本地镜像
    docker buildx build \
        --platform "${PLATFORMS}" \
        --tag "${IMAGE_NAME}:${IMAGE_TAG}" \
        --load \
        --file "${DOCKERFILE}" \
        .
    
    log_success "本地镜像构建完成"
}

# 显示帮助信息
show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help     显示此帮助信息"
    echo "  -l, --local    仅构建本地镜像（不推送）"
    echo "  -p, --push     构建并推送镜像到仓库（默认）"
    echo "  -t, --tag TAG  指定镜像标签（默认: latest）"
    echo "  --platforms    指定平台列表（默认: linux/arm64,linux/amd64）"
    echo ""
    echo "示例:"
    echo "  $0                    # 构建并推送 latest 标签"
    echo "  $0 -l                 # 仅构建本地镜像"
    echo "  $0 -t v1.0.0         # 构建并推送 v1.0.0 标签"
    echo "  $0 --platforms linux/amd64  # 仅构建 amd64 平台"
}

# 主函数
main() {
    local build_mode="push"
    local platforms="${PLATFORMS}"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -l|--local)
                build_mode="local"
                shift
                ;;
            -p|--push)
                build_mode="push"
                shift
                ;;
            -t|--tag)
                IMAGE_TAG="$2"
                shift 2
                ;;
            --platforms)
                platforms="$2"
                shift 2
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 更新平台变量
    PLATFORMS="${platforms}"
    
    log_info "=== SonarQube 扫描器镜像构建脚本 ==="
    log_info "构建模式: ${build_mode}"
    log_info "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}"
    log_info "支持平台: ${PLATFORMS}"
    echo ""
    
    # 执行构建流程
    check_files
    check_docker

    if [[ "${build_mode}" == "local" ]]; then
        build_local
    else
        build_image
    fi
    
    log_success "=== 构建完成 ==="
    log_info "镜像: ${IMAGE_NAME}:${IMAGE_TAG}"
    log_info "平台: ${PLATFORMS}"
    
    if [[ "${build_mode}" == "push" ]]; then
        log_info "镜像已推送到仓库，可以使用以下命令拉取："
        log_info "docker pull ${IMAGE_NAME}:${IMAGE_TAG}"
    else
        log_info "本地镜像已构建完成，可以使用以下命令查看："
        log_info "docker images ${IMAGE_NAME}"
    fi
}

# 执行主函数
main "$@"
