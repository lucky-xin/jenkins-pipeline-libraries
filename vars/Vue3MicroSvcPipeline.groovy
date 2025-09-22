import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.notify.DingTalk

/**
 * Vue3 微服务前端通用流水线（vars）
 *
 * 功能：
 *  - Node 构建（Yarn）
 *  - SonarQube 代码扫描
 *  - Docker 多架构镜像构建与推送
 *  - Kubernetes 发布（模板渲染）
 *
 * 先决条件：
 *  - 凭据：docker-registry-secret、sonarqube-token-secret
 *  - Config File Provider：部署模板（fileId: deployment-micro-svc-template）
 */

def call(Map<String, Object> config) {
    /**
     * 入参（config）：
     *  robotId                 钉钉机器人ID
     *  baseImage               Nginx 基础镜像（可选）
     *  buildImage              Node 构建镜像（默认 node:24.6.0-alpine）
     *  svcName                 服务名（必填）
     *  version                 大版本（最终包含 commit）
     *  dockerRepository        镜像仓库
     *  sqServerUrl             SonarQube 内网地址
     *  sqDashboardUrl          SonarQube 外网地址
     *  k8sServerUrl            k8s API 地址
     *  k8sDeployImage          kubectl 镜像
     *  k8sDeployArgs  kubectl 容器参数
     */
    // 设置默认值
    def params = [robotId         : config.robotId ?: '',
                  baseImage       : config.baseImage ?: "nginx:1.27-alpine",
                  buildImage      : config.buildImage ?: "node:24.6.0-alpine",
                  sourceDir       : config.sourceDir ?: 'src', // 源码目录
                  testDir         : config.testDir ?: 'test', // 单元测试目录
                  svcName         : config.svcName ?: "",
                  version         : config.version ?: "1.0.0",
                  dockerRepository: config.dockerRepository ?: "47.120.49.65:5001",
                  sqServerUrl     : config.sqServerUrl ?: "http://172.29.35.103:9000",
                  sqDashboardUrl  : config.sqDashboardUrl ?: "http://8.145.35.103:9000",
                  k8sServerUrl    : config.k8sServerUrl ?: "https://47.107.91.186:6443",
                  sqCliImage      : config.sqCliImage ?: "xin8/devops/sonar-scanner-cli:latest",//SonarQube扫描客户端镜像
                  k8sDeployImage  : config.k8sDeployImage ?: "bitnami/kubectl:latest",
                  k8sDeployArgs   : config.k8sDeployArgs ?: "-u root:root --entrypoint \"\""]

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
            NPM_CONFIG_CACHE = "/root/.npm"

            // Node.js 构建配置 - 优化性能和资源使用
            NODE_BUILD_ARGS = "-u root:root -v $HOME/.cache/npm:/root/.npm --cpus=4 --memory=6g --shm-size=2g"
            K8S_DEPLOY_ARGS = "${params.k8sDeployArgs}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            NAMESPACE = 'micro-svc-dev'
            IMAGE_NAME = "micro-svc/${params.svcName}"
            SERVICE_NAME = "${params.svcName}"
            // 如果是pre分支则镜像版本为：'v' + 大版本号，如果是非pre分支则版本号为：大版本号 + '-' +【Git Commot id】
            VERSION = "${BRANCH_NAME == 'pre' ? 'v' + params.version : params.version + '-' + GIT_COMMIT.substring(0, 8)}"
            // k8s发布文件模板id
            K8S_DEPLOY_FILE_ID = 'deployment-front-end-template'
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
                    sh label: "Node build in container", script: """
                        set -eux
                        # 设置 Node.js 内存限制，避免堆内存溢出
                        export NODE_OPTIONS="--max-old-space-size=4096"
                                
                        npm config set registry https://registry.npmmirror.com
                        npm config set cache /root/.npm
                        npm config set prefer-offline true
    
                        npm install
                        
                        npm run build
    
                        test -d dist && ls -la dist || (echo "构建产物 dist 不存在" && exit 1)
                    """
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
            stage("代码审核") {
                agent {
                    docker {
                        image "${params.sqCliImage}"
                        args '-u root:root'
                        reuseNode true
                    }
                }
                steps {
                    withCredentials([string(credentialsId: 'sonarqube-token-secret', variable: 'SONAR_TOKEN')]) {
                        script {
                            sh """
                                echo '开始执行 SonarQube 代码扫描...'
                                echo '当前工作目录:'
                                pwd
                                
                                echo 'sonar-scanner -v'
                                sonar-scanner -v        

                                # 运行SonarQube扫描
                                sonar-scanner \
                                    -Dsonar.host.url=${params.sqServerUrl} \
                                    -Dsonar.token=${SONAR_TOKEN} \
                                    -Dsonar.projectKey=${env.SERVICE_NAME} \
                                    -Dsonar.projectName=${env.SERVICE_NAME} \
                                    -Dsonar.projectVersion=${env.VERSION} \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                    -Dsonar.projectBaseDir=. \
                                    -Dsonar.sources=${params.sourceDir} \
                                    -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/*.min.js,**/coverage/**,**/.nyc_output/** \
                                    -Dsonar.javascript.file.suffixes=.js,.jsx,.vue \
                                    -Dsonar.typescript.file.suffixes=.ts,.tsx
                            """
                        }
                    }
                }
                post {
                    always {
                        // 归档报告
                        script {
                            if (fileExists("coverage/lcov.info")) {
                                archiveArtifacts artifacts: "coverage/lcov.info", fingerprint: true
                            }
                            if (fileExists("coverage/test-results.xml")) {
                                archiveArtifacts artifacts: "coverage/test-results.xml", fingerprint: true
                            }
                            if (fileExists("coverage/html")) {
                                archiveArtifacts artifacts: "coverage/html/**/*", fingerprint: true
                            }
                        }
                    }
                    success {
                        // 发布HTML覆盖率报告
                        script {
                            if (fileExists("coverage/html/index.html")) {
                                archiveArtifacts artifacts: "coverage/html/**/*", fingerprint: true
                                echo "HTML覆盖率报告已归档，可在构建产物中下载查看"
                            }
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
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }
            stage('k8s发布') {
                agent {
                    docker {
                        image "${params.k8sDeployImage}"
                        args "${params.k8sDeployArgs}"
                    }
                }
                steps {
                    script {
                        k8sDeployService.deploy([
                                robotId         : params.robotId,
                                serviceName     : env.SERVICE_NAME,
                                namespace       : env.NAMESPACE,
                                dockerRepository: env.DOCKER_REPOSITORY,
                                imageName       : env.IMAGE_NAME,
                                version         : env.VERSION,
                                k8sServerUrl    : params.k8sServerUrl,
                                k8sDeployImage  : params.k8sDeployImage,
                                k8sDeployArgs   : env.K8S_DEPLOY_ARGS,
                                k8sDeployFileId : env.K8S_DEPLOY_FILE_ID,
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
    } //pipeline
}

