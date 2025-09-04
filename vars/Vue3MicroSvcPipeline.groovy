import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.maven.MavenUtils
import xyz.dev.ops.notify.DingTalk

def call(Map<String, Object> config) {
    // 设置默认值
    def params = [robotId               : config.robotId ?: '',
                  baseImage             : config.baseImage ?: "nginx:1.27-alpine",
                  buildImage            : config.buildImage ?: "node:24.6.0-alpine3.22",
                  svcName               : config.svcName ?: "",
                  dockerRepository      : config.dockerRepository ?: "47.120.49.65:5001",
                  sonarqubeServerUrl    : config.sonarqubeServerUrl ?: "http://172.29.35.103:9000",
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
            // Node.js 构建配置 - 优化性能和资源使用
            NODE_BUILD_ARGS = "-u root:root -v $HOME/.cache/yarn:/root/.cache/yarn -v $HOME/.cache/npm:/root/.npm --cpus=4 --memory=6g --shm-size=2g"
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
            NPM_CONFIG_CACHE = "/root/.npm"
            YARN_CACHE_FOLDER = "/root/.cache/yarn"
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
                    export YARN_CACHE_FOLDER=/root/.cache/yarn
                    export NPM_CONFIG_CACHE=/root/.npm
                    export NPM_CONFIG_PREFER_OFFLINE=true
                    export NPM_CONFIG_AUDIT=false
                    export NPM_CONFIG_FUND=false
                    
                    # 配置 yarn 优化设置
                    echo "=== 配置 Yarn ==="
                    yarn config set registry https://registry.npmmirror.com
                    yarn config set network-concurrency 16
                    yarn config set prefer-offline true
                    yarn config set cache-folder /root/.cache/yarn
                    yarn config set network-timeout 300000
                    yarn config set child-concurrency 8
                    yarn config set enable-progress-bars false
                    yarn config set enable-emoji false
                    
                    # 清理可能的缓存问题
                    echo "=== 清理缓存 ==="
                    yarn cache clean --force || true
                    
                    # 优化的依赖安装
                    echo "=== 开始安装依赖 ==="
                    time yarn install \\
                        --frozen-lockfile \\
                        --network-timeout 300000 \\
                        --prefer-offline \\
                        --silent \\
                        --ignore-engines \\
                        --ignore-optional \\
                        --non-interactive
                    
                    echo "=== 依赖安装完成 ==="
                    echo "node_modules 大小: \$(du -sh node_modules 2>/dev/null || echo 'N/A')"
                    
                    # 优化的构建过程
                    echo "=== 开始构建 ==="
                    export NODE_OPTIONS="--max-old-space-size=4096 --max-semi-space-size=128"
                    export NODE_ENV=production
                    
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
                            echo '开始代码审核...'
                            docker run --rm -u root:root \
                                --platform linux/amd64 \
                                -v ./:/usr/src \
                                --entrypoint /bin/sh \
                                sonarsource/sonar-scanner-cli:latest \
                                -c "sonar-scanner -Dsonar.sources=/usr/src -Dsonar.projectVersion=${env.COMMIT_ID} -Dsonar.projectName=${env.SERVICE_NAME} -Dsonar.sourceEncoding=UTF-8 -Dsonar.host.url=${params.sonarqubeServerUrl} -Dsonar.login=${SONAR_TOKEN} -Dsonar.projectKey=${env.SERVICE_NAME} -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/*.min.js"
                            echo 'SonarQube 代码扫描完成'
                        }
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                robotId: "${params.robotId}",
                                jobName: "${env.SERVICE_NAME}",
                                reason: "【代码审核】失败！"
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
                        sh label: "Docker buildx build and push", script: """
                            set -eux
                            # 启用 BuildKit
                            export DOCKER_BUILDKIT=1

                            # 登录镜像仓库
                            echo "$REGISTRY_PASSWORD" | docker login "$DOCKER_REPOSITORY" -u "$REGISTRY_USERNAME" --password-stdin

                            # 设置版本标签
                            VERSION="${env.COMMIT_ID}"
                            
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
                                    robotId           : "${params.robotId}",
                                    jobName           : "${env.SERVICE_NAME}",
                                    sonarqubeServerUrl: "${params.sonarqubeServerUrl}"
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

