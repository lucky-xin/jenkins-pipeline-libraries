package xyz.dev.ops.test

import xyz.dev.ops.adapter.ExecutionsReportAdapter

/**
 * 测试脚本用于验证 ExecutionsReportAdapter 的 convert 方法
 * 将 test-result.json 转换为 testExecutions XML 格式
 * 
 * 功能：
 * - 测试 JSON 到 XML 的转换功能
 * - 验证生成的 XML 文件格式正确性
 * - 统计转换的测试用例数量
 * - 验证测试用例的基本结构
 * 
 * 运行方式：
 * groovy -cp src xyz.dev.ops.test.test_executions_report_adapter.groovy
 * 
 * 注意：此脚本需要在项目根目录下运行，以便正确解析类路径
 */

println "=== 测试 ExecutionsReportAdapter.convert 方法 ==="

// 创建输出目录
def reportsDir = new File("reports")
reportsDir.mkdirs()

// 测试数据文件路径
def testResultJsonPath = "resources/test-results.json"
def outputXmlPath = "reports/test-results.xml"

println "\n--- 开始转换测试 ---"
println "输入文件: ${testResultJsonPath}"
println "输出文件: ${outputXmlPath}"

try {
    // 检查输入文件是否存在
    def inputFile = new File(testResultJsonPath)
    if (!inputFile.exists()) {
        throw new FileNotFoundException("测试数据文件不存在: ${testResultJsonPath}")
    }
    
    println "✓ 输入文件存在，开始转换..."
    
    // 调用 convert 方法进行转换
    def result = ExecutionsReportAdapter.convert(testResultJsonPath, outputXmlPath)
    
    println "✓ 转换完成"
    println "转换结果:"
    println "  总测试用例数: ${result.total}"
    println "  错误信息: ${result.err ?: '无'}"
    
    // 验证输出文件是否生成
    def outputFile = new File(outputXmlPath)
    if (!outputFile.exists()) {
        throw new FileNotFoundException("输出文件未生成: ${outputXmlPath}")
    }
    
    println "✓ 输出文件已生成: ${outputFile.absolutePath}"
    println "文件大小: ${outputFile.size()} 字节"
    
    // 验证 XML 文件格式
    def xmlContent = outputFile.text
    if (!xmlContent.contains('<?xml version="1.0" encoding="UTF-8"?>')) {
        throw new RuntimeException("生成的 XML 文件格式不正确：缺少 XML 声明")
    }
    
    if (!xmlContent.contains('<testExecutions version="1">')) {
        throw new RuntimeException("生成的 XML 文件格式不正确：缺少 testExecutions 根元素")
    }
    
    if (!xmlContent.contains('</testExecutions>')) {
        throw new RuntimeException("生成的 XML 文件格式不正确：缺少 testExecutions 结束标签")
    }
    
    println "✓ XML 文件格式验证通过"
    
    // 统计测试用例数量
    def testCaseCount = xmlContent.split('<testCase').length - 1
    println "✓ XML 中包含 ${testCaseCount} 个测试用例"
    
    // 验证测试用例的基本结构
    if (testCaseCount > 0) {
        if (!xmlContent.contains('<file path=')) {
            throw new RuntimeException("生成的 XML 文件格式不正确：缺少 file 元素")
        }
        
        if (!xmlContent.contains('name=') || !xmlContent.contains('duration=')) {
            throw new RuntimeException("生成的 XML 文件格式不正确：测试用例缺少必要属性")
        }
        
        println "✓ 测试用例结构验证通过"
    }
    
    // 显示部分 XML 内容用于验证
    println "\n--- XML 文件内容预览（前 500 字符）---"
    println xmlContent.substring(0, Math.min(500, xmlContent.length()))
    if (xmlContent.length() > 500) {
        println "..."
    }
    
    println "\n✓ ExecutionsReportAdapter.convert 方法测试通过"
    
} catch (Exception e) {
    println "✗ ExecutionsReportAdapter.convert 方法测试失败: ${e.cause?.message ?: e.message}"
    e.printStackTrace()
}

println "\n=== 测试完成 ==="
println "输出文件保存在: ${new File(outputXmlPath).absolutePath}"
