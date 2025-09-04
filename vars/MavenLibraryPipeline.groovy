import xyz.dev.ops.notify.DingTalk

/**
 * Maven 库项目发布流水线（vars）
 *
 * 功能：读取 POM 信息，执行 Maven 构建与部署（deploy），并推送变更通知。
 * 先决条件：
 *  - Config File Provider: Maven settings.xml（fileId: 42697037-54bd-44a1-80c2-7a97d30f2266）
 */

def call(Map<String, Object> config) {
    /**
     * 入参（config）：
     *  robotId        钉钉机器人ID（可选）
     *  baseImg        基础镜像（可选）
     *  builderImage   构建镜像（默认 maven:3.9.11-amazoncorretto-17）
     *  sqDashboardUrl SonarQube 外网地址（可选，仅用于通知展示）
     */
    // 设置默认值
    def params = [
            robotId       : config.robotId ?: '',
            baseImg       : config.baseImg ?: "openjdk:17.0-slim",
            builderImage  : config.builderImage ?: "maven:3.9.11-amazoncorretto-17",
            sqDashboardUrl: config.sqDashboardUrl ?: "http://8.145.35.103:9000",
    ]

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
            COMMIT_ID = "${GIT_COMMIT}".substring(0, 8)
            BRANCH = "$env.BRANCH_NAME"
        }

        stages {
            stage("Maven构建 & 代码审核") {
                agent {
                    docker {
                        image "${params.builderImage}"
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
                        # 打包项目（使用Jenkins管理的settings.xml和.m2缓存）
                        mvn -s "$MAVEN_SETTINGS" -B clean package deploy
                        '''
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
                }
            }
        } //stages

        post {
            always { cleanWs() }
        }
    } //pipeline
}

