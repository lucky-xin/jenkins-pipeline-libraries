package xyz.dev.ops.adapter

import groovy.json.JsonSlurper
import xyz.dev.ops.adapter.SonarQubeJUnitXmlReportUtils

/**
 * 将 Vitest/Jest 的 test-results.json 转换为 SonarQube Generic Test Executions XML 报告
 */
class ExecutionsReportAdapter {

    /**
     * 将 JSON（test-results.json）转换为 testExecutions XML。
     *
     * @param lcovFilePath 输入文件路径（此参数名沿用接口约定，实际为 test-results.json 路径）
     * @param outputXmlPath 输出 testExecutions XML 路径
     */
    static def convert(String lcovFilePath, String outputXmlPath) {
        if (!lcovFilePath) {
            throw new IllegalArgumentException("test-results.json 路径不能为空")
        }
        def inputFile = new File(lcovFilePath)
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("找不到 test-results.json 文件: ${lcovFilePath}")
        }

        def json = new JsonSlurper().parse(inputFile)
        def results = (json?.testResults instanceof List) ? json.testResults : []

        def testcases = []
        results.each { suite ->
            String testFile = (suite?.name ?: '').toString()
            def assertions = (suite?.assertionResults instanceof List) ? suite.assertionResults : []
            assertions.each { ar ->
                String name = (ar?.fullName ?: ar?.title ?: '').toString()
                // 将 ancestorTitles 合并为 classname；若为空则用文件名去掉路径作为类名
                def ancestors = (ar?.ancestorTitles instanceof List) ? ar.ancestorTitles : []
                String classname = ancestors ? ancestors.join('.') : deriveClassNameFromPath(testFile)
                // vitest/jest duration 通常为毫秒，这里转为秒（decimal）
                BigDecimal seconds = 0.0
                try {
                    def dur = (ar?.duration ?: 0) as BigDecimal
                    seconds = dur.divide(new BigDecimal(1000))
                } catch (Throwable ignored) { seconds = 0.0 }

                String status = (ar?.status ?: '').toString()
                boolean isFailed = status?.toLowerCase() == 'failed' || ((ar?.failureMessages instanceof List) && ar.failureMessages.size() > 0)
                boolean isSkipped = status?.toLowerCase() in ['pending', 'skipped']

                testcases << [
                        name     : name,
                        classname: classname,
                        time     : seconds,
                        failure  : isFailed ? ((ar?.failureMessages && ar.failureMessages[0]) ?: 'FAILED') : null,
                        error    : null,
                        skipped  : isSkipped ? 'SKIPPED' : null,
                        testFile : testFile ?: (classname ? classname + '.test' : 'test.spec')
                ]
            }
        }

        def data = [ testcases: testcases ]
        SonarQubeJUnitXmlReportUtils.createExecutionsXmlReport(data, outputXmlPath)

        return [total: testcases.size(), err: '']
    }

    private static String deriveClassNameFromPath(String path) {
        if (!path) return ''
        def normalized = path.replace('\\', '/')
        def file = normalized.tokenize('/')?.last()
        return file ?: normalized
    }
}


