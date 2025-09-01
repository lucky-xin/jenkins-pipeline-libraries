//import com.pistonint.DevOps.Deploy.zDeployService
//import com.pistonint.DevOps.Docker.GoProjectImage
//import com.pistonint.DevOps.Docker.zImage
//import com.pistonint.DevOps.Maven.zMvn
//import com.pistonint.DevOps.MsgSender.zDingMsg
//
//// vars/zGeneralMavenPipline.groovy
//// package org.devops
//
//
//def call(String robotId,
//         String baseBuilderImg = "reg.pistonint.com/pistonint-go-builder:1.20.1") {
//
//    def dingmsg = new zDingMsg()
//    def docker_img = new GoProjectImage()
//    def deploy_svc = new zDeployService()
//
//    pipeline {
//        agent any
//        options {
//            timestamps()
//            disableConcurrentBuilds()
//            // ansiColor('xterm')
//            timeout(time: 180, unit: 'MINUTES')
//            buildDiscarder(logRotator(numToKeepStr: '5'))
//        }
//        environment {
//            builder_docker_image = "${baseBuilderImg}" // Build镜像
//            builder_args = "-v $HOME/.cache/go-build/:/tmp/.cache/go-build/"  //本地仓库挂载
//
//            base_docker_image = ""  //docker基础镜像
//            docker_repository = "reg.pistonint.com" //镜像仓库地址
//
//            service_name = readMavenPom().getArtifactId()  //服务名称
//            version = readMavenPom().getVersion()  //版本
//
//            commit_id = "${GIT_COMMIT}".substring(0, 8)
//            docker_tag = "${version}-${commit_id}" //docker镜像 tag 为区分环境,pre 前缀有v
//
//            branch = "$env.BRANCH_NAME"
//        }
//        stages {
//            stage("构建") {
//                agent {
//                    docker {
//                        image "${builder_docker_image}"
//                        args "${builder_args}"
//                        reuseNode true
//                    }
//                }
//
//                steps {
//                    script {
//                        echo "starting build go......"
//                        sh 'GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-w -s" main.go'
//                    }
//                }
//                post {
//                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
//                }
//            }
//            stage("封装Docker") {
//                steps {
//                    script {
//                        if (branch == "pre") {
//                            docker_tag = "v${version}"
//                        }
//                        docker_img.BuildReq("${service_name}", "${docker_repository}", "${docker_tag}")
//                    }
//                }
//                post {
//                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
//                }
//            }
//            stage("部署") {
//                agent { label 'ops-kube' }
//                steps {
//                    script {
//                        if (branch == "dev") {
//                            deploy_svc.DeployReq_k8s("${docker_tag}", "${service_name}", "${docker_repository}", 'dev')
//                        } else if (branch == "pre") {
//                            deploy_svc.DeployReq_k8s("${docker_tag}", "${service_name}", "${docker_repository}", 'pre')
//                        } else {
//                            echo "bye"
//                        }
//                    }
//                }
//                post {
//                    //发布消息给团队中所有的人
//                    success { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
//                    failure { script { dingmsg.DingdingReq("${robotId}", "${service_name}") } }
//                    always { cleanWs() }
//                }
//            }
//        } //stages
//
//        post {
//            always { cleanWs() }
//        }
//    } //pipeline
//}
//
