import xyz.dev.ops.maven.MavenUtils
import xyz.dev.ops.notify.DingTalk

def call(String robotId,
         String baseImage = "openjdk:17.0-slim",
         String buildImage = "maven:3.9.11-amazoncorretto-17",
         String svcName = "",
         String dockerRepository = "47.120.49.65:5001",
         String k8sServerUrl = "https://kubernetes.default.svc.cluster.local",
         String k8sDeployImage = "bitnami/kubectl:latest",
         String k8sDeployContainerArgs = "-u root:root --entrypoint \"\""
) {

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
            // Maven配置
            MAVEN_BUILD_ARGS = "-u root:root -v $HOME/.m2:/root/.m2"
            K8S_DEPLOY_CONTAINER_ARGS = "$k8sDeployContainerArgs"
            //镜像仓库地址
            DOCKER_REPOSITORY = "$dockerRepository"
            NAMESPACE = 'micro-svc-dev'

            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            // k8s发布文件模板id
            K8S_DEPLOYMENT_FILE_ID = 'deployment-micro-svc-template'
        }

        stages {
            stage("Maven构建 & 代码审核") {
                agent {
                    docker {
                        image "${buildImage}"
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
                            if (!svcName.isEmpty()) {
                                env.SERVICE_NAME = "$svcName"
                            } else {
                                env.SERVICE_NAME = "${env.ARTIFACT_ID}"
                            }
                            env.PROJECT_VERSION = mvnUtils.readVersion()
                            env.VERSION = "${env.PROJECT_VERSION}-${env.COMMIT_ID}"

                            if ("${env.BRANCH_NAME}" == "pre") {
                                //docker镜像 tag 为区分环境,pre 前缀有v
                                env.VERSION = "v${env.VERSION}"
                            }

                            env.IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
                            env.JAR_FILE = "${env.ARTIFACT_ID}-${env.PROJECT_VERSION}.jar"

                            echo "服务名称: ${env.SERVICE_NAME}"
                            echo "版本: ${env.VERSION}"
                            echo "JAR文件: ${env.JAR_FILE}"
                        }

                        sh '''
                        # 打包项目（使用Jenkins管理的settings.xml和.m2缓存）
                        mvn -s "$MAVEN_SETTINGS" package -DskipTests=true

                        # 验证JAR文件是否生成
                        ls -la target/*.jar
                        '''
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post(
                                    "${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【Maven构建 & 代码审核】失败！"
                            )
                        }
                    }
                }
            }
            stage('封装Docker镜像') {
                steps {
                    echo "开始构建Docker镜像多平台构建，然后镜像推送到镜像注册中心..."
                    script {
                        def fullImageName = "${env.DOCKER_REPOSITORY}/${env.IMAGE_NAME}"
                        sh """
                        export DOCKER_BUILDKIT=1
                        docker buildx ls
                        
                        docker buildx build \
                          --build-arg TZ=Asia/Shanghai \
                          --build-arg BASE_IMAGE=${baseImage} \
                          --build-arg JAR_FILE=${env.JAR_FILE} \
                          --build-arg APPLICATION_NAME=${env.SERVICE_NAME} \
                          -t ${fullImageName}:${env.VERSION} \
                          --platform linux/amd64,linux/arm64 \
                          --push \
                          .
                        """
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post(
                                    "${robotId}",
                                    "${env.SERVICE_NAME}",
                                    "【封装Docker镜像】失败！"
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
        } //stages

        post {
            always { cleanWs() }
        }
    } //pipeline
}

