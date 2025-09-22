package xyz.dev.ops.adapter

/**
 * 单元测试报告到 SonarQube 报告转换适配器
 *
 * 功能：
 * - 解析 Autotools 的 test-suite.log 文件
 * - 生成 SonarQube 兼容的 JUnit XML 格式测试报告
 * - 提供测试统计信息
 *
 * 使用示例：
 * def stats = UnitTestReportToSonarReportAdapter.convert('test-suite.log', 'reports/test-results.xml')
 */
class LcovUnitTestReportAdapter {

    /**
     * 将 test-suite.log 转换为 SonarQube JUnit XML 格式
     *
     * @param testSuiteLogPath test-suite.log 文件路径
     * @param outputXmlPath 输出的 JUnit XML 文件路径
     * @return Map 包含转换结果的统计信息
     */
    static def convert(String testSuiteLogPath, String outputXmlPath) {
        def stats = [
                totalTests : 0,
                passedTests: 0,
                failedTests: 0,
                errorTests : 0,
                err        : ""
        ]

        try {
            // 检查输入文件是否存在
            if (!new File(testSuiteLogPath).exists()) {
                stats.err = "test-suite.log 文件不存在: ${testSuiteLogPath}"
                return stats
            }

            // 创建输出目录
            def outputDir = new File(outputXmlPath).parentFile
            if (outputDir && !outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 解析 test-suite.log 文件
            def testData = parseTestSuiteLog(testSuiteLogPath)

            // 生成 JUnit XML 报告
            SonarQubeJUnitXmlReportUtils.createReport(testData, outputXmlPath)

            // 计算统计信息
            stats = calculateStats(testData)

        } catch (Exception e) {
            stats.err = e.message
            SonarQubeJUnitXmlReportUtils.createEmptyJUnitXmlReport(outputXmlPath)
        }

        return stats
    }

    /**
     * 解析 test-suite.log 文件
     */
    private static def parseTestSuiteLog(String testSuiteLogPath) {
        def testData = [
                totalTests     : 0,
                passedTests    : 0,
                failedTests    : 0,
                errorTests     : 0,
                failedTestNames: [],
                passedTestNames: []
        ]

        def lines = new File(testSuiteLogPath).readLines()
        for (final def line in lines) {
            // 解析测试统计信息
            if (line.startsWith('# TOTAL:')) {
                testData.totalTests = extractNumber(line, '# TOTAL:')
            } else if (line.startsWith('# PASS:')) {
                testData.passedTests = extractNumber(line, '# PASS:')
            } else if (line.startsWith('# FAIL:')) {
                testData.failedTests = extractNumber(line, '# FAIL:')
            } else if (line.startsWith('# ERROR:')) {
                testData.errorTests = extractNumber(line, '# ERROR:')
            } else if (line.startsWith('FAIL:')) {
                // 提取失败的测试名称
                def testName = extractFailedTestName(line)
                if (testName) {
                    testData.failedTestNames << testName
                }
            }
        }

        // 查找通过的测试（通过查找 .log 文件）
        def testDir = new File(testSuiteLogPath).parent
        def logFiles = findLogFiles(testDir)
        for (final def logFile in logFiles) {
            if (isPassedTest(logFile as File)) {
                def testName = extractTestNameFromLogFile(logFile as File)
                if (testName && !testData.failedTestNames.contains(testName)) {
                    testData.passedTestNames << testName
                }
            }
        }

        return testData
    }

    /**
     * 从统计行中提取数字
     */
    private static def extractNumber(String line, String prefix) {
        try {
            def numberPart = line.substring(prefix.length()).trim()
            return Integer.parseInt(numberPart.split()[0])
        } catch (Exception e) {
            return 0
        }
    }

    /**
     * 提取失败的测试名称
     */
    private static def extractFailedTestName(String line) {
        try {
            // 格式: FAIL: t/test-name (exit status: 139)
            def match = line =~ /FAIL: t\/([^\s]+)/
            if (match) {
                return match[0][1]
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null
    }

    /**
     * 查找所有 .log 文件
     */
    private static def findLogFiles(String testDir) {
        def logFiles = []
        def testDirFile = new File(testDir)
        if (testDirFile.exists() && testDirFile.isDirectory()) {
            testDirFile.eachFileRecurse { file ->
                if (file.name.endsWith('.log')) {
                    logFiles << file
                }
            }
        }
        return logFiles
    }

    /**
     * 检查测试是否通过
     */
    private static def isPassedTest(File logFile) {
        try {
            return logFile.text.contains('PASS') && logFile.text.contains('exit status: 0')
        } catch (Exception e) {
            return false
        }
    }

    /**
     * 从日志文件名提取测试名称
     */
    private static def extractTestNameFromLogFile(File logFile) {
        return logFile.name.replace('.log', '')
    }

    /**
     * 计算统计信息
     */
    private static def calculateStats(def testData) {
        return [
                totalTests : testData.totalTests,
                passedTests: testData.passedTests,
                failedTests: testData.failedTests,
                errorTests : testData.errorTests,
                err        : ""
        ]
    }

}
