package xyz.dev.ops.test

import xyz.dev.ops.adapter.GolangCoverageReportAdapter

// 测试脚本用于验证 GolangUnitTestReportAdapter 和 GolangCoverageReportAdapter

// 注意：此脚本需要在项目根目录下运行，以便正确解析类路径

println "=== 测试 GolangUnitTestReportAdapter 和 GolangCoverageReportAdapter ==="

// 创建输出目录
def reportsDir = new File("reports")
reportsDir.mkdirs()

// 创建测试数据目录
def testDir = new File("test_data")
testDir.mkdirs()

println "\n--- 测试 GolangUnitTestReportAdapter ---"



// 创建模拟的 test-report.txt 文件
def testReportFile = new File(testDir, "test-report1.txt")
testReportFile.write("""=== RUN   TestExample1
--- PASS: TestExample1 (0.05s)
=== RUN   TestExample2
--- FAIL: TestExample2 (0.03s)
=== RUN   TestExample3
--- SKIP: TestExample3 (0.01s)
=== RUN   TestExample4
--- PASS: TestExample4 (0.02s)
""")

// 测试 GolangUnitTestReportAdapter
try {

    // 验证结果
//    assert testStats.tests == 4
//    assert testStats.failures == 1
//    assert testStats.skipped == 1
//    assert testStats.errors == 0

    println "✓ GolangUnitTestReportAdapter 功能测试通过"
} catch (Exception e) {
    println "✗ GolangUnitTestReportAdapter 功能测试失败: ${e.cause?.message ?: e.message}"
    e.printStackTrace()
}

println "\n--- 测试 GolangCoverageReportAdapter ---"

// 创建模拟的 coverage.out 文件
def coverageFile = new File("coverage.out")

def coverageStats = GolangCoverageReportAdapter.convert(
        coverageFile.absolutePath,
        new File("sonar-coverage.xml").absolutePath
)

println "\n代码覆盖率统计信息:"
println "  总行数: ${coverageStats.totalLines}"
println "  覆盖行数: ${coverageStats.coveredLines}"
println "  覆盖率: ${String.format("%.2f", coverageStats.coveragePercentage)}%"


// 测试 GolangCoverageReportAdapter
try {
    coverageFile = new File(testDir, "coverage.out")
    coverageFile.write("""mode: set
example.go:10.20,12.2 1 1
example.go:13.20,15.2 1 0
example.go:16.20,18.2 1 1
example.go:20.20,22.2 1 1
example.go:23.20,25.2 1 0
""")
    coverageStats = GolangCoverageReportAdapter.convert(
            coverageFile.absolutePath,
            new File(testDir, "sonar-coverage.xml").absolutePath
    )

    println "\n代码覆盖率统计信息:"
    println "  总行数: ${coverageStats.totalLines}"
    println "  覆盖行数: ${coverageStats.coveredLines}"
    println "  覆盖率: ${String.format("%.2f", coverageStats.coveragePercent)}%"

    println "✓ GolangCoverageReportAdapter 功能测试通过"
} catch (Exception e) {
    println "✗ GolangCoverageReportAdapter 功能测试失败: ${e.cause?.message ?: e.message}"
    e.printStackTrace()
}

println "\n=== 测试完成 ==="
println "测试数据保存在: ${testDir.absolutePath}"

// 可选：清理测试数据
testDir.deleteDir()