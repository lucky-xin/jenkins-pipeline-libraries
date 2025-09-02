import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.notify.DingTalk

def call(Map<String, Object> config) {
    // 设置默认值
    def params = [robotId               : config.robotId ?: '',
                  baseImage             : config.baseImage ?: "alpine:latest",
                  buildImage            : config.buildImage ?: "golang:1.25",
                  svcName               : config.svcName ?: "",
                  version               : config.version ?: "1.0.0",
                  sonarqubeServerUrl    : config.sonarqubeServerUrl ?: "172.29.35.103:9000",
                  dockerRepository      : config.dockerRepository ?: "47.120.49.65:5001",
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
            BUILD_ARGS = "-u root:root -v $HOME/.cache/go-build/:/tmp/.cache/go-build/"  //本地仓库挂载
            SERVICE_NAME = "${params.svcName}"  //服务名称
            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            VERSION = "${params.version}-${COMMIT_ID}" //版本
            K8S_DEPLOY_CONTAINER_ARGS = "${params.k8sDeployContainerArgs}"
            IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            GITLAB_HOST = 'lab.pistonint.com'
            NAMESPACE = 'micro-svc-dev'
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
            SONARQUBE_TOKEN_SECRET = credentials('sonarqube-token-secret')
        }
        stages {
            stage("代码审核") {
                agent {
                    docker {
                        image "sonarsource/sonar-scanner-cli:latest"
                        args "-e SONAR_HOST_URL=\"${params.sonarqubeServerUrl}\" -e SONAR_TOKEN=\"${env.SONARQUBE_TOKEN_SECRET}\""
                    }
                }
                steps {
                    sh """
                       sonar-scanner \
                       -Dsonar.projectKey=${env.SERVICE_NAME} \
                       -Dsonar.sources=. \
                       -Dsonar.projectName=${env.SERVICE_NAME} \
                    """
                    // -Dsonar.host.url=${params.sonarqubeServerUrl}
                    // -Dsonar.login=cb4238366e2fb9b8a89324eef5581cdec439a36d
                }
            }
            stage("Golang构建") {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    checkout scm
                    withCredentials([usernamePassword(
                            credentialsId: 'gitlab-secret',
                            usernameVariable: 'GIT_USERNAME',
                            passwordVariable: 'GIT_PASSWORD')]) {
                        script {
                            if ("${env.BRANCH_NAME}" == "pre") {
                                env.VERSION = "v${env.VERSION}"
                            }
                        }
                        sh label: 'Go build in container', script: '''
                        set -eux
                        go version
                        
                        # 首先配置 Go 环境变量，避免访问 proxy.golang.org
                        go env -w GOPROXY=https://goproxy.cn,https://mirrors.aliyun.com/goproxy,direct
                        go env -w GOSUMDB=off
                        go env -w GOOS=linux 
                        go env -w GOARCH=arm64 
                        go env -w CGO_ENABLED=0 
                        go env -w GOPRIVATE="${GITLAB_HOST}"/* 
                        go env -w GONOSUMDB="${GITLAB_HOST}"/*
                        
                        # 然后配置 Git 私仓凭据
                        git config --global url."https://${GIT_USERNAME}:${GIT_PASSWORD}@${GITLAB_HOST}/".insteadOf "https://${GITLAB_HOST}/"

                        # 验证 Go 环境配置
                        echo "=== Go 环境配置 ==="
                        go env GOPROXY GOSUMDB GOPRIVATE GONOSUMDB
                        echo "=================="
                        go build -ldflags="-w -s" -o main main.go
                        
                        ls -la

                        # 清理 git 临时凭据映射（避免后续泄露）
                        git config --global --unset-all url."https://$GIT_USERNAME:$GIT_PASSWORD@${GITLAB_HOST}/".insteadOf || true
                    '''
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post("${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【Golang构建 & 代码审核】失败！")
                        }
                    }
                }
            }
            stage("封装Docker镜像") {
                steps {
                    script {
                        echo '开始构建Docker镜像多平台构建，然后镜像推送到镜像注册中心...'

                        withCredentials([usernamePassword(
                                credentialsId: 'docker-registry-secret',
                                usernameVariable: 'GIT_USERNAME',
                                passwordVariable: 'GIT_PASSWORD')]) {
                            sh label: 'Docker build with GitLab credentials', script: '''
                            set -eux
                            # 启用 BuildKit
                            export DOCKER_BUILDKIT=1
                            
                            # 检查 buildx 是否可用
                            if docker buildx version >/dev/null 2>&1 && docker buildx inspect jenkins-builder >/dev/null 2>&1; then
                                echo "✅ 使用 buildx 构建镜像..."
                                docker buildx build \
                                  --platform linux/arm64/v8,linux/amd64 \
                                  --tag $DOCKER_REPOSITORY/$IMAGE_NAME:$VERSION \
                                  --push \
                                  .
                            else
                                echo "⚠️ buildx 不可用，使用传统构建方式..."
                                docker build \
                                  -t $DOCKER_REPOSITORY/$IMAGE_NAME:$VERSION \
                                  .
                            fi
                        '''
                        }
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post(
                                    "${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【封装Docker】失败！"
                            )
                        }
                    }
                }
            }
            stage("k8s发布") {
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
                            dingTalk.post("${params.robotId}",
                                    "${env.SERVICE_NAME}")
                        }
                    }
                    failure {
                        script {
                            dingTalk.post("${params.robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【k8s发布】失败！")
                        }
                    }
                    always { cleanWs() }
                }
            }
        } //stages
        post {
            always { cleanWs() }
        }
    }
}