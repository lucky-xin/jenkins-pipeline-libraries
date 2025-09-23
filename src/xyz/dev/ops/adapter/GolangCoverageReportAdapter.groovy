package xyz.dev.ops.adapter


/**
 * GolangCoverageReportAdapter
 *
 * 将 Go 的 coverage.out 格式转换为 SonarQube 可识别的 XML 格式
 * 
 * Go coverage.out 格式说明：
 * mode: set
 * filename:startLine.startCol,endLine.endCol numberOfStatements numberOfHits
 * 
 * 例如：xyz/test/helloworld/main.go:22.13,27.10 3 0
 * - filename: xyz/test/helloworld/main.go
 * - startLine: 22, startCol: 13
 * - endLine: 27, endCol: 10
 * - numberOfStatements: 3
 * - numberOfHits: 0 (表示这3个语句没有被执行)
 */
class GolangCoverageReportAdapter {

    /**
     * 转换覆盖率报告
     * @param inputFile 输入的 coverage.out 文件路径
     * @param outputFile 输出的 SonarQube XML 格式文件路径
     * @return Map 包含转换结果的统计信息
     */
    static def convert(String inputFile, String outputFile) {
        def stats = [totalFiles     : 0,
                     totalLines     : 0,
                     coveredLines   : 0,
                     coveragePercent: 0.0,
                     err            : ""]

        try {
            // 读取输入文件
            def input = new File(inputFile)
            if (!input.exists()) {
                SonarQubeCoverageReportUtils.createEmptyXmlReport(outputFile)
                stats.err = "Coverage 文件不存在: ${inputFile}"
                return stats
            }

            // 创建输出目录
            def output = new File(outputFile)
            output.getParentFile().mkdirs()

            // 解析覆盖率数据
            Map<String, List<Map<String, Object>>> fileData = new HashMap<String, List<Map<String, Object>>>()
            def lines = input.readLines()

            // 统计信息
            def totalStatements = 0
            def coveredStatements = 0

            // 跳过第一行 "mode: set"
            for (int i = 1; i < lines.size(); i++) {
                def line = lines[i].trim()
                if (line.isEmpty()) continue

                try {
                    // 解析格式: filename:startLine.startCol,endLine.endCol numberOfStatements numberOfHits
                    def parts = line.split()
                    if (parts.length != 3) continue

                    def filePos = parts[0]
                    def numStatements = Integer.parseInt(parts[1])
                    def hits = Integer.parseInt(parts[2])

                    // 分割文件名和位置信息
                    def posSplit = filePos.split(':')
                    if (posSplit.length != 2) continue

                    def fileName = posSplit[0]
                    def position = posSplit[1]

                    // 解析位置信息: startLine.startCol,endLine.endCol
                    def rangeParts = position.split(',')
                    if (rangeParts.length != 2) continue

                    def startPart = rangeParts[0].split('\\.')
                    def endPart = rangeParts[1].split('\\.')
                    if (startPart.length != 2 || endPart.length != 2) continue

                    def startLine = Integer.parseInt(startPart[0])
                    def endLine = Integer.parseInt(endPart[0])

                    // 按文件组织数据
                    if (!fileData.containsKey(fileName)) {
                        fileData[fileName] = []
                    }

                    // 处理行范围：从 startLine 到 endLine
                    // Go 覆盖率格式中，hits 表示整个语句块的命中次数
                    // 对于重叠的行，我们使用"或"逻辑：如果任何语句块覆盖了该行，则该行被覆盖
                    for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
                        // 检查是否已经存在该行的数据
                        def existingLine = fileData[fileName].find { it.lineNumber == lineNum }
                        if (existingLine) {
                            // 使用"或"逻辑：如果当前行未覆盖但新的数据表明已覆盖，则更新
                            if (!existingLine.covered && hits > 0) {
                                existingLine.hitCount = hits
                                existingLine.covered = true
                            } else if (existingLine.covered && hits > 0) {
                                // 如果都已覆盖，取最大命中次数
                                existingLine.hitCount = Math.max(existingLine.hitCount as int, hits)
                            }
                        } else {
                            // 添加新的行数据
                            fileData[fileName] << ([
                                    lineNumber: lineNum,
                                    hitCount  : hits,
                                    covered   : hits > 0
                            ] as Map<String, Object>)
                        }
                    }

                    // 更新统计信息
                    totalStatements += numStatements
                    if (hits > 0) {
                        coveredStatements += numStatements
                    }

                } catch (Exception e) {
                    println "警告: 无法解析行: ${line}, 错误: ${e.message}"
                }
            }

            // 计算统计信息
            stats.totalFiles = fileData.size()

            fileData.each { fileName, flines ->
                def fileTotalLines = flines.size()
                def fileCoveredLines = flines.count { it.covered }
                stats.totalLines += fileTotalLines
                stats.coveredLines += fileCoveredLines
            }

            if (stats.totalLines > 0) {
                stats.coveragePercent = (stats.coveredLines / stats.totalLines) * 100
            }
            
            // 生成 SonarQube XML 格式
            SonarQubeCoverageReportUtils.createXMLReport(fileData, outputFile)
        } catch (Exception e) {
            stats.err = "解析覆盖率文件异常: " + e.message
            SonarQubeCoverageReportUtils.createEmptyXmlReport(outputFile)
        }

        return stats
    }

}
