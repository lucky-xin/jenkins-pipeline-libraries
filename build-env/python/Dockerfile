FROM python:3.12-alpine

# 设置构建参数（默认值为空，构建时传入）
ARG NEXUS_URL="https://xyz.com/repository/python-group/simple"
ARG NEXUS_USERNAME
ARG NEXUS_PASSWORD
ENV NEXUS_URL=${NEXUS_URL}

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories &&\
    apk update && \
    apk add --no-cache git && \
    mkdir -p /root/.pip && \
    pip config set global.index-url ${NEXUS_URL} && \
    echo "[global]\n\
    index-url = https://${NEXUS_USERNAME}:${NEXUS_PASSWORD}@${NEXUS_URL}/repository/pypi-group/simple\n\
    trusted-host = ${NEXUS_HOST}" > /root/.pip/pip.conf && \
    pip install -U pip && \
    RUN pip install pyinstaller uv


