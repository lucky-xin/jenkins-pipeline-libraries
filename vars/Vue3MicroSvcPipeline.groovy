import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.notify.DingTalk

def call(Map<String, Object> config) {
    // 设置默认值
    def params = [robotId               : config.robotId ?: '',
                  baseImage             : config.baseImage ?: "nginx:1.27-alpine",
                  buildImage            : config.buildImage ?: "node:24.6.0-alpine",
                  svcName               : config.svcName ?: "",
                  version: config.version ?: "1.0.0",
                  dockerRepository      : config.dockerRepository ?: "47.120.49.65:5001",
                  sqServerUrl           : config.sqServerUrl ?: "http://172.29.35.103:9000",
                  sqDashboardUrl        : config.sqDashboardUrl ?: "http://8.145.35.103:9000",
                  k8sServerUrl          : config.k8sServerUrl ?: "https://47.107.91.186:6443",
                  k8sDeployImage        : config.k8sDeployImage ?: "bitnami/kubectl:latest",
                  k8sDeployContainerArgs: config.k8sDeployContainerArgs ?: "-u root:root --entrypoint \"\""]

    def dingTalk = new DingTalk()
    def k8sDeployService = new K8sDeployService(this)
    pipeline {
        agent any
        options {
            timestamps()
            disableConcurrentBuilds()
            // ansiColor('xterm')
            timeout(time: 180, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '5'))
        }
        environment {
            YARN_CACHE_FOLDER = "/root/.cache/yarn"
            NPM_CONFIG_CACHE = "/root/.npm"

            // Node.js 构建配置 - 优化性能和资源使用
            NODE_BUILD_ARGS = "-u root:root -v $HOME/.cache/yarn:${env.YARN_CACHE_FOLDER} -v $HOME/.cache/npm:${env.NPM_CONFIG_CACHE} --cpus=4 --memory=6g --shm-size=2g"
            K8S_DEPLOY_CONTAINER_ARGS = "${params.k8sDeployContainerArgs}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            NAMESPACE = 'micro-svc-dev'
            IMAGE_NAME = "micro-svc/${params.svcName}"
            SERVICE_NAME = "${params.svcName}"
            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            // k8s发布文件模板id
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
            // Node.js 性能优化环境变量
            NODE_OPTIONS = "--max-old-space-size=4096 --max-semi-space-size=128"

            // 修复 Rollup 架构兼容性
            ROLLUP_PLATFORM = "linux"
            ROLLUP_ARCH = "x64"
            // 强制 Rollup 使用 JavaScript 实现
            ROLLUP_DISABLE_NATIVE = "true"
            // 禁用 Rollup 原生二进制文件
            ROLLUP_NATIVE_DISABLE = "1"
        }

        stages {
            stage("Vue3构建") {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.NODE_BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    echo '开始使用 Node 构建前端...'
                    checkout scm
                    sh label: 'Optimized Node build in container', script: """
                    set -eux
                    
                    # 显示系统信息
                    echo "=== 系统信息 ==="
                    node -v
                    npm -v
                    echo "CPU 核心数: \$(nproc)"
                    echo "内存信息: \$(free -h)"
                    echo "磁盘空间: \$(df -h /)"
                    
                    # 启用 corepack 并准备 yarn
                    corepack enable
                    corepack prepare yarn@1.22.22 --activate
                    
                    # 设置环境变量优化
                    export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
                    export YARN_TIMEOUT=300000
                    export YARN_NETWORK_TIMEOUT=300000
                    export YARN_NETWORK_CONCURRENCY=16
                    export YARN_PREFER_OFFLINE=true
                    export YARN_CACHE_FOLDER=\${env.YARN_CACHE_FOLDER}
                    export NPM_CONFIG_CACHE=\${env.NPM_CONFIG_CACHE}
                    export NPM_CONFIG_PREFER_OFFLINE=true
                    export NPM_CONFIG_AUDIT=false
                    export NPM_CONFIG_FUND=false
                    
                    # 配置 yarn 优化设置
                    echo "=== 配置 Yarn ==="
                    yarn config set registry https://registry.npmmirror.com
                    yarn config set network-concurrency 16
                    yarn config set prefer-offline true
                    yarn config set cache-folder \${env.YARN_CACHE_FOLDER}
                    yarn config set network-timeout 300000
                    yarn config set child-concurrency 8
                    yarn config set enable-progress-bars false
                    yarn config set enable-emoji false
                    
                    # 配置 npm 优化设置
                    echo "=== 配置 NPM ==="
                    npm config set prefer-offline true
                    npm config set audit false
                    npm config set fund false
                    npm config set cache \${env.NPM_CONFIG_CACHE}
                    
                    # 清理可能的缓存问题
                    echo "=== 清理缓存和依赖 ==="
                    rm -rf node_modules package-lock.json yarn.lock || true
                    yarn cache clean --force || true
                    npm cache clean --force || true
                    
                    # 优化的依赖安装
                    echo "=== 开始安装依赖 ==="
                    # 使用 npm 安装，排除可选依赖
                    echo "使用 npm 安装依赖（排除可选依赖）..."
                    time npm install \
                        --omit=optional \
                        --prefer-offline \
                        --no-audit \
                        --no-fund \
                        --force
                    
                    # 如果 npm 安装失败，回退到 yarn
                    if [ \$? -ne 0 ]; then
                        echo "npm 安装失败，回退到 yarn..."
                        time yarn install \
                            --network-timeout 300000 \
                            --prefer-offline \
                            --silent \
                            --ignore-engines \
                            --ignore-optional \
                            --non-interactive \
                            --force
                    fi
                    
                    echo "=== 依赖安装完成 ==="
                    echo "node_modules 大小: \$(du -sh node_modules 2>/dev/null || echo 'N/A')"

                    # 修复 Rollup 架构问题
                    echo "=== 修复 Rollup 架构问题 ==="
                    
                    # 方法1: 强制安装正确的 Rollup 原生模块
                    npm install @rollup/rollup-linux-x64-musl --no-save --force --omit=optional || echo "x64 musl 模块安装失败"
                    npm install @rollup/rollup-linux-x64-gnu --no-save --force --omit=optional || echo "x64 gnu 模块安装失败"
                    
                    # 方法1.5: 强制安装 ARM64 模块（如果存在）
                    npm install @rollup/rollup-linux-arm64-musl --no-save --force --omit=optional || echo "ARM64 musl 模块安装失败"
                    npm install @rollup/rollup-linux-arm64-gnu --no-save --force --omit=optional || echo "ARM64 gnu 模块安装失败"
                    
                    # 方法2: 创建符号链接，让 ARM64 模块指向 x64 模块
                    if [ -d "node_modules/@rollup/rollup-linux-x64-musl" ] && [ ! -d "node_modules/@rollup/rollup-linux-arm64-musl" ]; then
                        echo "创建 ARM64 musl 到 x64 musl 的符号链接"
                        mkdir -p node_modules/@rollup
                        ln -sf rollup-linux-x64-musl node_modules/@rollup/rollup-linux-arm64-musl
                    fi
                    
                    if [ -d "node_modules/@rollup/rollup-linux-x64-gnu" ] && [ ! -d "node_modules/@rollup/rollup-linux-arm64-gnu" ]; then
                        echo "创建 ARM64 gnu 到 x64 gnu 的符号链接"
                        mkdir -p node_modules/@rollup
                        ln -sf rollup-linux-x64-gnu node_modules/@rollup/rollup-linux-arm64-gnu
                    fi
                    
                    # 方法3: 直接替换所有 Rollup 的原生模块文件，强制使用 JavaScript 实现
                    echo "查找并替换所有 Rollup native.js 文件"
                    
                    # 处理顶级 Rollup 依赖
                    if [ -f "node_modules/rollup/dist/native.js" ]; then
                        echo "备份并替换顶级 Rollup native.js 文件"
                        cp node_modules/rollup/dist/native.js node_modules/rollup/dist/native.js.backup
                        cat > node_modules/rollup/dist/native.js << 'EOF'
// 禁用原生模块，强制使用 JavaScript 实现
module.exports = function() {
    throw new Error('Native module disabled - using JavaScript implementation');
};
EOF
                    fi
                    
                    # 处理 Vite 子依赖中的 Rollup
                    if [ -f "node_modules/vite/node_modules/rollup/dist/native.js" ]; then
                        echo "备份并替换 Vite 子依赖中的 Rollup native.js 文件"
                        cp node_modules/vite/node_modules/rollup/dist/native.js node_modules/vite/node_modules/rollup/dist/native.js.backup
                        cat > node_modules/vite/node_modules/rollup/dist/native.js << 'EOF'
// 禁用原生模块，强制使用 JavaScript 实现
module.exports = function() {
    throw new Error('Native module disabled - using JavaScript implementation');
};
EOF
                    fi
                    
                    # 查找并处理所有可能的 Rollup 实例
                    find node_modules -name "native.js" -path "*/rollup/dist/*" -exec sh -c '
                        echo "处理 Rollup native.js 文件: \$1"
                        cp "\$1" "\$1.backup"
                        cat > "\$1" << "EOF"
// 禁用原生模块，强制使用 JavaScript 实现
module.exports = function() {
    throw new Error("Native module disabled - using JavaScript implementation");
};
EOF
                     _ {} \\;
                    
                    # 优化的构建过程
                    echo "=== 开始构建 ==="
                    export NODE_OPTIONS="--max-old-space-size=4096 --max-semi-space-size=128"
                    export NODE_ENV=production
                    export ROLLUP_PLATFORM="linux"
                    export ROLLUP_ARCH="x64"
                    export ROLLUP_DISABLE_NATIVE="true"
                    export ROLLUP_NATIVE_DISABLE="1"
                    # 强制 Rollup 使用 JavaScript 实现
                    export ROLLUP_FORCE_JS="true"
                    export ROLLUP_USE_JS="1"
                    
                    # 使用并行构建和优化选项
                    time yarn build --mode production
                    
                    echo "=== 构建完成 ==="
                    test -d dist && ls -la dist || (echo "构建产物 dist 不存在" && exit 1)
                    echo "构建产物大小: \$(du -sh dist 2>/dev/null || echo 'N/A')"
                """
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【Vue3构建】失败！"
                            ])
                        }
                    }
                }
            }
            stage("代码审核") {
                steps {
                    withCredentials([string(credentialsId: 'sonarqube-token-secret', variable: 'SONAR_TOKEN')]) {
                        script {
                            sh """
                                echo '开始代码审核...'
                                docker run --rm -u root:root \
                                    -v ./:/usr/src \
                                    -e SONAR_TOKEN=${SONAR_TOKEN} \
                                    -e SONAR_HOST_URL=${params.sqServerUrl} \
                                    -e SONAR_PROJECT_KEY=${env.SERVICE_NAME} \
                                    -e SONAR_PROJECT_NAME=${env.SERVICE_NAME} \
                                    -e SONAR_PROJECT_VERSION=${env.VERSION} \
                                    -e SONAR_EXCLUSIONS=**/node_modules/**,**/dist/**,**/*.min.js \
                                    -e SONAR_SOURCE_ENCODING=UTF-8 \
                                    -e SONAR_SOURCES=/usr/src \
                                    -e SONAR_TESTS=/usr/src \
                                    xin8/sonar-scanner-cli:latest
                                echo 'SonarQube 代码扫描完成'
                            """
                        }
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【代码审核】失败！"
                            ])
                        }
                    }
                }
            }
            stage('封装Docker镜像') {
                steps {
                    withCredentials([usernamePassword(
                            credentialsId: 'docker-registry-secret',
                            usernameVariable: 'REGISTRY_USERNAME',
                            passwordVariable: 'REGISTRY_PASSWORD'
                    )]) {
                        script {
                            // 设置版本标签
                            env.VERSION = "${params.version}-${env.COMMIT_ID}"
                            if ("${env.BRANCH_NAME}" == "pre") {
                                env.VERSION = "v${env.VERSION}"
                            }
                        }
                        sh label: "Docker buildx build and push", script: """
                            set -eux
                            # 启用 BuildKit
                            export DOCKER_BUILDKIT=1

                            # 登录镜像仓库
                            echo "$REGISTRY_PASSWORD" | docker login "$DOCKER_REPOSITORY" -u "$REGISTRY_USERNAME" --password-stdin
                          
                            docker buildx build \
                              -t $DOCKER_REPOSITORY/$IMAGE_NAME:$VERSION \
                              --platform linux/amd64,linux/arm64/v8 \
                              --push \
                              .
                        """
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【封装Docker镜像】失败！"
                            ])
                        }
                    }
                }
            }
            stage('k8s发布') {
                agent {
                    docker {
                        image "${params.k8sDeployImage}"
                        args "${params.k8sDeployContainerArgs}"
                    }
                }
                steps {
                    script {
                        k8sDeployService.deploy([
                                robotId               : params.robotId,
                                serviceName           : env.SERVICE_NAME,
                                namespace             : env.NAMESPACE,
                                dockerRepository      : env.DOCKER_REPOSITORY,
                                imageName             : env.IMAGE_NAME,
                                version               : env.COMMIT_ID,
                                k8sServerUrl          : params.k8sServerUrl,
                                k8sDeployImage        : params.k8sDeployImage,
                                k8sDeployContainerArgs: env.K8S_DEPLOY_CONTAINER_ARGS,
                                k8sDeploymentFileId   : env.K8S_DEPLOYMENT_FILE_ID
                        ])
                    }
                }
                post {
                    success {
                        script {
                            dingTalk.post([
                                    robotId    : "${params.robotId}",
                                    jobName    : "${env.SERVICE_NAME}",
                                    sqServerUrl: "${params.sqDashboardUrl}"
                            ])
                        }
                    }
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【k8s发布】失败！"
                            ])
                        }
                    }
                    always { cleanWs() }
                }
            }
        } //stages

        post {
            always { cleanWs() }
        }
    } //pipeline
}

