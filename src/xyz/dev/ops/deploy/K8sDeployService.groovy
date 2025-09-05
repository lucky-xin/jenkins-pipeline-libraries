package xyz.dev.ops.deploy

import xyz.dev.ops.notify.DingTalk

/**
 * Kubernetes 部署服务（Jenkins Shared Library）
 *
 * 用途：在流水线中根据模板渲染部署文件并执行 k8s 部署步骤。
 * 依赖：
 *  - Jenkins Credentials: kubeconfig（ID: k8s-config）
 *  - Jenkins Config File Provider: 部署模板文件（fileId 由调用方传入）
 */
class K8sDeployService implements Serializable {
    def script
    DingTalk dingTalk

    K8sDeployService(script) {
        this.script = script
        this.dingTalk = new DingTalk()
    }

    /**
     * 执行 k8s 部署
     *
     * @param config 参数表：
     *  robotId                可选，钉钉机器人ID
     *  serviceName            必填，服务名（用于模板占位符 ${APP_NAME}）
     *  namespace              必填，命名空间（${NAMESPACE}）
     *  dockerRepository       必填，镜像仓库地址（${DOCKER_REPOSITORY}）
     *  imageName              必填，镜像名（${IMAGE_NAME}）
     *  version                必填，镜像版本（${VERSION}）
     *  k8sServerUrl           可选，k8s API 地址，默认集群内地址
     *  k8sDeployImage         可选，kubectl 镜像，默认 bitnami/kubectl:latest
     *  k8sDeployContainerArgs 可选，kubectl 容器参数
     *  k8sDeploymentFileId    必填，Config File Provider 中的模板文件ID
     */
    def deploy(Map<String, Object> config) {
        // 设置默认值（允许调用方仅提供差异项）
        def params = [
                robotId               : config.robotId ?: '',
                serviceName           : config.serviceName ?: '',
                namespace             : config.namespace ?: '',
                dockerRepository      : config.dockerRepository ?: '',
                imageName             : config.imageName ?: '',
                version               : config.version ?: '',
                frontend       : config.frontend ?: false,
                backendServices: config.backendServices ?: Collections.emptyList(),
                k8sServerUrl          : config.k8sServerUrl ?: "https://kubernetes.default.svc.cluster.local",
                k8sDeployImage        : config.k8sDeployImage ?: "bitnami/kubectl:latest",
                k8sDeployContainerArgs: config.k8sDeployContainerArgs ?: "-u root:root --entrypoint \"\"",
                k8sDeploymentFileId   : config.k8sDeploymentFileId ?: 'deployment-micro-svc-template'
        ]
        // 关键参数校验，提前失败以便快速定位问题
        assert params.serviceName: 'serviceName 不能为空'
        assert params.namespace: 'namespace 不能为空'
        assert params.dockerRepository: 'dockerRepository 不能为空'
        assert params.imageName: 'imageName 不能为空'
        assert params.version: 'version 不能为空'
        assert params.k8sDeploymentFileId: 'k8sDeploymentFileId 不能为空'

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

                    params['templateContent'] = deployTemplate
                    def deployment = K8sDeployConfigTool.create(params)
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
