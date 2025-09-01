import xyz.dev.ops.notify.DingTalk

def call(String robotId,
         String baseImage = "alpine:latest",
         String buildImage = "golang:1.25",
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

            service_name = readMavenPom().getArtifactId()  //服务名称
            version = readMavenPom().getVersion()  //版本

            commit_id = "${GIT_COMMIT}".substring(0, 8)
            docker_tag = "${version}-${commit_id}" //docker镜像 tag 为区分环境,pre 前缀有v

            branch = "$env.BRANCH_NAME"
        }
        stages {
            stage("Go 构建") {
                agent {
                    docker {
                        image "${buildImage}"
                        args "${env.BUILD_ARGS}"
                        reuseNode true
                    }
                }

                steps {
                    script {
                        echo "starting build go......"
                        sh 'GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-w -s" main.go'
                    }
                }
                post {
                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
                }
            }
            stage("封装Docker") {
                steps {
                    script {
                        if (branch == "pre") {
                            docker_tag = "v${version}"
                        }
                        docker_img.BuildReq("${service_name}", "${docker_repository}", "${docker_tag}")
                    }
                }
                post {
                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
                }
            }
            stage("部署") {
                agent { label 'ops-kube' }
                steps {
                    script {
                        if (branch == "dev") {
                            deploy_svc.DeployReq_k8s("${docker_tag}", "${service_name}", "${docker_repository}", 'dev')
                        } else if (branch == "pre") {
                            deploy_svc.DeployReq_k8s("${docker_tag}", "${service_name}", "${docker_repository}", 'pre')
                        } else {
                            echo "bye"
                        }
                    }
                }
                post {
                    //发布消息给团队中所有的人
                    success { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
                    always { cleanWs() }
                }
            }
        } //stages

        post {
            always { cleanWs() }
        }
    } //pipeline
}

