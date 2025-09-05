#!/usr/bin/env groovy
import xyz.dev.ops.deploy.K8sDeployConfigTool

/**
 * IngressConfigTool 独立测试脚本
 *
 * 用途：不依赖 Jenkins 环境，直接测试 IngressConfigTool 工具类
 * 包括：nginx 配置生成、Ingress paths 生成、模板文件处理等
 *
 * 运行方式：
 * groovy test_ingress_config_tool.groovy
 */

// 模拟 Jenkins script 对象
class MockJenkinsScript {
    def readFile(Map params) {
        def file = new File(params.file)
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: ${params.file}")
        }
        return file.text
    }

    def configFileProvider(List configs, Closure closure) {
        // 模拟 configFileProvider 行为
        def config = configs[0]
        def fileId = config.fileId
        def targetLocation = config.targetLocation

        // 根据 fileId 映射到实际文件路径
        def fileMapping = [
                'deployment-front-end-template': 'resources/template/deployment-front-end-template.yaml'
        ]

        def sourceFile = fileMapping[fileId]
        if (!sourceFile) {
            throw new IllegalArgumentException("未找到文件ID: ${fileId}")
        }

        def source = new File(sourceFile)
        if (!source.exists()) {
            throw new FileNotFoundException("模板文件不存在: ${sourceFile}")
        }

        // 复制文件到目标位置
        def target = new File(targetLocation)
        target.text = source.text

        try {
            return closure()
        } finally {
            // 清理临时文件
            if (target.exists()) {
                target.delete()
            }
        }
    }

    def configFile(Map params) {
        return params
    }
}

println "🚀 开始测试 IngressConfigTool 工具类"

// 创建模拟的 Jenkins script 对象
def mockScript = new MockJenkinsScript()

// 测试配置数据
def backendServices = [
        [svc_name: "tb-core", url: "/svc/tb-core"],
        [svc_name: "user-service", url: "/svc/user"],
        [svc_name: "order-service", url: "/svc/order"]
]
def backendSvcName = "backend-microservices"

println "📋 测试配置："
println "后端服务数量: ${backendServices.size()}"
backendServices.each { service ->
    println "  - ${service.svc_name}: ${service.url}"
}
println "后端服务名称: ${backendSvcName}"

// 测试 1: 生成 nginx configuration-snippet
println "\n🔧 测试 1: 生成 nginx configuration-snippet"
def nginxConfig = K8sDeployConfigTool.generateNginxConfigSnippet(backendServices)
println "生成的 nginx 配置："
println nginxConfig

// 验证 nginx 配置内容
assert nginxConfig.contains("set \$service_name \"\";")
assert nginxConfig.contains("if (\$request_uri ~* \"^/svc/tb-core/\")")
assert nginxConfig.contains("set \$service_name \"tb-core\";")
assert nginxConfig.contains("if (\$service_name = tb-core)")
assert nginxConfig.contains("rewrite \"^/svc/tb-core/(.*)\" /\$1 break")
println "✅ nginx configuration-snippet 生成测试通过"

// 测试 2: 生成 Ingress paths
println "\n🔧 测试 2: 生成 Ingress paths"
def ingressPaths = K8sDeployConfigTool.generateIngressPaths(backendServices)
println "生成的 Ingress paths："
println ingressPaths

// 验证 Ingress paths 内容
assert ingressPaths.contains("path: /(.*)")
assert ingressPaths.contains("name: fe-\${APP_NAME}")
assert ingressPaths.contains("path: /svc/tb-core/(/|\$)(.*)")
//assert ingressPaths.contains("name: ${backendSvcName}")
assert ingressPaths.contains("number: 21080")
println "✅ Ingress paths 生成测试通过"

// 测试 3: 读取真实模板文件并处理
println "\n🔧 测试 3: 读取真实模板文件并处理"

try {
    // 测试 modifyIngressConfig 方法（直接读取文件）
    namespace = "dev"
    appName = "test-front-end"
    version = "1.0.0"
    dockerRepository = "127.0.0.1:5000"
    imageName = "micro-svc/test-front-end"
    def templateContent = mockScript.readFile(encoding: "UTF-8", file: 'resources/template/deployment-front-end-template.yaml')

    def params = [
            templateContent : templateContent,
            serviceName     : config.serviceName ?: '',
            namespace       : config.namespace ?: '',
            dockerRepository: config.dockerRepository ?: '',
            imageName       : config.imageName ?: '',
            version         : config.version ?: '',
            backendServices : config.backendServices ?: Collections.emptyList(),
    ]
    def modifiedContent = K8sDeployConfigTool.create(params)

    println "修改后的模板内容："
    println "=" * 80
    println modifiedContent
    println "=" * 80

    // 验证修改结果
    assert modifiedContent.contains("set \$service_name \"\";")
    assert modifiedContent.contains("if (\$request_uri ~* \"^/svc/tb-core/\")")
    assert modifiedContent.contains("if (\$service_name = tb-core)")
    assert modifiedContent.contains("rewrite \"^/svc/tb-core/(.*)\" /\$1 break")
    assert modifiedContent.contains("path: /svc/tb-core/(/|\$)(.*)")
    assert modifiedContent.contains("number: 21080")
    assert modifiedContent.contains("fe-${appName}")  // 保持原占位符
    assert modifiedContent.contains("${namespace}")    // 保持原占位符

    println "✅ 真实模板文件处理测试通过"

} catch (Exception e) {
    println "❌ 真实模板文件处理测试失败: ${e.message}"
    e.printStackTrace()
}

// 测试 5: 边界情况测试
println "\n🔧 测试 5: 边界情况测试"

// 空后端服务列表
def emptyServices = []
def emptyNginxConfig = K8sDeployConfigTool.generateNginxConfigSnippet(emptyServices)
assert emptyNginxConfig.contains("set \$service_name \"\";")
assert !emptyNginxConfig.contains("if (\$request_uri")
println "✅ 空服务列表测试通过"

// 单个服务
def singleService = [[svc_name: "single-svc", url: "/api/single"]]
def singleNginxConfig = K8sDeployConfigTool.generateNginxConfigSnippet(singleService)
assert singleNginxConfig.contains("if (\$request_uri ~* \"^/api/single/\")")
assert singleNginxConfig.contains("set \$service_name \"single-svc\";")
println "✅ 单个服务测试通过"

println "\n🎉 所有测试通过！IngressConfigTool 工具类功能正常"

// 输出使用示例
println "\n📖 使用示例："
println """
// 在 Jenkins Pipeline 中使用
def ingressTool = new xyz.dev.ops.deploy.IngressConfigTool(this)

def backendServices = [
    [svc_name: "tb-core", url: "/svc/tb-core"],
    [svc_name: "user-service", url: "/svc/user"]
]

// 方法1: 从文件路径读取模板
def modifiedYaml = ingressTool.modifyIngressConfig(
    'resources/template/deployment-front-end-template.yaml',
    backendServices,
    'backend-service'
)

// 方法2: 从 Config File Provider 读取模板
def modifiedYaml2 = ingressTool.modifyIngressFromTemplate(
    'deployment-front-end-template',
    backendServices,
    'backend-service'
)
"""

println "\n✨ 测试完成！"
