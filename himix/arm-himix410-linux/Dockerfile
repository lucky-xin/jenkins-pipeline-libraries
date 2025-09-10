# 使用 Ubuntu 基础镜像
FROM ubuntu:18.04

# 维护者信息
MAINTAINER YourName <your.email@example.com>

# 设置环境变量，例如定义工具链路径、目标平台架构等。
# 这些环境变量在后续编译代码时可能会被用到。
ENV TARGET_PLATFORM="hi3556v200" \
    ARCH="armv7-a" \
    TOOLCHAIN_NAME=arm-himix410-linux \
    TOOLCHAIN_PREFIX=${TOOLCHAIN_NAME}- \
    TOOLCHAIN_HOME=/opt/${TOOLCHAIN_NAME} \
    PATH="${PATH}:${TOOLCHAIN_HOME}/bin"

# 解决 apt-get 安装时的时区交互问题，并更换源以提高下载速度（可选）
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y tzdata && \
    apt-get install -y --no-install-recommends \
    # 安装编译所需的基础库和工具
    build-essential \
    make \
    gcc \
    g++ \
    git \
    wget \
    # 64位系统上运行32位工具链所需的兼容库，非常重要！
    lib32z1 \
    lib32z1-dev \
    lib32stdc++6 \
    lib32ncurses5 \
    # 其他可能需要的依赖，根据工具链和项目需求添加
    gcc-multilib \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 创建工具链的安装目录
RUN mkdir -p ${TOOLCHAIN_HOME}

# 将本地的工具链压缩包复制到镜像中（请确保 Dockerfile 同级目录下有此文件）
# 或者使用 ADD 命令，它会自动解压 .tgz 或 .tar.gz 文件
ADD arm-himix410-linux.tgz /tmp/
# 如果是 .tar.bz2 或其他格式，可能需要在 RUN 指令中手动解压
# COPY arm-hisiv300-linux.tar.bz2 /tmp/
# RUN tar -jxvf /tmp/arm-hisiv300-linux.tar.bz2 -C /tmp/

# 安装工具链（假设工具包内有安装脚本）
# 根据你实际获取的工具链的安装方法进行调整，有时只需要解压到特定目录即可
RUN cd /tmp/arm-himix410-linux && \
    ./arm-himix410-linux.install ${TOOLCHAIN_HOME} && \
    # 安装后清理临时文件，减小镜像体积
    rm -rf /tmp/*

# 设置工作目录（可选）
WORKDIR /home/work

# 验证工具链是否安装成功（可选）
# RUN ${TOOLCHAIN_PREFIX}gcc --version