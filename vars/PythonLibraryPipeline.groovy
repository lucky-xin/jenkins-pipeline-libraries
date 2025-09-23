import xyz.dev.ops.deploy.K8sDeployService
import xyz.dev.ops.notify.DingTalk

/**
 * Python 公共依赖项目通用流水线（vars）
 *
 * 功能：
 *  - SonarQube 代码扫描
 *
 * 先决条件：
 *  - 凭据：gitlab-secret、docker-registry-secret、sonarqube-token-secret
 *  - Jenkins 节点具备 docker 权限*/

def call(Map<String, Object> config) {
    /**
     * 入参（config）：
     *  robotId                 钉钉机器人ID（可选，用于通知）
     *  baseImage               基础镜像（可选，默认 alpine:latest）
     *  buildImage              构建镜像（可选，默认 golang:1.25）
     *  svcName                 服务名（必填，作为镜像与部署名）
     *  version                 大版本号（可选，默认 1.0.0；最终版本包含 git commit）
     *  sqServerUrl             SonarQube 内网地址（可选）
     *  sqDashboardUrl          SonarQube 外网地址（可选）
     */
    // 设置默认值
    def params = [robotId         : config.robotId ?: '',
                  baseImage       : config.baseImage ?: "python:3.12-alpine",
                  buildImage      : config.buildImage ?: "xin8/devops/python:latest",
                  sqCliImage      : config.sqCliImage ?: "xin8/devops/sonar-scanner-cli:latest",//SonarQube扫描客户端镜像
                  sourceDir       : config.sourceDir ?: 'src', // 源码目录
                  testDir         : config.testDir ?: 'test', // 单元测试目录
                  svcName         : config.svcName ?: "",//微服务名称，规范【小写英文字母，数字，中划线'-'】
                  version         : config.version ?: "1.0.0",//大版本号，最终的版本号为：大版本号-【Git Commot id】
                  sqServerUrl     : config.sqServerUrl ?: "http://172.29.35.103:9000",//SonarQube内网地址
                  sqDashboardUrl  : config.sqDashboardUrl ?: "http://8.145.35.103:9000",//SonarQube外网地址，如果想在非公司网络看质量报告则配置SonarQube外网地址，否则该配置为内网地址
                  nexusUrl        : config.nexusUrl ?: "http://172.29.35.103:8081/repository/python-hosted/simple",
                  ]

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
            BUILD_ARGS = "-u root:root -v $HOME/.cache/pip/:/root/.cache/pip/ -v $HOME/.cache/uv/:/root/.cache/uv/"  //挂载 pip 依赖缓存
            SERVICE_NAME = "${params.svcName}"  //服务名称
            // 如果是pre分支则镜像版本为：'v' + 大版本号，如果是非pre分支则版本号为：大版本号 + '-' +【Git Commot id】
            VERSION = "${BRANCH_NAME == 'pre' ? 'v' + params.version : params.version + '-' + GIT_COMMIT.substring(0, 8)}"
            SOURCE_DIR = "${params.sourceDir}"
            TEST_DIR = "${params.testDir}"
            SQ_SERVER_URL = "${params.sqServerUrl}"
            NEXUS_URL = "${params.nexusUrl}"
            NEXUS_CREDENTIALS_ID = 'nexus-credentials'    // 你在 Jenkins 中配置的凭据 ID
        }
        stages {
            stage("Python构建") {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    checkout scm
                    script {
                        withCredentials([usernamePassword(
                                credentialsId: "${env.NEXUS_CREDENTIALS_ID}",
                                usernameVariable: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_PASS')]) {

                            sh label: "Python build in container", script: """
                                set -eux

                                python3 --version
                                pip --version
                                # 配置 Nexus 私仓并安装依赖
                                HOST=\$(echo "$NEXUS_URL" | sed -E 's#^https?://([^/]+)/?.*\$#\\1#')
                                SCHEME=\$(echo "$NEXUS_URL" | sed -E 's#^(https?)://.*#\\1#')
                                PATH_PART=\$(echo "$NEXUS_URL" | sed -E 's#^https?://[^/]+(.*)\$#\\1#')
                                INDEX_URL="\$SCHEME://\$NEXUS_USER:\$NEXUS_PASS@\$HOST\$PATH_PART"

                                mkdir -p /root/.pip reports
                                printf "[global]\\nindex-url = %s\ntrusted-host = %s\n" "\$INDEX_URL" "\$HOST" > /root/.pip/pip.conf

                                if [ -f requirements.txt ]; then
                                    # 使用 pip + requirements.txt 安装
                                    pip install --timeout 60 --no-cache-dir --index-url "\$INDEX_URL" --trusted-host "\$HOST" -r requirements.txt
                                    # 确保测试所需依赖存在
                                    pip install --timeout 60 --no-cache-dir --index-url "\$INDEX_URL" --trusted-host "\$HOST" pytest pytest-cov
                                    # 自动提取 requirements.txt 模块并作为 hidden-import（仅保留包名并前缀参数）
                                elif [ -f uv.lock ]; then
                                    # 使用 uv + uv.lock 安装
                                    sed -i 's#@8.145.35.103#172.29.35.103#g' uv.lock
                                    export UV_INDEX_URL="\$INDEX_URL"
                                    export UV_EXTRA_INDEX_URL="\$INDEX_URL"
                                    # CI 环境需要完整依赖，包含所有分组与 extras
                                    uv sync --all-groups --all-extras --frozen
                                fi

                                # 选择测试命令：若存在 uv.lock，使用 uv 虚拟环境执行
                                TEST_CMD="pytest"
                                if [ -f uv.lock ]; then
                                    uv --version || true
                                    TEST_CMD="uv run pytest"
                                fi

                                # 运行单元测试并生成覆盖率与JUnit报告
                                \$TEST_CMD ${TEST_DIR} \
                                    --cov=${SOURCE_DIR} \
                                    --cov-report=xml:reports/coverage.xml \
                                    --cov-report=term \
                                    --junitxml=reports/junit.xml
                            """
                        }
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([robotId: "${params.robotId}",
                                           jobName: "${env.SERVICE_NAME}",
                                           reason : "【${env.STAGE_NAME}】失败！"])
                        }
                    }
                    always {
                        sh label: "清理Nexus与pip敏感配置", script: """
                            set +e
                            rm -f /root/.pip/pip.conf /root/.config/pip/pip.conf || true
                            unset NEXUS_USER NEXUS_PASS INDEX_URL UV_INDEX_URL UV_EXTRA_INDEX_URL || true
                        """
                    }
                }
            }
            stage("代码审核") {

                agent {
                    docker {
                        image "${params.sqCliImage}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }

                steps {
                    withCredentials([string(credentialsId: 'sonarqube-token-secret', variable: 'SONAR_TOKEN')]) {
                        script {
                            // 使用 sh 命令直接运行 Docker 容器
                            sh """
                                echo '开始执行 SonarQube 代码扫描...'
                                echo 'sonar-scanner -v'
                                sonar-scanner -v

                                # 运行SonarQube扫描
                                sonar-scanner \
                                    -Dsonar.host.url=${SQ_SERVER_URL} \
                                    -Dsonar.token=\$SONAR_TOKEN \
                                    -Dsonar.projectKey=${SERVICE_NAME} \
                                    -Dsonar.projectName=${SERVICE_NAME} \
                                    -Dsonar.projectVersion=${VERSION} \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                    -Dsonar.projectBaseDir=. \
                                    -Dsonar.sources=${SOURCE_DIR} \
                                    -Dsonar.tests=${TEST_DIR} \
                                    -Dsonar.python.version=3.12 \
                                    -Dsonar.exclusions=**/venv/**,**/.venv/**,**/node_modules/** \
                                    -Dsonar.python.coverage.reportPaths=reports/coverage.xml \
                                    -Dsonar.python.xunit.reportPath=reports/junit.xml
                            """
                        }
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([robotId: "${params.robotId}",
                                           jobName: "${env.SERVICE_NAME}",
                                           reason : "【${env.STAGE_NAME}】失败！"])
                        }
                    }
                }
            }
            stage('Deploy to Nexus') {
                agent {
                    docker {
                        image "${params.buildImage}"
                        args "${env.BUILD_ARGS}"
                        reuseNode true
                    }
                }
                steps {
                    script {
                        // 步骤 6: 使用 curl 上传文件到 Nexus
                        withCredentials([usernamePassword(
                                credentialsId: "${env.NEXUS_CREDENTIALS_ID}",
                                usernameVariable: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_PASS')]) {

                            sh """
                                # 打包产物
                                python3 setup.py sdist bdist_wheel
                                python3 -m twine upload --repository-url ${NEXUS_URL} --username \$NEXUS_USER --password \$NEXUS_PASS dist/*
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
        } //stages
//        post {
//            always { cleanWs() }
//        }
    }
}