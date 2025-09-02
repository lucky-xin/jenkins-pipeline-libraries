package xyz.dev.ops.deploy

import xyz.dev.ops.notify.DingTalk

class K8sDeployService implements Serializable {
    DingTalk dingTalk

    K8sDeployService() {
        this.dingTalk = new DingTalk()
    }

    def deploy(Map<String, Object> config) {
        // 设置默认值
        def params = [
                robotId               : config.robotId ?: '',
                serviceName           : config.serviceName ?: '',
                namespace             : config.namespace ?: '',
                dockerRepository      : config.dockerRepository ?: '',
                imageName             : config.imageName ?: '',
                version               : config.version ?: '',
                k8sServerUrl          : config.k8sServerUrl ?: "https://kubernetes.default.svc.cluster.local",
                k8sDeployImage        : config.k8sDeployImage ?: "bitnami/kubectl:latest",
                k8sDeployContainerArgs: config.k8sDeployContainerArgs ?: "-u root:root --entrypoint \"\"",
                k8sDeploymentFileId   : config.k8sDeploymentFileId ?: 'deployment-micro-svc-template'
        ]

        // 直接执行部署逻辑，不包装在 stage 中
        // 因为调用方已经在 stage 中调用了这个方法
        withKubeConfig([credentialsId: "jenkins-k8s-config",
                        serverUrl    : params.k8sServerUrl]) {
            // 使用 configFile 插件，创建 Kubernetes 部署文件 deployment.yaml
            configFileProvider([script.configFile(
                    fileId: params.k8sDeploymentFileId,
                    targetLocation: "deployment.tpl")
            ]) {
                script {
                    sh "cat deployment.tpl"
                    def deployTemplate = readFile(encoding: "UTF-8", file: "deployment.tpl")
                    def deployment = deployTemplate
                            .replaceAll("\\{APP_NAME\\}", params.serviceName)
                            .replaceAll("\\{NAMESPACE\\}", params.namespace)
                            .replaceAll("\\{DOCKER_REPOSITORY\\}", params.dockerRepository)
                            .replaceAll("\\{IMAGE_NAME\\}", params.imageName)
                            .replaceAll("\\{VERSION\\}", params.version)
                    writeFile(encoding: 'UTF-8', file: './deploy.yaml', text: deployment)
                }

                // 输出新创建的部署 yaml 文件内容
                sh "cat deploy.yaml"
                // 执行 Kuberctl 命令进行部署操作
                sh "kubectl apply -n ${params.namespace} -f deploy.yaml"
            }
        }
    }
}
