import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.notify.DingTalk

def call(Map<String, Object> config) {
    // 设置默认值
    def params = [robotId               : config.robotId ?: '',
                  baseImage             : config.baseImage ?: "alpine:latest",
                  buildImage            : config.buildImage ?: "golang:1.25",
                  svcName       : config.svcName ?: "",//微服务名称，规范【小写英文字母，数字，中划线'-'】
                  version       : config.version ?: "1.0.0",//大版本号，最终的版本号为：大版本号-【Git Commot id】
                  sqServerUrl   : config.sqServerUrl ?: "http://172.29.35.103:9000",//SonarQube内网地址
                  sqDashboardUrl: config.sqDashboardUrl ?: "http://8.145.35.103:9000",//SonarQube外网地址，如果想在非公司网络看质量报告则配置SonarQube外网地址，否则该配置为内网地址
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
            // 如果是pre分支则镜像版本为：'v' + 大版本号，如果是非pre分支则版本号为：大版本号 + '-' +【Git Commot id】
            VERSION = "${BRANCH_NAME == 'pre' ? 'v' + params.version : params.version + '-' + GIT_COMMIT.substring(0, 8)}"
            K8S_DEPLOY_CONTAINER_ARGS = "${params.k8sDeployContainerArgs}"
            // 镜像名称
            IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            GITLAB_HOST = 'lab.pistonint.com'
            // k8s命名空间
            NAMESPACE = 'micro-svc-dev'
            // k8s部署文件模板id
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
        }
        stages {
            stage("代码审核") {
                steps {
                    checkout scm
                    withCredentials([string(credentialsId: 'sonarqube-token-secret', variable: 'SONAR_TOKEN')]) {
                        script {
                            // 使用 sh 命令直接运行 Docker 容器
                            sh """
                                echo '开始执行 SonarQube 代码扫描...'
                                docker run --rm -u root:root \
                                    -v ./:/usr/src \
                                    -e SONAR_TOKEN=${SONAR_TOKEN} \
                                    -e SONAR_HOST_URL=${params.sqServerUrl} \
                                    -e SONAR_PROJECT_KEY=${env.SERVICE_NAME} \
                                    -e SONAR_PROJECT_NAME=${env.SERVICE_NAME} \
                                    -e SONAR_PROJECT_VERSION=${env.VERSION} \
                                    -e SONAR_SOURCE_ENCODING=UTF-8 \
                                    -e SONAR_SOURCES=/usr/src \
                                    -e SONAR_TESTS=/usr/src \
                                    -e SONAR_EXCLUSIONS=**/vendor/**,**/node_modules/**,**/*.pb.go,**/testdata/** \
                                    -e SONAR_TEST_EXCLUSIONS=**/*_test.go \
                                    -e SONAR_TEST_INCLUSIONS=**/*_test.go \
                                    -e SONAR_COVERAGE_EXCLUSIONS=**/*_test.go \
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
                                    reason: "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
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
                    withCredentials([usernamePassword(
                            credentialsId: 'gitlab-secret',
                            usernameVariable: 'GIT_USERNAME',
                            passwordVariable: 'GIT_PASSWORD')]) {
                        sh label: "Go build in container", script: """
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
                        
                        # 清理 git 临时凭据映射（避免后续泄露）
                        git config --global --unset-all url."https://$GIT_USERNAME:$GIT_PASSWORD@${GITLAB_HOST}/".insteadOf || true
                    """
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
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
                                usernameVariable: 'REGISTRY_USERNAME',
                                passwordVariable: 'REGISTRY_PASSWORD')]) {
                            sh label: "Docker build with GitLab credentials", script: """
                                set -eux
                                # 启用 BuildKit
                                export DOCKER_BUILDKIT=1
                                
                                # 登录镜像仓库
                                echo "$REGISTRY_PASSWORD" | docker login "$DOCKER_REPOSITORY" -u "$REGISTRY_USERNAME" --password-stdin
                                
                                # 检查 buildx 是否可用
                                if docker buildx version >/dev/null 2>&1 && docker buildx inspect jenkins-builder >/dev/null 2>&1; then
                                    echo "✅ 使用 buildx 构建镜像..."
                                    docker buildx build \
                                      -t $DOCKER_REPOSITORY/$IMAGE_NAME:$VERSION \
                                      --platform linux/amd64,linux/arm64/v8 \
                                      --push \
                                      .
                                else
                                    echo "⚠️ buildx 不可用，使用传统构建方式..."
                                    docker build \
                                      -t $DOCKER_REPOSITORY/$IMAGE_NAME:$VERSION \
                                      .
                                fi
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
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
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
                                    reason : "【${env.STAGE_NAME}】失败！"
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
    }
}