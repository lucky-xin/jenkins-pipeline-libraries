import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.maven.MavenUtils
import xyz.dev.ops.notify.DingTalk

def call(Map<String, Object> config) {
    // 设置默认值
    def params = [robotId               : config.robotId ?: '',
                  baseImage             : config.baseImage ?: "openjdk:17.0-slim",
                  buildImage            : config.buildImage ?: "maven:3.9.11-amazoncorretto-17",
                  svcName               : config.svcName ?: "",
                  sqDashboardUrl        : config.sqDashboardUrl ?: "http://8.145.35.103:9000",
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
            // Maven配置
            MAVEN_BUILD_ARGS = "-u root:root -v $HOME/.m2:/root/.m2"
            K8S_DEPLOY_CONTAINER_ARGS = "${params.k8sDeployContainerArgs}"
            //镜像仓库地址
            DOCKER_REPOSITORY = "${params.dockerRepository}"
            NAMESPACE = 'micro-svc-dev'
            // k8s发布文件模板id
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
        }

        stages {
            stage("Maven构建 & 代码审核") {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.MAVEN_BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    echo "开始Maven构建..."
                    checkout scm

                    // Maven配置文件
                    configFileProvider([configFile(
                            fileId: '42697037-54bd-44a1-80c2-7a97d30f2266',
                            variable: 'MAVEN_SETTINGS'
                    )]) {

                        script {
                            // 使用通用工具类获取 POM 信息
                            def mvnUtils = new MavenUtils(this)
                            env.ARTIFACT_ID = mvnUtils.readArtifactId()
                            if (!params.svcName.isEmpty()) {
                                env.SERVICE_NAME = "${params.svcName}"
                            } else {
                                env.SERVICE_NAME = "${env.ARTIFACT_ID}"
                            }
                            env.PROJECT_VERSION = mvnUtils.readVersion()
                            // 如果是pre分支则镜像版本为：'v' + 大版本号，如果是非pre分支则版本号为：大版本号 + '-' +【Git Commot id】
                            env.VERSION = "${env.BRANCH_NAME == 'pre' ? 'v' + env.PROJECT_VERSION : env.PROJECT_VERSION + '-' + env.GIT_COMMIT.substring(0, 8)}"
                            
                            env.IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
                            env.JAR_FILE = "${env.ARTIFACT_ID}-${env.PROJECT_VERSION}.jar"

                            echo "服务名称: ${env.SERVICE_NAME}"
                            echo "版本: ${env.VERSION}"
                            echo "JAR文件: ${env.JAR_FILE}"
                        }

                        sh """
                        # 打包项目（使用Jenkins管理的settings.xml和.m2缓存）
                        mvn -B -s "$MAVEN_SETTINGS" package sonar:sonar -Dsonar.projectKey=${env.SERVICE_NAME}

                        # 验证JAR文件是否生成
                        ls -la target/*.jar
                        """
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【Maven构建 & 代码审核】失败！"
                            ])
                        }
                    }
                }
            }
            stage('封装Docker镜像') {
                steps {
                    echo "开始构建Docker镜像多平台构建，然后镜像推送到镜像注册中心..."
                    script {
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
                                --build-arg TZ=Asia/Shanghai \
                                --build-arg BASE_IMAGE=${params.baseImage} \
                                --build-arg JAR_FILE=${env.JAR_FILE} \
                                --build-arg APPLICATION_NAME=${env.SERVICE_NAME} \
                                -t ${env.DOCKER_REPOSITORY}/${env.IMAGE_NAME}:${env.VERSION} \
                                --platform linux/amd64,linux/arm64/v8 \
                                --push \
                                .
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

