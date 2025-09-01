import xyz.dev.ops.notify.DingTalk

def call(String robotId,
         String baseImage = "alpine:latest",
         String buildImage = "golang:1.25",
         String svcName = "",
         String version = "1.0.0",
         String dockerRepository = "47.120.49.65:5001",
         String k8sServerUrl = "https://kubernetes.default.svc.cluster.local",
         String k8sDeployImage = "bitnami/kubectl:latest",
         String k8sDeployContainerArgs = "-u root:root --entrypoint \"\"") {

    def dingTalk = new DingTalk()

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
            SERVICE_NAME = "${svcName}"  //服务名称
            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            VERSION = "${version}-${COMMIT_ID}" //版本
            K8S_DEPLOY_CONTAINER_ARGS = "$k8sDeployContainerArgs"
            IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "$dockerRepository"
            GITLAB_HOST = 'lab.pistonint.com'
        }
        stages {
            stage("Golang构建 & 代码审核") {
                agent {
                    docker {
                        image "${buildImage}"
                        args "${env.BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    checkout scm
                    withCredentials([usernamePassword(
                            credentialsId: 'gitlab-secret',
                            usernameVariable: 'GIT_USERNAME',
                            passwordVariable: 'GIT_PASSWORD')]
                    ) {
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

                        # 拉取依赖并构建（校验可编译）
                        # 设置超时和重试，避免网络问题
                        timeout 300s bash -c '
                            for i in {1..3}; do
                                echo "尝试下载依赖 (第 $i 次)..."
                                if go mod download; then
                                    echo "依赖下载成功！"
                                    break
                                else
                                    echo "依赖下载失败 (第 $i 次)"
                                    if [ $i -eq 3 ]; then
                                        echo "所有重试都失败了"
                                        exit 1
                                    fi
                                    sleep 10
                                fi
                            done
                        '
                        
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
                            dingTalk.post(
                                    "${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【Golang构建 & 代码审核】失败！"
                            )
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
                                passwordVariable: 'GIT_PASSWORD')]
                        ) {
                            sh label: 'Docker build with GitLab credentials', script: '''
                            set -eux
                            # 启用 BuildKit
                            export DOCKER_BUILDKIT=1
                            
                            # 检查 buildx 是否可用
                            if docker buildx version >/dev/null 2>&1 && docker buildx inspect jenkins-builder >/dev/null 2>&1; then
                                echo "✅ 使用 buildx 构建镜像..."
                                docker buildx build \
                                  --platform linux/arm64/v8,linux/amd64 \
                                  --tag ${env.DOCKER_REPOSITORY}/${env.IMAGE_NAME}:${env.VERSION} \
                                  --build-arg ${env.PLATFORM}=linux/arm64/v8 \
                                  --push \
                                  .
                            else
                                echo "⚠️ buildx 不可用，使用传统构建方式..."
                                docker build \
                                  -t ${env.DOCKER_REPOSITORY}/${env.IMAGE_NAME}:${env.VERSION} \
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
            stage('k8s部署') {
                agent {
                    docker {
                        image "$k8sDeployImage"
                        args "${env.K8S_DEPLOY_CONTAINER_ARGS}"
                    }
                }
                steps {
                    withKubeConfig([credentialsId: "jenkins-k8s-config",
                                    serverUrl    : "$k8sServerUrl"]) {
                        // 使用 configFile 插件，创建 Kubernetes 部署文件 deployment.yaml
                        configFileProvider([configFile(
                                fileId: "${env.K8S_DEPLOYMENT_FILE_ID}",
                                targetLocation: "deployment.tpl")
                        ]) {
                            script {
                                sh "cat deployment.tpl"
                                def deployTemplate = readFile(encoding: "UTF-8", file: "deployment.tpl")
                                def deployment = deployTemplate
                                        .replaceAll("\\{APP_NAME\\}", "${env.SERVICE_NAME}")
                                        .replaceAll("\\{NAMESPACE\\}", "${env.NAMESPACE}")
                                        .replaceAll("\\{DOCKER_REPOSITORY\\}", "${env.DOCKER_REPOSITORY}")
                                        .replaceAll("\\{IMAGE_NAME\\}", "${env.IMAGE_NAME}")
                                        .replaceAll("\\{VERSION\\}", "${env.VERSION}")
                                writeFile(encoding: 'UTF-8', file: './deploy.yaml', text: "${deployment}")
                            }

                            // 输出新创建的部署 yaml 文件内容
                            sh "cat deploy.yaml"
                            // 执行 Kuberctl 命令进行部署操作
                            sh "kubectl apply -n ${NAMESPACE} -f deploy.yaml"
                        }
                    }
                }
                post {
                    //发布消息给团队中所有的人
                    failure {
                        script {
                            dingTalk.post(
                                    "${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【k8s部署】失败！"
                            )
                        }
                    }
                    success { script { dingTalk.post("${robotId}", "${env.SERVICE_NAME}") } }
                    always { cleanWs() }
                }
            }
        } //pipeline
        post {
            always { cleanWs() }
        }
    }
}