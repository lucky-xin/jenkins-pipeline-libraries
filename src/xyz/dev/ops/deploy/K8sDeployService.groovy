package xyz.dev.ops.deploy

import xyz.dev.ops.notify.DingTalk

class K8sDeployService implements Serializable {
    def script
    DingTalk dingTalk

    K8sDeployService(script) {
        this.script = script
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
        // 使用k8s密钥文件连接k8s集群
        script.withKubeConfig([credentialsId: "k8s-config",
                               serverUrl    : params.k8sServerUrl
        ]) {
            // 使用 configFile 插件，获取配置文件模板，创建 Kubernetes 部署文件 deployment.yaml
            script.configFileProvider([script.configFile(
                    fileId: params.k8sDeploymentFileId,
                    targetLocation: "deployment.tpl"
            )]) {
                script.script {
                    script.sh "cat deployment.tpl"
                    def deployTemplate = script.readFile(encoding: "UTF-8", file: "deployment.tpl")
                    def deployment = deployTemplate
                            .replace('${APP_NAME}', params.serviceName)
                            .replace('${NAMESPACE}', params.namespace)
                            .replace('${DOCKER_REPOSITORY}', params.dockerRepository)
                            .replace('${IMAGE_NAME}', params.imageName)
                            .replace('${VERSION}', params.version)
                    script.writeFile(encoding: 'UTF-8', file: './deploy.yaml', text: deployment)
                }

                // 输出新创建的部署 yaml 文件内容
                script.sh "cat deploy.yaml"
                // 执行 Kuberctl 命令进行部署操作
//                script.sh "kubectl apply -n ${params.namespace} -f deploy.yaml"
            }
        }
    }
}
