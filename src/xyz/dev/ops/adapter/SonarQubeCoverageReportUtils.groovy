package xyz.dev.ops.adapter

/**
 * LCOV 到 SonarQube 覆盖率报告转换适配器
 *
 */
class SonarQubeCoverageReportUtils {

    /**
     * 生成 SonarQube XML 报告*/
    static def createXMLReport(Map<String, List<Map<String, Object>>> coverageData,
                               String outputXmlPath) {
        def xml = new StringBuilder()

        // XML 头部
        xml.append('<?xml version="1.0" encoding="UTF-8"?>\n')
        xml.append('<coverage version="1">\n')
        xml.append('  <!-- Generated from lcov coverage data -->\n')

        // 处理每个文件的覆盖率数据
        coverageData.each { filePath, lines ->
            xml.append("  <file path=\"${filePath}\">\n")
            lines.each { lineData -> xml.append("    <lineToCover lineNumber=\"${lineData.lineNumber}\" covered=\"${lineData.covered}\" branchesToCover=\"0\" coveredBranches=\"0\"/>\n")
            }
            xml.append("  </file>\n")
        }

        // XML 尾部
        xml.append('</coverage>\n')

        // 写入文件
        new File(outputXmlPath).text = xml.toString()
    }

    /**
     * 创建空的 XML 报告*/
    static def createEmptyXmlReport(String outputXmlPath) {
        def xml = '''<?xml version="1.0" encoding="UTF-8"?>
<coverage version="1">
  <!-- Empty coverage report -->
</coverage>'''

        // 创建输出目录
        def outputDir = new File(outputXmlPath).parentFile
        if (outputDir && !outputDir.exists()) {
            outputDir.mkdirs()
        }

        new File(outputXmlPath).text = xml
    }
}
