FROM python:3.12

ARG PIP_INDEX_URL="https://pypi.tuna.tsinghua.edu.cn/simple"

RUN apt-get update && \
    apt-get install -y --no-install-recommends git && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /root/.pip && \
    HOST=$(echo "$PIP_INDEX_URL" | sed -E 's#^https?://([^/]+)/?.*$#\1#') && \
    printf "[global]\nindex-url = %s\ntrusted-host = %s\n" "$PIP_INDEX_URL" "$HOST" > /root/.pip/pip.conf && \
    pip install -i "$PIP_INDEX_URL" -U pip && \
    pip install -i "$PIP_INDEX_URL" pyinstaller uv pytest


