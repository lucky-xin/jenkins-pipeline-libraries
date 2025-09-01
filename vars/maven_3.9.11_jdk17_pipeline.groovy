import xyz.dev.ops.notify.DingTalk

def call(String robotId,
         String baseImg = "openjdk:17.0-slim",
         String builderImage = "maven:3.9.11-amazoncorretto-17",
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
            DOCKER_REPOSITORY = '47.120.49.65:5001' //镜像仓库地址

            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            BRANCH = "$env.BRANCH_NAME"
        }

        stages {
            stage("Maven构建 & 代码审核") {
                agent {
                    docker {
                        image "${builderImage}"
                        args "${env.MAVEN_BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    echo "开始Maven构建..."
                    checkout scm

                    script {
                        // 读取Maven POM信息
                        def pom = readMavenPom()
                        env.SERVICE_NAME = pom.getArtifactId()  //服务名称
                        env.IMAGE_NAME = "micro-svc/${env.SERVICE_NAME}"
                        env.VERSION = pom.getVersion()  //版本
                        env.PACKAGING = pom.getPackaging() //文件后缀
                        env.DOCKER_TAG = "${env.VERSION}-${env.COMMIT_ID}" //docker镜像 tag 为区分环境,pre 前缀有v
                        env.JAR_FILE = "${env.SERVICE_NAME}-${env.VERSION}.jar"

                        echo "服务名称: ${env.SERVICE_NAME}"
                        echo "版本: ${env.VERSION}"
                        echo "JAR文件: ${env.JAR_FILE}"
                    }

                    // Maven配置文件
                    configFileProvider([configFile(
                            fileId: '42697037-54bd-44a1-80c2-7a97d30f2266',
                            variable: 'MAVEN_SETTINGS'
                    )]) {
                        sh '''
                        ls -la
                        mvn -v

                        # 打包项目（使用Jenkins管理的settings.xml和.m2缓存）
                        mvn -s "$MAVEN_SETTINGS" package -DskipTests=true

                        # 验证JAR文件是否生成
                        ls -la target/*.jar
                    '''
                    }
                }
                post {
                    failure { script { dingTalk.post("${robotId}", "${env.SERVICE_NAME}") } }
                }
            }
            stage('封装Docker镜像') {
                steps {
                    echo "开始构建Docker镜像多平台构建，然后镜像推送到镜像注册中心..."
                    script {
                        def fullImageName = "${env.DOCKER_REPOSITORY}/${env.IMAGE_NAME}"
                        def dockerTag = "${env.DOCKER_TAG}"
                        if (env.BRANCH == "pre") {
                            dockerTag = "v${env.DOCKER_TAG}"
                        }
                        sh """
                        docker buildx build \
                          --build-arg TZ=Asia/Shanghai \
                          --build-arg JAR_FILE=${env.JAR_FILE} \
                          --build-arg APPLICATION_NAME=${env.SERVICE_NAME} \
                          -t ${fullImageName}:$dockerTag \
                          --platform linux/amd64,linux/arm64 \
                          --push \
                          .
                    """
                    }
                }
                post {
                    failure { script { dingTalk.post("${robotId}", "${env.SERVICE_NAME}") } }
                }
            }
            stage('k8s发布') {
                agent {
                    docker {
                        image "$k8sDeployImage"
                        args "$k8sDeployContainerArgs"
                    }
                }
                steps {
                    withKubeConfig([credentialsId: "jenkins-k8s-config",
                                    serverUrl    : "$k8sServerUrl"]) {
                        // 使用 configFile 插件，创建 Kubernetes 部署文件 deployment.yaml
                        configFileProvider([configFile(
                                fileId: "${K8S_DEPLOYMENT_FILE_ID}",
                                targetLocation: "deployment.tpl")
                        ]) {
                            script {
                                sh "cat deployment.tpl"
                                def deployTemplate = readFile(encoding: "UTF-8", file: "deployment.tpl")
                                def deployment = deployTemplate
                                        .replaceAll("\\{APP_NAME\\}", "${env.SERVICE_NAME}")
                                        .replaceAll("\\{NAMESPACE\\}", "${NAMESPACE}")
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
                    success { script { dingTalk.post("${robotId}", "${env.SERVICE_NAME}") } }
                    failure { script { dingTalk.post("${robotId}", "${env.SERVICE_NAME}") } }
                    always { cleanWs() }
                }
            }
        } //stages

        post {
            always { cleanWs() }
        }
    } //pipeline
}

