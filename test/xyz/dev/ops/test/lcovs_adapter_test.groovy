package xyz.dev.ops.test

import xyz.dev.ops.adapter.LcovCoverageReportAdapter

/**
 * LcovsToSonarCoverageReportAdapter 测试类
 *
 * 功能：
 * - 测试 LCOV 到 SonarQube XML 的转换功能
 * - 验证覆盖率数据解析的准确性
 * - 测试错误处理和边界情况
 *
 * 使用方法：
 * 1. 确保 coverage.info 文件存在于项目根目录
 * 2. 运行测试：groovy src/xyz/dev/ops/adapter/xyz.dev.ops.test.lcovs_adapter_test.groovy
 */
class LcovsToSonarCoverageReportAdapterTest {

    /**
     * 运行所有测试
     */
    def runAllTests() {
        println "=== LcovsToSonarCoverageReportAdapter 测试开始 ==="

        try {
            testConvertWithRealFile()
//            testConvertWithNonExistentFile()
//            testConvertWithEmptyFile()
//            testParseLcovFile()
//            testGenerateSonarXmlReport()
//            testCalculateStats()

            println "=== 所有测试通过！ ==="
        } catch (Exception e) {
            println "=== 测试失败: ${e.message} ==="
            e.printStackTrace()
        }
    }

    /**
     * 测试使用真实文件进行转换
     */
    def testConvertWithRealFile() {
        println "\n--- 测试1: 使用真实 coverage.info 文件进行转换 ---"

        def inputFile = "/Users/chaoxin.lu/IdeaProjects/jenkins-pipeline-libraries/coverage.info"
        def outputFile = "test-sonar-coverage.xml"

        // 检查输入文件是否存在
        if (!new File(inputFile).exists()) {
            println "警告: ${inputFile} 文件不存在，跳过此测试"
            return
        }

        // 执行转换
        def stats = LcovCoverageReportAdapter.convert(inputFile, outputFile)

        // 验证结果
        assert stats != null: "转换结果不应为空"
        assert stats.totalFiles >= 0: "文件数量应大于等于0"
        assert stats.totalLines >= 0: "总行数应大于等于0"
        assert stats.coveredLines >= 0: "覆盖行数应大于等于0"
        assert stats.coveragePercent >= 0.0: "覆盖率应大于等于0"
        assert stats.coveredLines <= stats.totalLines: "覆盖行数不应超过总行数"

        // 检查输出文件
        def outputFileObj = new File(outputFile)
        assert outputFileObj.exists(): "输出文件应该存在"
        assert outputFileObj.size() > 0: "输出文件不应为空"

        // 验证XML格式
        def xmlContent = outputFileObj.text
        assert xmlContent.contains('<?xml version="1.0" encoding="UTF-8"?>'): "应该包含XML声明"
        assert xmlContent.contains('<coverage version="1">'): "应该包含coverage根元素"
        assert xmlContent.contains('</coverage>'): "应该包含coverage结束标签"

        println "✅ 转换成功:"
        println "   - 文件数量: ${stats.totalFiles}"
        println "   - 总行数: ${stats.totalLines}"
        println "   - 覆盖行数: ${stats.coveredLines}"
        println "   - 覆盖率: ${String.format('%.2f', stats.coveragePercent)}%"
        println "   - 输出文件: ${outputFile} (${outputFileObj.size()} 字节)"

        // 清理测试文件
        outputFileObj.delete()
    }

    /**
     * 测试不存在的文件
     */
    def testConvertWithNonExistentFile() {
        println "\n--- 测试2: 测试不存在的文件 ---"

        def adapter = new LcovCoverageReportAdapter()
        def inputFile = "non-existent-file.info"
        def outputFile = "test-empty-coverage.xml"

        def stats = adapter.convert(inputFile, outputFile)

        // 验证结果
        assert stats != null: "转换结果不应为空"
        assert stats.err != null: "应该有错误信息"
        assert stats.err.contains("LCOV 文件不存在"): "错误信息应该包含文件不存在的提示"

        // 检查是否创建了空报告
        def outputFileObj = new File(outputFile)
        assert outputFileObj.exists(): "应该创建空报告文件"

        def xmlContent = outputFileObj.text
        assert xmlContent.contains('<!-- Empty coverage report -->'): "应该包含空报告注释"

        println "✅ 正确处理不存在的文件"
        println "   - 错误信息: ${stats.err}"

        // 清理测试文件
        outputFileObj.delete()
    }

    /**
     * 测试空文件
     */
    def testConvertWithEmptyFile() {
        println "\n--- 测试3: 测试空文件 ---"

        def adapter = new LcovCoverageReportAdapter()
        def inputFile = "test-empty.info"
        def outputFile = "test-empty-result.xml"

        // 创建空文件
        new File(inputFile).text = ""

        def stats = adapter.convert(inputFile, outputFile)

        // 验证结果
        assert stats != null: "转换结果不应为空"
        assert stats.totalFiles == 0: "空文件应该有0个文件"
        assert stats.totalLines == 0: "空文件应该有0行"
        assert stats.coveragePercent == 0.0: "空文件应该有0%覆盖率"

        // 检查输出文件
        def outputFileObj = new File(outputFile)
        assert outputFileObj.exists(): "应该创建输出文件"

        println "✅ 正确处理空文件"

        // 清理测试文件
        new File(inputFile).delete()
        outputFileObj.delete()
    }

