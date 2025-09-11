# protobuf-c 构建环境 Dockerfile
FROM ubuntu:22.04

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# 安装基础系统包
RUN apt-get update -q -y && \
    apt-get install -q -y \
        # 基础构建工具
        build-essential \
        autotools-dev \
        autoconf \
        automake \
        libtool \
        pkg-config \
        # 网络工具
        wget \
        curl \
        git \
        ca-certificates \
        # protobuf 相关
        libprotobuf-dev \
        libprotoc-dev \
        protobuf-compiler \
        # CMake 和文档工具
        cmake \
        doxygen \
        graphviz \
        # 其他有用工具
        vim \
        less \
        tree \
        && \
    # 清理缓存
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 创建构建用户（可选，提高安全性）
RUN useradd -m -s /bin/bash builder && \
    usermod -aG sudo builder

# 设置工作目录
WORKDIR /workspace

# 保持 root 用户，因为 Jenkins 需要 root 权限进行构建
# USER builder

# 验证安装
RUN echo "=== 构建环境验证 ===" && \
    autoconf --version | head -1 && \
    automake --version | head -1 && \
    libtool --version | head -1 && \
    pkg-config --version && \
    protoc --version && \
    cmake --version | head -1 && \
    echo "=== 环境准备完成 ==="

# 默认命令
CMD ["/bin/bash"]
