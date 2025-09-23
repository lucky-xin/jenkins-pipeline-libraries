FROM python:3.12

RUN apt-get update && \
    apt-get install -y --no-install-recommends git && \
    rm -rf /var/lib/apt/lists/* && \
    pip install -U pip && \
    pip install pyinstaller uv


