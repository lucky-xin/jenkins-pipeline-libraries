package xyz.dev.ops.adapter

/**
 * LCOV 到 SonarQube 覆盖率报告转换适配器
 *
 * 功能：
 * - 解析 LCOV 格式的覆盖率数据文件
 * - 转换为 SonarQube 兼容的 XML 格式覆盖率报告
 * - 提供覆盖率统计信息
 *
 * 使用示例：
 * def adapter = new LcovToSonarCoverageReportAdapter()
 * adapter.convert('reports/coverage.info', 'reports/sonar-coverage.xml')*/
class LcovCoverageReportAdapter {

    /**
     * 将 LCOV 覆盖率文件转换为 SonarQube XML 格式
     *
     * @param lcovFilePath LCOV 覆盖率文件路径
     * @param outputXmlPath 输出的 SonarQube XML 文件路径
     * @return Map 包含转换结果的统计信息
     */
    static def convert(String lcovFilePath, String outputXmlPath) {
        def stats = [totalFiles     : 0,
                     totalLines     : 0,
                     coveredLines   : 0,
                     coveragePercent: 0.0,
                     err            : ""]

        try {
            // 检查输入文件是否存在
            if (!new File(lcovFilePath).exists()) {
                SonarQubeCoverageReportUtils.createEmptyXmlReport(outputXmlPath)
                stats.err = "LCOV 文件不存在: ${lcovFilePath}"
                return stats
            }

            // 创建输出目录
            def outputDir = new File(outputXmlPath).parent
            if (outputDir && !new File(outputDir).exists()) {
                new File(outputDir).mkdirs()
            }

            // 解析 LCOV 文件
            def coverageData = parseLcovFile(lcovFilePath)

            // 生成 SonarQube XML 报告
            SonarQubeCoverageReportUtils.createXMLReport(coverageData as Map<String, List<Map<String, Object>>>, outputXmlPath)

            // 计算统计信息
            stats = calculateStats(coverageData)
        } catch (Exception e) {
            stats.err = "解析LCOV 覆盖率文件异常:" + e.message
            SonarQubeCoverageReportUtils.createEmptyXmlReport(outputXmlPath)
        }

        return stats
    }

    /**
     * 解析 LCOV 文件*/
    private static def parseLcovFile(String lcovFilePath) {
        def coverageData = [:]
        def file = new File(lcovFilePath)
        if (!file.exists()) {
            coverageData['err'] = "LCOV 文件不存在: ${lcovFilePath}"
            return coverageData
        }

        def currentFile = null
        def lines = file.readLines()
        for (final def line in lines) {
            if (line.startsWith('SF:')) {
                // 文件路径
                def filePath = line.substring(3)
                // 移除工作目录前缀，支持多种路径格式
                filePath = filePath.replaceFirst(/^\/workspace\//, '')
                filePath = filePath.replaceFirst(/^\/var\/jenkins_home\/workspace\/[^\/]+\//, '')
                filePath = filePath.replaceFirst(/^.*\/workspace\/[^\/]+\//, '')
                currentFile = filePath
                coverageData[currentFile] = []
            } else if (line.startsWith('DA:') && currentFile) {
                // 行覆盖率数据: DA:line_number,hit_count
                def dataPart = line.substring(3) // 移除 "DA:" 前缀
                def commaIndex = dataPart.indexOf(',')
                if (commaIndex > 0) {
                    try {
                        def lineNumber = dataPart.substring(0, commaIndex) as Integer
                        def hitCount = dataPart.substring(commaIndex + 1) as Integer
                        coverageData[currentFile] << [lineNumber: lineNumber,
                                                      hitCount  : hitCount,
                                                      covered   : hitCount > 0]
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的行
                        coverageData[currentFile] << [err: "警告: 无法解析DA行: ${line}, msg:" + e.getMessage()]
                    }
                }
            } else if (line.startsWith('end_of_record')) {
                break
            }
        }
        return coverageData
    }

    /**
     * 计算统计信息*/
    private static def calculateStats(def coverageData) {
        def stats = [totalFiles     : coverageData.size(),
                     totalLines     : 0,
                     totalErrs      : 0,
                     coveredLines   : 0,
                     coveragePercent: 0.0]

        coverageData.each { filePath, lines ->
            stats.totalLines += lines.size()
            stats.coveredLines += lines.count { it.covered }
            stats.totalErrs += lines.count { it.err != null }
        }

        if (stats.totalLines > 0) {
            stats.coveragePercent = (stats.coveredLines / stats.totalLines) * 100
        }

        return stats
    }
}
