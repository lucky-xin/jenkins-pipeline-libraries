#!/usr/bin/env groovy

// 测试缩进修复后的 IngressConfigTool
import xyz.dev.ops.deploy.K8sDeployConfigTool

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
        def config = configs[0]
        def fileId = config.fileId
        def targetLocation = config.targetLocation
        
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
        
        def target = new File(targetLocation)
        target.text = source.text
        
        try {
            return closure()
        } finally {
            if (target.exists()) {
                target.delete()
            }
        }
    }
    
    def configFile(Map params) {
        return params
    }
}

println "🚀 测试缩进修复后的 IngressConfigTool"

// 创建模拟的 Jenkins script 对象
def mockScript = new MockJenkinsScript()

// 创建工具实例
def ingressTool = new K8sDeployConfigTool(mockScript)

// 测试配置数据
def backendServices = [
    [svc_name: "tb-core", url: "/svc/tb-core"],
    [svc_name: "user-service", url: "/svc/user"]
]

println "📋 测试配置："
backendServices.each { service ->
    println "  - ${service.svc_name}: ${service.url}"
}

// 测试生成 nginx 配置（检查缩进）
println "\n🔧 测试生成 nginx 配置（检查缩进）"
def nginxConfig = K8sDeployConfigTool.generateNginxConfigSnippet(backendServices)
println "生成的 nginx 配置："
println "=" * 50
println nginxConfig
println "=" * 50

// 检查缩进是否正确
def lines = nginxConfig.split('\n')
def correctIndent = true
lines.each { line ->
    if (line.trim() && !line.startsWith('      ')) {
        println "❌ 缩进错误: '${line}'"
        correctIndent = false
    }
}

if (correctIndent) {
    println "✅ nginx 配置缩进正确"
} else {
    println "❌ nginx 配置缩进有误"
}

// 测试修改模板文件
println "\n🔧 测试修改模板文件"
try {
    def modifiedContent = ingressTool.create(
        'resources/template/deployment-front-end-template.yaml',
        backendServices,
        'test-namespace',
        'test-app',
        'v1.0.0',
        'test-registry',
        'test-image'
    )
    
    // 提取 nginx configuration-snippet 部分
    def configSnippetMatch = modifiedContent =~ /(?s)nginx\.ingress\.kubernetes\.io\/configuration-snippet: \|(.*?)nginx\.ingress\.kubernetes\.io\/server-snippet/
    if (configSnippetMatch) {
        def configSnippet = configSnippetMatch[0][1]
        println "nginx configuration-snippet 内容："
        println "=" * 50
        println configSnippet
        println "=" * 50
        
        // 检查缩进
        def configLines = configSnippet.split('\n')
        def configCorrectIndent = true
        configLines.each { line ->
            if (line.trim() && !line.startsWith('      ')) {
                println "❌ 配置缩进错误: '${line}'"
                configCorrectIndent = false
            }
        }
        
        if (configCorrectIndent) {
            println "✅ nginx configuration-snippet 缩进正确"
        } else {
            println "❌ nginx configuration-snippet 缩进有误"
        }
    } else {
        println "❌ 未找到 nginx configuration-snippet"
    }
    
} catch (Exception e) {
    println "❌ 模板文件处理失败: ${e.message}"
    e.printStackTrace()
}

println "\n✨ 测试完成！"




