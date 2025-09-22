package xyz.dev.ops.adapter

/**
 * SonarQube JUnit XML 报告工具类
 *
 * 功能：
 * - 提供统一的 JUnit XML 格式测试报告生成功能
 * - 支持多种测试框架的测试结果转换
 * - 生成 SonarQube 兼容的测试执行报告
 *
 * 使用示例：
 * def testData = [totalTests: 10, passedTests: 8, failedTests: 2, ...]
 * SonarQubeJUnitXmlReportUtils.generateJUnitXmlReport(testData, 'reports/test-results.xml')
 */
class SonarQubeJUnitXmlReportUtils {

    /**
     * 生成 JUnit XML 格式的测试报告
     *
     * @param testData 测试数据，包含以下字段：
     *   - totalTests: 总测试数
     *   - passedTests: 通过测试数
     *   - failedTests: 失败测试数
     *   - errorTests: 错误测试数
     *   - failedTestNames: 失败测试名称列表
     *   - passedTestNames: 通过测试名称列表
     * @param outputXmlPath 输出的 JUnit XML 文件路径
     */
    static def createReport(def testData, String outputXmlPath) {
        def xml = new StringBuilder()

        // XML 头部
        xml.append('<?xml version="1.0" encoding="UTF-8"?>\n')
        xml.append("<testsuite name=\"protobuf-c-tests\" tests=\"${testData.totalTests}\" failures=\"${testData.failedTests}\" errors=\"${testData.errorTests}\" time=\"0.0\">\n")

        // 添加失败的测试用例
        testData.failedTestNames.each { testName ->
            xml.append("  <testcase name=\"${testName}\" classname=\"protobuf-c\" time=\"0.0\">\n")
            xml.append("    <failure message=\"Test failed with exit status 139 (Segmentation fault)\" type=\"runtime_error\"/>\n")
            xml.append("  </testcase>\n")
        }

        // 添加通过的测试用例
        testData.passedTestNames.each { testName ->
            xml.append("  <testcase name=\"${testName}\" classname=\"protobuf-c\" time=\"0.0\"/>\n")
        }

        // XML 尾部
        xml.append('</testsuite>\n')

        // 写入文件
        new File(outputXmlPath).text = xml.toString()
    }

    /**
     * 生成 SonarQube testExecutions XML 格式的测试报告
     *
     * @param testSuiteData 测试套件数据，包含以下字段：
     *   - name: 测试套件名称
     *   - tests: 总测试数
     *   - skipped: 跳过测试数
     *   - failures: 失败测试数
     *   - errors: 错误测试数
     *   - time: 总执行时间
     *   - testcases: 测试用例列表，每个测试用例包含：
     *     - name: 测试名称
     *     - classname: 类名
     *     - time: 执行时间
     *     - failure: 失败信息（可选）
     *     - error: 错误信息（可选）
     *     - skipped: 跳过信息（可选）
     * @param outputXmlPath 输出的 testExecutions XML 文件路径
     */
    static def createExecutionsXmlReport(def testSuiteData, String outputXmlPath) {
        def xml = new StringBuilder()

        // XML 头部
        xml.append('<?xml version="1.0" encoding="UTF-8"?>\n')
        xml.append('<testExecutions version="1">\n')

        // 按文件分组测试用例
        def fileGroups = [:]
        testSuiteData.testcases.each { testcase ->
            def fileName = testcase.testFile ?: "go_test.go" // 使用测试文件信息，默认为 go_test.go
            if (!fileGroups.containsKey(fileName)) {
                fileGroups[fileName] = []
            }
            fileGroups[fileName] << testcase
        }

        // 为每个文件生成测试用例
        fileGroups.each { fileName, testCases ->
            xml.append("  <file path=\"${fileName}\">\n")

            testCases.each { testcase ->
                def duration = (testcase.time * 1000) as int // 转换为毫秒
                xml.append("    <testCase name=\"${testcase.name}\" duration=\"${duration}\"")

                // 添加状态信息
                if (testcase.failure) {
                    xml.append(" status=\"FAILED\"")
                } else if (testcase.error) {
                    xml.append(" status=\"ERROR\"")
                } else if (testcase.skipped) {
                    xml.append(" status=\"SKIPPED\"")
                } else {
                    xml.append(" status=\"PASSED\"")
                }

                xml.append("/>\n")
            }

            xml.append("  </file>\n")
        }

        xml.append('</testExecutions>\n')

        // 写入文件
        new File(outputXmlPath).text = xml.toString()
    }

    /**
     * 创建空的 JUnit XML 报告
     *
     * @param outputXmlPath 输出的 XML 文件路径
     */
    static def createEmptyJUnitXmlReport(String outputXmlPath) {
        def xml = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="protobuf-c-tests" tests="0" failures="0" errors="0" time="0.0">
</testsuite>'''

        // 创建输出目录
        def outputDir = new File(outputXmlPath).parent
        if (outputDir && !new File(outputDir).exists()) {
            new File(outputDir).mkdirs()
        }

        new File(outputXmlPath).text = xml
    }

    /**
     * 创建空的 testExecutions XML 报告
     *
     * @param outputXmlPath 输出的 XML 文件路径
     */
    static def createEmptyTestExecutionsXmlReport(String outputXmlPath) {
        def xml = '''<?xml version="1.0" encoding="UTF-8"?>
<testExecutions version="1">
</testExecutions>'''

        // 创建输出目录
        def output = new File(outputXmlPath)
        output.getParentFile().mkdirs()

        output.write(xml)
    }
}

