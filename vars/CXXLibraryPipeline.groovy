import xyz.dev.ops.adapter.LcovCoverageReportAdapter
import xyz.dev.ops.adapter.LcovUnitTestReportAdapter
import xyz.dev.ops.notify.DingTalk

/**
 * Maven 库项目发布流水线（vars）
 *
 * 功能：读取 POM 信息，执行 Maven 构建与部署（deploy），并推送变更通知。
 * 先决条件：
 *  - Config File Provider: Maven settings.xml（fileId: 42697037-54bd-44a1-80c2-7a97d30f2266）
 */

def call(Map<String, Object> config) {
    /**
     * 入参（config）：
     *  robotId        钉钉机器人ID（可选）
     *  builderImage   构建镜像（默认 xin8/devops/cxx:latest）
     *  sqDashboardUrl SonarQube 外网地址（可选，仅用于通知展示）
     *  nexusUrl       Nexus3依赖仓库地址 外网地址（可选，仅用于通知展示）
     */
    // 设置默认值
    def params = [
            robotId       : config.robotId ?: '',
            projectName   : config.projectName ?: '',
            version       : config.version ?: '1.0.0',
            buildDir      : config.buildDir ?: 'build', // 构建目录
            sourceDir     : config.sourceDir ?: 'src', // 源码目录
            testDir       : config.testDir ?: 'test', // 单元测试目录
            cppcheckDir   : config.cppcheckDir ?: 'src', // cppcheck目录
            buildType     : config.buildType ?: 'Release', //构建选项
            buildSystem   : config.buildSystem ?: 'both', //构建系统，有效值：autotools、cmake、both
            installDir    : config.installDir ?: 'install', //的构建输出目录
            buildImage    : config.buildImage ?: "xin8/devops/cxx:latest",//c++编译环境镜像
            sqCliImage    : config.sqCliImage ?: "xin8/devops/sonar-scanner-cli:latest",//SonarQube扫描客户端镜像
            sqServerUrl   : config.sqServerUrl ?: "http://172.29.35.103:9000",//SonarQube内网地址
            sqDashboardUrl: config.sqDashboardUrl ?: "http://8.145.35.103:9000",//SonarQube外网地址，如果想在非公司网络看质量报告则配置SonarQube外网地址，否则该配置为内网地址
            nexusUrl      : config.nexusUrl ?: 'http://172.29.35.103:8081',// 你的 Nexus 服务器地址
            nexusRepo     : config.nexusRepo ?: 'c-cpp-raw-hosted',// 你的 Nexus 仓库名称
            enableTests   : config.enableTests ?: 'true',//开始测试
            enableDocs    : config.enableDocs ?: 'true',//是否生成文档
            cmakeFlags    : config.cmakeFlags ?: '',//自定义 CMake 参数
    ]

    def dingTalk = new DingTalk()
    pipeline {
        agent any
        options {
            timestamps()
            disableConcurrentBuilds()
            // ansiColor('xterm')
            timeout(time: 180, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '5'))
        }
        environment {
            SERVICE_NAME = "${params.projectName}"
            PROJECT_NAME = "${params.projectName}"
            // Maven配置
            MAVEN_BUILD_ARGS = "-u root:root"
            // 如果是pre分支则镜像版本为：'v' + 大版本号，如果是非pre分支则版本号为：大版本号 + '-' +【Git Commot id】
            VERSION = "${BRANCH_NAME == 'pre' ? 'v' + PROJECT_VERSION : PROJECT_VERSION + '-' + GIT_COMMIT.substring(0, 8)}"
            NEXUS_URL = "${params.nexusUrl}" // 你的 Nexus 服务器地址
            NEXUS_RAW_REPO = "${params.nexusRepo}"         // 你的 Raw Hosted 仓库名
            NEXUS_CREDENTIALS_ID = 'nexus-credentials'    // 你在 Jenkins 中配置的凭据 ID
            BUILD_IMAGE = "${params.buildImage}"
            PROJECT_VERSION = "${params.version}"
            BUILD_DIR = "${params.buildDir}" // 的构建输出目录
            INSTALL_DIR = "${params.installDir}" // 安装目录
            BUILD_TYPE = "${params.buildType}" // 构建选项
            ENABLE_TESTS = "${params.enableTests}"
            BUILD_SYSTEM = "${params.buildSystem}"
            ENABLE_DOCS = "${params.enableDocs}"
            CUSTOM_CMAKE_FLAGS = "${params.cmakeFlags}"
            CPPCHECK_DIR = "${params.cppcheckDir}"
            SOURCE_DIR = "${params.sourceDir}"
            TEST_DIR = "${params.testDir}"
            SQ_SERVER_URL = "${params.sqServerUrl}"
        }

        stages {
            stage('Setup Environment') {
                steps {
                    checkout scm
                    script {
                        // 步骤 2: 设置构建环境
                        sh '''
                        echo "=== 构建环境信息 ==="
                        echo "操作系统: $(uname -a)"
                        echo "架构: $(uname -m)"
                        echo "工作目录: $(pwd)"
                        echo "用户: $(whoami)"
                        echo "构建系统: ${BUILD_SYSTEM}"
                        echo "构建类型: ${BUILD_TYPE}"
                        echo "=================="
                        
                        # 创建必要的目录
                        mkdir -p ${INSTALL_DIR}
                        mkdir -p ${BUILD_DIR}
                    '''
                    }
                }
                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }


            stage('Check environment') {
                agent {
                    docker {
                        image "${env.BUILD_IMAGE}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }
                steps {
                    script {
                        // 步骤 3: 验证预构建的 Docker 镜像环境
                        sh '''
                        echo "=== 验证预构建环境 ==="
                        echo "使用镜像: ${BUILD_IMAGE}"
                        echo "当前用户: $(whoami)"
                        echo "工作目录: $(pwd)"

                        # 验证工具版本
                        echo "=== 验证工具版本 ==="
                        autoconf --version | head -1
                        automake --version | head -1
                        pkg-config --version
                        protoc --version
                        cmake --version | head -1

                        # 检查关键依赖
                        echo "=== 检查关键依赖 ==="
                        pkg-config --exists protobuf && echo "protobuf: OK" || echo "protobuf: MISSING"
                        pkg-config --exists libprotoc && echo "libprotoc: OK" || echo "libprotoc: MISSING"

                        echo "=== 预构建环境验证完成 ==="
                    '''
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Build with Autotools') {
                when {
                    anyOf {
                        equals expected: 'autotools', actual: env.BUILD_SYSTEM
                        equals expected: 'both', actual: env.BUILD_SYSTEM
                    }
                }

                agent {
                    docker {
                        image "${env.BUILD_IMAGE}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }

                steps {
                    script {
                        // 步骤 4: 使用 autotools 构建（包含覆盖率支持）
                        sh '''
                            echo "=== 开始 Autotools 构建 ==="
                            
                            # 生成构建系统
                            ./autogen.sh
                            
                            # 配置构建（启用覆盖率）
                            ./configure \
                                --prefix=${WORKSPACE}/${INSTALL_DIR} \
                                --enable-static \
                                --enable-shared \
                                --enable-code-coverage
                            
                            # 编译
                            make -j$(nproc)
                            
                            # 运行测试（如果启用）
                            if [ "${ENABLE_TESTS}" = "true" ]; then
                                echo "=== 运行测试 ==="
                                # 使用 || true 确保测试失败不会终止流水线
                                make check || {
                                    echo "警告: 部分测试失败，但继续执行后续步骤"
                                    echo "测试失败详情:"
                                    if [ -f test-suite.log ]; then
                                        tail -20 test-suite.log
                                    fi
                                }
                                
                                # 生成覆盖率报告
                                echo "=== 生成覆盖率报告 ==="
                                
                                # 创建reports目录
                                mkdir -p reports
                                    
                                # 生成覆盖率数据，使用错误处理确保失败不会终止流水线
                                if lcov --capture --directory . --output-file reports/coverage.info --rc lcov_branch_coverage=1 2>/dev/null; then
                                    echo "覆盖率数据收集成功"
                                    
                                    # 过滤覆盖率数据，移除系统路径和测试路径
                                    lcov --remove reports/coverage.info '/usr/*' --output-file reports/coverage.info
                                    lcov --remove reports/coverage.info '*/test/*' --output-file reports/coverage.info
                                    lcov --remove reports/coverage.info '*/build/*' --output-file reports/coverage.info
                                    lcov --remove reports/coverage.info '*/t/*' --output-file reports/coverage.info
                                    
                                    # 修复重复的路径问题：将 /var/jenkins_home/workspace/project-name/project-name/ 替换为 project-name/
                                    echo "修复重复路径问题..."
                                    # 使用项目名称动态替换路径
                                    PROJECT_NAME_FOR_SED=$(echo "${PROJECT_NAME}" | sed 's/[[\\.*^$()+?{|]/\\&/g')
                                    sed -i "s|/var/jenkins_home/workspace/${PROJECT_NAME_FOR_SED}/${PROJECT_NAME_FOR_SED}/|${PROJECT_NAME_FOR_SED}/|g" reports/coverage.info
                                    
                                    echo '覆盖率报告生成完成:'
                                    lcov --summary reports/coverage.info
                                    
                                    # 显示生成的覆盖率文件中的路径信息
                                    echo '覆盖率文件中的路径示例:'
                                    grep '^SF:' reports/coverage.info | head -3
                                    
                                    # 生成HTML报告
                                    if genhtml reports/coverage.info --output-directory reports/coverage-html --branch-coverage --title '${PROJECT_NAME} Coverage Report' 2>/dev/null; then
                                        echo "HTML覆盖率报告生成成功"
                                    else
                                        echo "警告: HTML覆盖率报告生成失败，但继续执行"
                                    fi
                                else
                                    echo "警告: 覆盖率数据收集失败，可能是没有编译时启用覆盖率选项"
                                    echo "TN:" > reports/coverage.info
                                fi
                                
                                # 生成静态代码分析报告
                                echo '=== 生成静态代码分析报告 ==='
                                if command -v cppcheck >/dev/null 2>&1; then
                                    if cppcheck ${CPPCHECK_DIR} --enable=all --inconclusive --xml-version=2 . 2> reports/cppcheck.xml; then
                                        echo "静态代码分析报告生成成功"
                                    else
                                        echo "警告: 静态代码分析报告生成失败，但继续执行"
                                        echo '<?xml version="1.0" encoding="UTF-8"?><results version="2"></results>' > reports/cppcheck.xml
                                    fi
                                else
                                    echo '警告: cppcheck未安装，创建空静态代码分析报告'
                                    echo '<?xml version="1.0" encoding="UTF-8"?><results version="2"></results>' > reports/cppcheck.xml
                                fi
                            fi
                        '''

                        if (fileExists('reports/coverage.info')) {
                            sh '''
                                echo '使用Groovy工具类生成SonarQube XML覆盖率报告...'
                                    
                                # 显示原始lcov覆盖率统计
                                echo '原始lcov覆盖率统计:'
                                lcov --summary reports/coverage.info | grep -E '(lines|functions)' || true
                            '''
                            // 添加调试信息
                            sh "echo '=== 调试信息 ==='"
                            sh "ls -la ${WORKSPACE}/reports/"
                            sh "echo 'coverage.info 文件大小:' && wc -l ${WORKSPACE}/reports/coverage.info"
                            sh "echo 'coverage.info 前10行:' && head -10 ${WORKSPACE}/reports/coverage.info"
                            sh "echo 'coverage.info 中的SF行:' && grep '^SF:' ${WORKSPACE}/reports/coverage.info | head -5"

                            def stats = LcovCoverageReportAdapter.convert("${WORKSPACE}/reports/coverage.info", "${WORKSPACE}/reports/sonar-coverage.xml")

                            // 检查生成的XML文件
                            sh "echo '生成的XML文件:' && ls -la ${WORKSPACE}/reports/sonar-coverage.xml || echo 'XML文件未生成'"

                            echo "📊 转换完成统计:"
                            echo "   - 文件数量: ${stats.totalFiles}"
                            echo "   - 总行数: ${stats.totalLines}"
                            echo "   - 覆盖行数: ${stats.coveredLines}"
                            echo "   - 覆盖率: ${String.format('%.1f', stats.coveragePercent)}%"
                            echo "   - 错误信息: ${stats.err}"
                            echo "   - 错误行数: ${stats.totalErrs}"
                        }

                        // 生成单元测试报告
                        if (fileExists('test-suite.log')) {
                            script {
                                def testStats = LcovUnitTestReportAdapter.convert("${WORKSPACE}/test-suite.log", "${WORKSPACE}/reports/test-results.xml")

                                echo "📊 单元测试报告生成统计:"
                                echo "   - 总测试数: ${testStats.totalTests}"
                                echo "   - 通过测试: ${testStats.passedTests}"
                                echo "   - 失败测试: ${testStats.failedTests}"
                                echo "   - 错误测试: ${testStats.errorTests}"
                                echo "   - 错误信息: ${testStats.err}"

                                // 检查生成的XML文件
                                sh "echo '生成的测试XML文件:' && ls -la ${WORKSPACE}/reports/test-results.xml || echo '测试XML文件未生成'"
                            }
                        }
                        sh '''
                            # 安装到指定目录
                            make install
                            echo "=== Autotools 构建完成 ==="
                        '''
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage("代码审核") {
                when {
                    anyOf {
                        equals expected: 'autotools', actual: env.BUILD_SYSTEM
                        equals expected: 'both', actual: env.BUILD_SYSTEM
                    }
                }

                agent {
                    docker {
                        image "${params.sqCliImage}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }

                steps {
                    withCredentials([string(credentialsId: 'sonarqube-token-secret', variable: 'SONAR_TOKEN')]) {
                        script {
                            // 简化的SonarQube扫描（复用已构建的环境）
                            sh """
                                echo '开始执行 SonarQube 代码扫描...'
                                echo '当前工作目录:'
                                pwd
                                
                                echo 'sonar-scanner -v'
                                sonar-scanner -v        

                                # 运行SonarQube扫描
                                sonar-scanner \
                                    -Dsonar.host.url=${SQ_SERVER_URL} \
                                    -Dsonar.token=\$SONAR_TOKEN \
                                    -Dsonar.projectKey=${SERVICE_NAME} \
                                    -Dsonar.projectName=${SERVICE_NAME} \
                                    -Dsonar.projectVersion=${VERSION} \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                    -Dsonar.projectBaseDir=. \
                                    -Dsonar.sources=${SOURCE_DIR} \
                                    -Dsonar.tests=${TEST_DIR} \
                                    -Dsonar.language=c++ \
                                    -Dsonar.coverage.exclusions=**/t/**,**/tests/**,**/test/** \
                                    -Dsonar.exclusions=**/*.o,**/*.la,**/*.pc,**/*.gcno,**/*.gcda,**/.deps/**,**/.libs/** \
                                    -Dsonar.cxx.file.suffixes=.c,.cpp,.cc,.cxx,.h,.hpp,.hxx \
                                    -Dsonar.cxx.includeDirectories=${SOURCE_DIR} \
                                    -Dsonar.cxx.defines=HAVE_CONFIG_H \
                                    -Dsonar.cxx.cppcheck.reportPaths=reports/cppcheck.xml \
                                    -Dsonar.cxx.xunit.reportPaths=reports/test-results.xml \
                                    -Dsonar.coverageReportPaths=reports/sonar-coverage.xml
                            """
                        }
                    }
                }
                post {
                    always {
                        // 归档报告
                        script {
                            if (fileExists("reports/coverage.info")) {
                                archiveArtifacts artifacts: "reports/coverage.info", fingerprint: true
                            }
                            if (fileExists("reports/coverage-html")) {
                                archiveArtifacts artifacts: "reports/coverage-html/**/*", fingerprint: true
                            }
                            if (fileExists("reports/test-results.xml")) {
                                archiveArtifacts artifacts: "reports/test-results.xml", fingerprint: true
                            }
                        }
                    }
                    success {
                        // 发布HTML覆盖率报告
                        script {
                            // 归档HTML覆盖率报告
                            script {
                                if (fileExists("coverage-html/index.html")) {
                                    archiveArtifacts artifacts: "coverage-html/**/*", fingerprint: true
                                    echo "HTML覆盖率报告已归档，可在构建产物中下载查看"
                                }
                            }
                        }
                    }
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Build with CMake') {
                when {
                    anyOf {
                        equals expected: 'cmake', actual: env.BUILD_SYSTEM
                        equals expected: 'both', actual: env.BUILD_SYSTEM
                    }
                }

                agent {
                    docker {
                        image "${env.BUILD_IMAGE}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }

                steps {
                    script {
                        sh '''
                        echo "=== 开始 CMake 构建 ==="
                        
                        cd ${BUILD_DIR}
                        
                        # CMake 配置
                        cmake -S ../build-cmake \
                            -DCMAKE_BUILD_TYPE=${BUILD_TYPE} \
                            -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/${INSTALL_DIR} \
                            -DBUILD_TESTS=${ENABLE_TESTS} \
                            -DBUILD_PROTOC=ON \
                            ${CUSTOM_CMAKE_FLAGS}
                        
                        # 编译
                        cmake --build . -j$(nproc)
                        
                        # 运行测试（如果启用）
                        if [ "${ENABLE_TESTS}" = "true" ]; then
                            echo "=== 运行 CMake 测试 ==="
                            cmake --build . --target test
                        fi
                        
                        # 安装
                        cmake --build . --target install
                        
                        echo "=== CMake 构建完成 ==="
                    '''
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Generate Documentation') {
                when {
                    equals expected: true, actual: env.ENABLE_DOCS
                }

                agent {
                    docker {
                        image "${env.BUILD_IMAGE}"
                        // 使用 root 用户并挂载 Maven 缓存目录
                        args '-u root:root'
                        reuseNode true
                    }
                }

                steps {
                    script {
                        sh '''
                        echo "=== 生成文档 ==="
                        
                        # 生成 Doxygen 文档
                        if [ -f Doxyfile ]; then
                            doxygen Doxyfile
                        else
                            echo "Doxyfile 不存在，跳过文档生成"
                        fi
                        
                        echo "=== 文档生成完成 ==="
                    '''
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Collect Artifacts') {
                steps {
                    script {
                        // 步骤 5: 列出构建产物，确认文件
                        sh '''
                        echo "=== 构建产物收集 ==="
                        
                        # 显示目录结构
                        tree ${INSTALL_DIR} || find ${INSTALL_DIR} -type f | head -20
                                              
                        # 显示文件大小
                        echo "=== 产物文件大小 ==="
                        du -sh ${INSTALL_DIR}/* || true
                    '''
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Deploy to Nexus') {
                steps {
                    script {
                        // 步骤 6: 使用 curl 上传文件到 Nexus
                        withCredentials([usernamePassword(
                                credentialsId: "${env.NEXUS_CREDENTIALS_ID}",
                                usernameVariable: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_PASS')]) {

                            // 获取系统架构信息（Docker 容器内）
                            def arch = sh(script: 'uname -m', returnStdout: true).trim()
                            def os = sh(script: 'uname -s | tr "[:upper:]" "[:lower:]"', returnStdout: true).trim()
                            // 在 Docker 容器中，通常是 linux
                            def platform = "linux-${arch}"

                            echo "上传到平台: ${platform}"

                            env.FULL_NAME = "${SERVICE_NAME}-${VERSION}-${platform}"

                            // 创建版本目录结构
                            def versionPath = "${PROJECT_NAME}/${VERSION}/${platform}"

                            // 批量上传函数
                            def uploadFile = { sourceFile, targetPath ->
                                if (fileExists(sourceFile)) {
                                    sh """
                                    curl -v -u \$NEXUS_USER:\$NEXUS_PASS \
                                        --upload-file ${sourceFile} \
                                        \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/${targetPath}
                                """
                                    return true
                                }
                                return false
                            }

                            // 上传文档（如果存在）
                            if (params.ENABLE_DOCS && fileExists("html")) {
                                sh """
                                    tar -czf docs.tar.gz html/
                                """

                                uploadFile("docs.tar.gz", "docs.tar.gz")
                            }

                            // 打包整个安装目录为 tar.gz 并上传
                            echo "=== 打包安装产物 ==="

                            sh """
                                # 创建完整的产物包
                                tar -czf ${FULL_NAME}.tar.gz -C ${INSTALL_DIR} .
                                
                                # 显示打包结果
                                echo "产物包信息:"
                                ls -lh ${FULL_NAME}.tar.gz
                            """
                            uploadFile("${FULL_NAME}.tar.gz", "${FULL_NAME}.tar.gz")

                            // 创建包含所有文件的完整包（包括文档）
                            if (params.ENABLE_DOCS && fileExists("html")) {
                                echo "=== 创建包含文档的完整包 ==="
                                sh """
                                    # 创建临时目录
                                    mkdir -p temp-package
                                    
                                    # 复制安装文件
                                    cp -r ${INSTALL_DIR}/* temp-package/
                                    
                                    # 复制文档
                                    cp -r html temp-package/docs
                                    
                                    # 创建完整包
                                    tar -czf ${FULL_NAME}-with-docs.tar.gz -C temp-package .
                                    
                                    # 显示打包结果
                                    echo "完整包信息:"
                                    ls -lh ${FULL_NAME}-with-docs.tar.gz
    
                                    # 清理临时目录
                                    rm -rf temp-package
                                """
                                uploadFile("${FULL_NAME}-with-docs.tar.gz", "${FULL_NAME}-with-docs.tar.gz")
                            }

                            // 创建版本信息文件
                            sh """
                            cat > version-info.json << EOF
{
    "name": "${PROJECT_NAME}",
    "version": "${PROJECT_VERSION}",
    "platform": "${platform}",
    "build_date": "\$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "git_commit": "\$(git rev-parse HEAD)",
    "git_branch": "\$(git rev-parse --abbrev-ref HEAD)",
    "build_system": "${BUILD_SYSTEM}",
    "build_type": "${BUILD_TYPE}",
    "docker_image": "\$(cat /etc/os-release | grep PRETTY_NAME | cut -d'=' -f2 | tr -d '\\\"')",
    "packages": {
        "main": "${FULL_NAME}.tar.gz",
        "with_docs": "${FULL_NAME}-with-docs.tar.gz",
        "docs_only": "docs.tar.gz"
    }
}
EOF
                        """
                            uploadFile("version-info.json", "version-info.json ")
                        }
                    }
                }

                post {
                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }

            stage('Archive Artifacts') {
                steps {
                    script {
                        // 步骤 7: 归档构建产物供 Jenkins 下载
                        archiveArtifacts artifacts: "${INSTALL_DIR}/**/*", fingerprint: true, allowEmptyArchive: true

                        // 归档打包文件
                        archiveArtifacts artifacts: "${env.FULL_NAME}*.tar.gz", fingerprint: true, allowEmptyArchive: true

                        // 如果生成了文档，也归档文档
                        if (params.ENABLE_DOCS && fileExists("html")) {
                            archiveArtifacts artifacts: "html/**/*", fingerprint: true, allowEmptyArchive: true
                        }

                        // 归档版本信息文件
                        archiveArtifacts artifacts: "version-info.json", fingerprint: true, allowEmptyArchive: true
                    }
                }

                post {
                    success {
                        script {
                            dingTalk.post([
                                    robotId    : "${params.robotId}",
                                    jobName    : "${env.SERVICE_NAME}",
                                    sqServerUrl: "${params.sqDashboardUrl}"
                            ])
                        }
                    }

                    failure {
                        script {
                            dingTalk.post([
                                    robotId: "${params.robotId}",
                                    jobName: "${env.SERVICE_NAME}",
                                    reason : "【${env.STAGE_NAME}】失败！"
                            ])
                        }
                    }
                }
            }
        } //stages

        post {
            always {
                cleanWs()
            }
        }
    } //pipeline
}
