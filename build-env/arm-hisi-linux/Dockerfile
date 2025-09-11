
ARG DOCKER_REPO=xin8
FROM $DOCKER_REPO/build/arm-mix-linux:latest

# 设置环境变量
ENV GO_VERSION=1.23.10
ENV GOOS=linux
ENV GOARCH=amd64
ENV CGO_ENABLED=1
ENV GOPROXY=https://goproxy.cn,direct
ENV GOSUMDB=sum.golang.google.cn

# 复制交叉编译工具链
COPY --from=sdk_builder /etc/profile /etc/profile
COPY --from=sdk_builder /opt/hisi-linux /opt/hisi-linux
COPY --from=sdk_builder /opt/linux /opt/linux

# 安装Go语言环境
RUN wget -q https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz -P /tmp/ \
    && tar -C /usr/local -xzf /tmp/go${GO_VERSION}.linux-amd64.tar.gz \
    && rm /tmp/go${GO_VERSION}.linux-amd64.tar.gz \
    && /usr/local/go/bin/go env -w CGO_ENABLED=1 \
    && /usr/local/go/bin/go env -w GOPROXY=https://goproxy.cn,direct \
    && /usr/local/go/bin/go env -w GOSUMDB=sum.golang.google.cn \
    && echo "export PATH=\$PATH:/usr/local/go/bin" >> /etc/profile

# 配置SSH服务
RUN mkdir /var/run/sshd \
    && echo 'root:root' | chpasswd \
    && sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config \
    && sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config \
    && echo "export PATH=\$PATH:/usr/local/go/bin" >> /root/.bashrc

# 创建软链接解决libisl.so.19的问题
RUN ln -sf /lib/x86_64-linux-gnu/libisl.so.23 /lib/x86_64-linux-gnu/libisl.so.19

# 创建应用目录
WORKDIR /app

# 暴露端口
EXPOSE 22 8110

# 创建启动脚本
RUN echo '#!/bin/bash' > /start.sh \
    && echo 'service ssh start' >> /start.sh \
    && echo 'exec "$@"' >> /start.sh \
    && chmod +x /start.sh

# 设置入口点
ENTRYPOINT ["/start.sh"]
CMD ["sleep", "infinity"]