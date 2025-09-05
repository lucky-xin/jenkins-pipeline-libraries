package xyz.dev.ops.deploy

import java.util.regex.Matcher

/**
 * Ingress 配置工具类
 *
 * 用途：读取和修改 Kubernetes Ingress 配置，特别是 nginx 相关的配置
 * 支持动态添加后端服务路由配置
 */
class IngressConfigTool implements Serializable {

    IngressConfigTool() {
    }

    /**
     * 读取模板文件并修改 Ingress 配置
     *
     * @param templateContent 模板文件内容
     * @param backendServices 后端服务配置列表，格式：[{svc_name: "tb-core", url: "/svc/tb-core"}]
     * @param serviceName 必填，服务名（用于模板占位符 ${APP_NAME}）
     * @param namespace 必填，命名空间（${NAMESPACE}）
     * @param dockerRepository 必填，镜像仓库地址（${DOCKER_REPOSITORY}）
     * @param imageName 必填，镜像名（${IMAGE_NAME}）
     * @param version 必填，镜像版本（${VERSION}）
     * @return 修改后的 YAML 内容
     */
    static def modifyIngressConfig(Map<String, Object> config) {
        def params = [
                templateContent : config.templateContent ?: '',
                serviceName     : config.serviceName ?: '',
                namespace       : config.namespace ?: '',
                dockerRepository: config.dockerRepository ?: '',
                imageName       : config.imageName ?: '',
                version         : config.version ?: '',
                backendServices : config.backendServices ?: Collections.emptyList(),
        ]
        // 关键参数校验，提前失败以便快速定位问题
        assert params.templateContent: 'templateContent 不能为空'
        assert params.namespace: 'namespace 不能为空'
        assert params.dockerRepository: 'dockerRepository 不能为空'
        assert params.imageName: 'imageName 不能为空'
        assert params.version: 'version 不能为空'

        // 替换模板中的占位符
        String modifiedContent = params.templateContent
                .replace('${APP_NAME}', params.appName)
                .replace('${NAMESPACE}', params.namespace)
                .replace('${VERSION}', params.version)
                .replace('${DOCKER_REPOSITORY}', params.dockerRepository)
                .replace('${IMAGE_NAME}', params.imageName)

        // 修改 nginx configuration-snippet
        modifiedContent = modifyNginxConfigSnippet(modifiedContent, params.backendServices as List<Map<String, String>>)

        // 修改 Ingress paths
        modifiedContent = modifyIngressPaths(modifiedContent, params.backendServices as List<Map<String, String>>)

        return modifiedContent
    }

    /**
     * 修改 nginx configuration-snippet
     */
    private static String modifyNginxConfigSnippet(String content, List<Map<String, String>> backendServices) {
        def nginxConfigSnippet = generateNginxConfigSnippet(backendServices)

        // 查找并替换 nginx.ingress.kubernetes.io/configuration-snippet
        def configSnippetPattern = /(?s)nginx\.ingress\.kubernetes\.io\/configuration-snippet: \|(.*?)nginx\.ingress\.kubernetes\.io\/server-snippet/
        def configSnippetReplacement = Matcher.quoteReplacement("""nginx.ingress.kubernetes.io/configuration-snippet: |
${nginxConfigSnippet}    nginx.ingress.kubernetes.io/server-snippet""")

        return content.replaceAll(configSnippetPattern, configSnippetReplacement)
    }

    /**
     * 修改 Ingress paths
     */
    private static String modifyIngressPaths(String content, List<Map<String, String>> backendServices) {
        def ingressPaths = generateIngressPaths(backendServices)

        // 查找并替换 Ingress paths 部分
        def pathsPattern = /(?s) {8}paths:(.*?)(?=\Z)/
        def pathsReplacement = Matcher.quoteReplacement("""        paths:
${ingressPaths}""")

        return content.replaceAll(pathsPattern, pathsReplacement)
    }

    /**
     * 生成 nginx configuration-snippet 内容
     */
    static String generateNginxConfigSnippet(List<Map<String, String>> backendServices) {
        def sb = new StringBuilder()
        sb.append("      # 定义服务识别变量\n")
        sb.append("      set \$service_name \"\";\n\n")
        sb.append("      # 识别服务路径\n")

        backendServices.each { service ->
            def svcName = service.svc_name
            def url = service.url
            sb.append("      if (\$request_uri ~* \"^${url}/\") {\n")
            sb.append("        set \$service_name \"${svcName}\";\n")
            sb.append("      }\n")
        }

        sb.append("\n      # 服务路由处理\n")
        backendServices.each { service ->
            def svcName = service.svc_name
            def url = service.url
            sb.append("      if (\$service_name = ${svcName}) {\n")
            sb.append("        # ${svcName}微服务\n")
            sb.append("        rewrite \"^${url}/(.*)\" /\$1 break;  # ${url}/xxx → /xxx\n")
            sb.append("      }\n")
        }

        return sb.toString()
    }

    /**
     * 生成 Ingress paths 内容
     */
    static String generateIngressPaths(List<Map<String, String>> backendServices) {
        def sb = new StringBuilder()

        // 前端服务规则
        sb.append("          - path: /(.*)\n")
        sb.append("            pathType: Prefix\n")
        sb.append("            backend:\n")
        sb.append("              service:\n")
        sb.append("                name: fe-\${APP_NAME}\n")
        sb.append("                port:\n")
        sb.append("                  number: 8080\n")

        // 后端服务规则
        backendServices.each { service ->
            def url = service.url
            sb.append("\n          # 后端接口服务地址配置\n")
            sb.append("          - path: ${url}/(/|\$)(.*)\n")
            sb.append("            pathType: Prefix\n")
            sb.append("            backend:\n")
            sb.append("              service:\n")
            sb.append("                name: ${service.svc_name}\n")
            sb.append("                port:\n")
            sb.append("                  number: 21080\n")
        }

        return sb.toString()
    }

    /**
     * 从模板文件路径读取并修改配置
     *
     * @param templateFileId Config File Provider 中的文件ID
     * @param backendServices 后端服务配置
     * @param namespace k8s命名空间（用于替换 ${NAMESPACE}）
     * @param appName 前端服务名称（用于替换 ${APP_NAME}）
     * @param version 版本号（用于替换 ${VERSION}）
     * @param dockerRepository 镜像仓库（用于替换 ${DOCKER_REPOSITORY}）
     * @param imageName 镜像名称（用于替换 ${IMAGE_NAME}）
     * @return 修改后的 YAML 内容
     */
    def modifyIngressFromTemplate(String templateFileId,
                                  List<Map<String, String>> backendServices,
                                  String namespace,
                                  String appName,
                                  String version,
                                  String dockerRepository,
                                  String imageName) {
        def templateContent = ""

        // 使用 configFile 插件读取模板
        script.configFileProvider([script.configFile(
                fileId: templateFileId,
                targetLocation: "ingress-template.tpl"
        )]) {
            templateContent = script.readFile(encoding: "UTF-8", file: "ingress-template.tpl")
        }

        // 替换模板中的占位符
        def modifiedContent = templateContent
                .replace('${APP_NAME}', appName)
                .replace('${NAMESPACE}', namespace)
                .replace('${VERSION}', version)
                .replace('${DOCKER_REPOSITORY}', dockerRepository)
                .replace('${IMAGE_NAME}', imageName)
        // 修改 nginx configuration-snippet
        modifiedContent = modifyNginxConfigSnippet(modifiedContent, backendServices)

        // 修改 Ingress paths
        modifiedContent = modifyIngressPaths(modifiedContent, backendServices)

        return modifiedContent
    }
}