#!/usr/bin/env groovy
package xyz.dev.ops.test

import xyz.dev.ops.adapter.LcovUnitTestReportAdapter

// 测试 UnitTestReportToSonarReportAdapter

// 创建测试用的 test-suite.log 文件
def testSuiteLog = '''# TOTAL: 10
# PASS: 8
# FAIL: 2
# ERROR: 0
FAIL: t/test-failed-1 (exit status: 139)
FAIL: t/test-failed-2 (exit status: 1)
PASS: t/test-passed-1 (exit status: 0)
PASS: t/test-passed-2 (exit status: 0)
'''

// 写入测试文件
//new File('test-suite.log').text = testSuiteLog

// 创建测试目录和日志文件
new File('t').mkdirs()
new File('t/test-passed-1.log').text = 'PASS: t/test-passed-1 (exit status: 0)'
new File('t/test-passed-2.log').text = 'PASS: t/test-passed-2 (exit status: 0)'
new File('t/test-failed-1.log').text = 'FAIL: t/test-failed-1 (exit status: 139)'
new File('t/test-failed-2.log').text = 'FAIL: t/test-failed-2 (exit status: 1)'

// 模拟适配器的核心逻辑
def parseTestSuiteLog(String testSuiteLogPath) {
    def testData = [
            totalTests     : 0,
            passedTests    : 0,
            failedTests    : 0,
            errorTests     : 0,
            failedTestNames: [],
            passedTestNames: []
    ]

    new File(testSuiteLogPath).eachLine { line ->
        if (line.startsWith('# TOTAL:')) {
            testData.totalTests = Integer.parseInt(line.substring(8).trim().split()[0])
        } else if (line.startsWith('# PASS:')) {
            testData.passedTests = Integer.parseInt(line.substring(7).trim().split()[0])
        } else if (line.startsWith('# FAIL:')) {
            testData.failedTests = Integer.parseInt(line.substring(7).trim().split()[0])
        } else if (line.startsWith('# ERROR:')) {
            testData.errorTests = Integer.parseInt(line.substring(8).trim().split()[0])
        } else if (line.startsWith('FAIL:')) {
            def match = line =~ /FAIL: t\/([^\s]+)/
            if (match) {
                testData.failedTestNames << match[0][1]
            }
        }
    }

    // 查找通过的测试
    def testDir = new File(testSuiteLogPath).parent
    def logFiles = []
    new File(testDir).eachFileRecurse { file ->
        if (file.name.endsWith('.log')) {
            logFiles << file
        }
    }

    logFiles.each { logFile ->
        if (logFile.text.contains('PASS') && logFile.text.contains('exit status: 0')) {
            def testName = logFile.name.replace('.log', '')
            if (testName && !testData.failedTestNames.contains(testName)) {
                testData.passedTestNames << testName
            }
        }
    }

    return testData
}

def workDir = '/Users/chaoxin.lu/IdeaProjects/jenkins-pipeline-libraries'
// 测试解析逻辑
def testData = LcovUnitTestReportAdapter.convert(
        workDir + '/test-suite.log',
        workDir + '/test-results.xml'
)

println "=== 测试结果 ==="
println "总测试数: ${testData.totalTests}"
println "通过测试: ${testData.passedTests}"
println "失败测试: ${testData.failedTests}"
println "错误测试: ${testData.errorTests}"
println "失败测试名称: ${testData.failedTestNames}"
println "通过测试名称: ${testData.passedTestNames}"

new File('t').deleteDir()