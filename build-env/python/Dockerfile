FROM python:3.12-alpine

# 设置构建参数（默认值为空，构建时传入）
ARG NEXUS_URL="https://xyz.com/repository/python-group/simple"
ARG NEXUS_USERNAME
ARG NEXUS_PASSWORD
ENV NEXUS_URL=${NEXUS_URL}

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories && \
    apk update && \
    apk add --no-cache git sed && \
    mkdir -p /root/.pip && \
    HOST=$(echo "$NEXUS_URL" | sed -E 's#^https?://([^/]+)/?.*$#\1#') && \
    SCHEME=$(echo "$NEXUS_URL" | sed -E 's#^(https?)://.*#\1#') && \
    PATH_PART=$(echo "$NEXUS_URL" | sed -E 's#^https?://[^/]+(.*)$#\1#') && \
    if [ -n "$NEXUS_USERNAME" ] && [ -n "$NEXUS_PASSWORD" ]; then \
      INDEX_URL="$SCHEME://$NEXUS_USERNAME:$NEXUS_PASSWORD@$HOST$PATH_PART"; \
    else \
      INDEX_URL="$NEXUS_URL"; \
    fi && \
    pip config set global.index-url "$INDEX_URL" && \
    printf "[global]\nindex-url = %s\ntrusted-host = %s\n" "$INDEX_URL" "$HOST" > /root/.pip/pip.conf && \
    pip install -U pip && \
    pip install pyinstaller uv


