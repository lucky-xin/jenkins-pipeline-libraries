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
                  sonarqubeServerUrl    : config.sonarqubeServerUrl ?: "http://8.145.35.103:9000",
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
            // Maven配置
            MAVEN_BUILD_ARGS = "-u root:root -v $HOME/.m2:/root/.m2"
            K8S_DEPLOY_CONTAINER_ARGS = "${params.k8sDeployContainerArgs}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            NAMESPACE = 'micro-svc-dev'
            IMAGE_NAME = "micro-svc/${params.svcName}"
            SERVICE_NAME = "${params.svcName}"
            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            // k8s发布文件模板id
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
        }

        stages {
            stage("Vue3构建 & 代码审核") {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.MAVEN_BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    echo '开始使用 Node 构建前端...'
                    checkout scm
                    sh label: 'Node build in container', script: """
                    set -eux
                    # 设置 Node.js 内存限制，避免堆内存溢出
                    export NODE_OPTIONS="--max-old-space-size=4096"

                    node -v
                    corepack enable
                    corepack prepare yarn@1.22.22 --activate

                    export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
                    export YARN_TIMEOUT=600000

                    yarn config set registry https://registry.npmmirror.com
                    yarn config set network-concurrency 8
                    yarn config set prefer-offline true
                    yarn config set cache-folder /root/.cache/yarn

                    yarn install --frozen-lockfile --network-timeout 600000 --prefer-offline
                    yarn build

                    test -d dist && ls -la dist || (echo "构建产物 dist 不存在" && exit 1)
                """
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【Vue3构建 & 代码审核】失败！"
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

                            docker buildx build \
                              -t $REGISTRY/$IMAGE_NAME:$VERSION \
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
                                version               : env.VERSION,
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