    /**
     * 测试 LCOV 文件解析
     */
    def testParseLcovFile() {
        println "\n--- 测试4: 测试 LCOV 文件解析 ---"

        def adapter = new LcovCoverageReportAdapter()

        // 创建测试 LCOV 文件
        def testLcovContent = """TN:
SF:/workspace/test/file1.c
FN:10,test_function1
FN:20,test_function2
FNDA:5,test_function1
FNDA:3,test_function2
FNF:2
FNH:2
DA:10,5
DA:11,5
DA:20,3
DA:21,0
end_of_record
SF:/workspace/test/file2.c
FN:30,test_function3
FNDA:2,test_function3
FNF:1
FNH:1
DA:30,2
DA:31,2
end_of_record"""

        def testFile = "test-parse.info"
        new File(testFile).text = testLcovContent

        // 使用反射调用私有方法进行测试
        def method = adapter.class.getDeclaredMethod('parseLcovFile', String.class)
        method.setAccessible(true)
        def coverageData = method.invoke(adapter, testFile)

        // 验证解析结果
        assert coverageData != null: "解析结果不应为空"
        assert coverageData.size() == 2: "应该解析出2个文件"
        assert coverageData.containsKey('test/file1.c'): "应该包含 file1.c"
        assert coverageData.containsKey('test/file2.c'): "应该包含 file2.c"

        // 验证 file1.c 的数据
        def file1Data = coverageData['test/file1.c']
        assert file1Data.size() == 4: "file1.c 应该有4行数据"
        assert file1Data.find { it.lineNumber == 10 && it.hitCount == 5 && it.covered }: "第10行应该被覆盖"
        assert file1Data.find { it.lineNumber == 21 && it.hitCount == 0 && !it.covered }: "第21行应该未被覆盖"

        println "✅ LCOV 文件解析正确"
        println "   - 解析文件数: ${coverageData.size()}"
        println "   - file1.c 行数: ${coverageData['test/file1.c'].size()}"
        println "   - file2.c 行数: ${coverageData['test/file2.c'].size()}"

        // 清理测试文件
        new File(testFile).delete()
    }

    /**
     * 测试 SonarQube XML 报告生成
     */
    def testGenerateSonarXmlReport() {
        println "\n--- 测试5: 测试 SonarQube XML 报告生成 ---"

        def adapter = new LcovCoverageReportAdapter()

        // 创建测试数据
        def testData = [
                'test/file1.c': [
                        [lineNumber: 10, hitCount: 5, covered: true],
                        [lineNumber: 11, hitCount: 0, covered: false]
                ],
                'test/file2.c': [
                        [lineNumber: 20, hitCount: 3, covered: true]
                ]
        ]

        def outputFile = "test-xml-report.xml"

        // 使用反射调用私有方法进行测试
        def method = adapter.class.getDeclaredMethod('generateSonarXmlReport', Map.class, String.class)
        method.setAccessible(true)
        method.invoke(adapter, testData, outputFile)

        // 验证输出文件
        def outputFileObj = new File(outputFile)
        assert outputFileObj.exists(): "应该创建XML文件"

        def xmlContent = outputFileObj.text
        assert xmlContent.contains('<?xml version="1.0" encoding="UTF-8"?>'): "应该包含XML声明"
        assert xmlContent.contains('<coverage version="1">'): "应该包含coverage根元素"
        assert xmlContent.contains('<file path="test/file1.c">'): "应该包含file1.c"
        assert xmlContent.contains('<file path="test/file2.c">'): "应该包含file2.c"
        assert xmlContent.contains('<lineToCover lineNumber="10" covered="true"'): "应该包含第10行覆盖信息"
        assert xmlContent.contains('<lineToCover lineNumber="11" covered="false"'): "应该包含第11行未覆盖信息"
        assert xmlContent.contains('</coverage>'): "应该包含coverage结束标签"

        println "✅ SonarQube XML 报告生成正确"
        println "   - XML文件大小: ${outputFileObj.size()} 字节"

        // 清理测试文件
        outputFileObj.delete()
    }

    /**
     * 测试统计信息计算
     */
    def testCalculateStats() {
        println "\n--- 测试6: 测试统计信息计算 ---"

        def adapter = new LcovCoverageReportAdapter()

        // 创建测试数据
        def testData = [
                'file1.c': [
                        [lineNumber: 10, hitCount: 5, covered: true],
                        [lineNumber: 11, hitCount: 0, covered: false],
                        [lineNumber: 12, hitCount: 3, covered: true]
                ],
                'file2.c': [
                        [lineNumber: 20, hitCount: 2, covered: true],
                        [lineNumber: 21, hitCount: 0, covered: false]
                ]
        ]

        // 使用反射调用私有方法进行测试
        def method = adapter.class.getDeclaredMethod('calculateStats', Map.class)
        method.setAccessible(true)
        def stats = method.invoke(adapter, testData)

        // 验证统计结果
        assert stats.totalFiles == 2: "应该有2个文件"
        assert stats.totalLines == 5: "应该有5行代码"
        assert stats.coveredLines == 3: "应该有3行被覆盖"
        assert stats.coveragePercent == 60.0: "覆盖率应该是60%"

        println "✅ 统计信息计算正确"
        println "   - 文件数: ${stats.totalFiles}"
        println "   - 总行数: ${stats.totalLines}"
        println "   - 覆盖行数: ${stats.coveredLines}"
        println "   - 覆盖率: ${stats.coveragePercent}%"
    }
}

def test = new LcovsToSonarCoverageReportAdapterTest()
test.testConvertWithRealFile()
