package xyz.dev.ops.deploy

import xyz.dev.ops.notify.DingTalk

class K8sDeployService implements Serializable {
    def script
    def dingTalk

    K8sDeployService(script) {
        this.script = script
        this.dingTalk = new DingTalk()
    }

    def deploy(String robotId,
               String serviceName,
               String namespace,
               String dockerRepository,
               String imageName,
               String version,
               String k8sServerUrl = "https://kubernetes.default.svc.cluster.local",
               String k8sDeployImage = "bitnami/kubectl:latest",
               String k8sDeployContainerArgs = "-u root:root --entrypoint \"\"",
               String k8sDeploymentFileId = 'deployment-micro-svc-template') {

        script.stage('k8s部署') {
            script.agent {
                script.docker {
                    script.image k8sDeployImage
                    script.args k8sDeployContainerArgs
                }
            }

            script.steps {
                script.withKubeConfig([credentialsId: "jenkins-k8s-config",
                                       serverUrl    : k8sServerUrl]) {
                    // 使用 configFile 插件，创建 Kubernetes 部署文件 deployment.yaml
                    script.configFileProvider([script.configFile(
                            fileId: k8sDeploymentFileId,
                            targetLocation: "deployment.tpl")
                    ]) {
                        script.script {
                            script.sh "cat deployment.tpl"
                            def deployTemplate = script.readFile(encoding: "UTF-8", file: "deployment.tpl")
                            def deployment = deployTemplate
                                    .replaceAll("\\{APP_NAME\\}", serviceName)
                                    .replaceAll("\\{NAMESPACE\\}", namespace)
                                    .replaceAll("\\{DOCKER_REPOSITORY\\}", dockerRepository)
                                    .replaceAll("\\{IMAGE_NAME\\}", imageName)
                                    .replaceAll("\\{VERSION\\}", version)
                            script.writeFile(encoding: 'UTF-8', file: './deploy.yaml', text: deployment)
                        }

                        // 输出新创建的部署 yaml 文件内容
                        script.sh "cat deploy.yaml"
                        // 执行 Kuberctl 命令进行部署操作
                        script.sh "kubectl apply -n ${namespace} -f deploy.yaml"
                    }
                }
            }

            script.post {
                //发布消息给团队中所有的人
                script.failure {
                    script.script {
                        dingTalk.post(
                                robotId,
                                serviceName,
                                "【k8s部署】失败！"
                        )
                    }
                }
                script.success {
                    script.script {
                        dingTalk.post(robotId, serviceName)
                    }
                }
                script.always {
                    script.cleanWs()
                }
            }
        }
    }
}
