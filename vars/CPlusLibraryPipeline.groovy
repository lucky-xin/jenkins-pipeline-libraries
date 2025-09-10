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
     *  builderImage   构建镜像（默认 xin8/protobuf-c-builder:latest）
     *  sqDashboardUrl SonarQube 外网地址（可选，仅用于通知展示）
     *  nexusUrl       Nexus3依赖仓库地址 外网地址（可选，仅用于通知展示）
     */
    // 设置默认值
    def params = [
            robotId       : config.robotId ?: '',
            projectName   : config.projectName ?: '',
            version       : config.version ?: '1.0.0',
            buildDir      : config.buildDir ?: 'build', //protobuf-c 的构建输出目录
            buildType     : config.buildType ?: 'Release', //构建选项
            buildSystem   : config.buildSystem ?: 'both', //构建系统，有效值：autotools、cmake、both
            installDir    : config.installDir ?: 'install', //protobuf-c 的构建输出目录
            builderImage  : config.builderImage ?: "xin8/protobuf-c-builder:latest",
            sqDashboardUrl: config.sqDashboardUrl ?: "http://8.145.35.103:9000",
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
            BUILD_IMAGE = 'xin8/protobuf-c-builder:latest'
            PROJECT_VERSION = "${params.version}"
            BUILD_DIR = "${params.buildDir}" // 的构建输出目录
            INSTALL_DIR = "${params.installDir}" // 安装目录
            BUILD_TYPE = "${params.buildType}" // 构建选项
            ENABLE_TESTS = "${params.enableTests}"
            BUILD_SYSTEM = "${params.buildSystem}"
            ENABLE_DOCS = "${params.enableDocs}"
            CUSTOM_CMAKE_FLAGS = "${params.cmakeFlags}"
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
                        libtool --version | head -1
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
                        // 步骤 4: 使用 autotools 构建
                        sh '''
                        echo "=== 开始 Autotools 构建 ==="
                        
                        # 生成构建系统
                        ./autogen.sh
                        
                        # 配置构建
                        ./configure \
                            --prefix=${WORKSPACE}/${INSTALL_DIR} \
                            --enable-static \
                            --enable-shared
                        
                        # 编译
                        make -j$(nproc)
                        
                        # 运行测试（如果启用）
                        if [ "${ENABLE_TESTS}" = "true" ]; then
                            echo "=== 运行测试 ==="
                            make check
                        fi
                        
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
                        
                        echo "=== 主要产物文件 ==="
                        ls -la ${INSTALL_DIR}/lib/libprotobuf-c.* || true
                        ls -la ${INSTALL_DIR}/bin/protoc-gen-c || true
                        ls -la ${INSTALL_DIR}/include/protobuf-c.h || true
                        ls -la ${INSTALL_DIR}/lib/pkgconfig/libprotobuf-c.pc || true
                        
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

                            // 创建版本目录结构
                            def versionPath = "${PROJECT_NAME}/${VERSION}/${platform}"

                            // 批量上传函数
                            def uploadFile = { sourceFile, targetPath ->
                                if (fileExists(sourceFile)) {
                                    sh """
                                    curl -v -u \${NEXUS_USER}:\${NEXUS_PASS} \
                                        --upload-file ${sourceFile} \
                                        \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/${targetPath}
                                """
                                    return true
                                }
                                return false
                            }

                            // 上传库文件
                            uploadFile("${INSTALL_DIR}/lib/libprotobuf-c.so", "lib/libprotobuf-c.so")
                            uploadFile("${INSTALL_DIR}/lib/libprotobuf-c.a", "lib/libprotobuf-c.a")

                            // 上传头文件
                            uploadFile("${INSTALL_DIR}/include/protobuf-c.h", "include/protobuf-c.h")

                            // 上传 pkg-config 文件
                            uploadFile("${INSTALL_DIR}/lib/pkgconfig/libprotobuf-c.pc", "lib/pkgconfig/libprotobuf-c.pc")

                            // 上传可执行文件
                            uploadFile("${INSTALL_DIR}/bin/protoc-gen-c", "bin/protoc-gen-c")

                            // 上传 proto 文件
                            uploadFile("${INSTALL_DIR}/include/protobuf-c/protobuf-c.proto", "include/protobuf-c/protobuf-c.proto")

                            // 上传文档（如果存在）
                            if (params.ENABLE_DOCS && fileExists("html")) {
                                sh """
                                tar -czf docs.tar.gz html/
                                curl -v -u \${NEXUS_USER}:\${NEXUS_PASS} \
                                    --upload-file docs.tar.gz \
                                    \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/docs.tar.gz
                            """
                            }

                            // 打包整个安装目录为 tar.gz 并上传
                            echo "=== 打包安装产物 ==="
                            sh """
                            # 创建完整的产物包
                            tar -czf protobuf-c-${VERSION}-${platform}.tar.gz -C ${INSTALL_DIR} .
                            
                            # 显示打包结果
                            echo "产物包信息:"
                            ls -lh protobuf-c-${VERSION}-${platform}.tar.gz
                            
                            # 上传完整的产物包
                            curl -v -u \${NEXUS_USER}:\${NEXUS_PASS} \
                                --upload-file protobuf-c-${VERSION}-${platform}.tar.gz \
                                \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/protobuf-c-${VERSION}-${platform}.tar.gz
                        """

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
                                tar -czf protobuf-c-${VERSION}-${platform}-with-docs.tar.gz -C temp-package .
                                
                                # 显示打包结果
                                echo "完整包信息:"
                                ls -lh protobuf-c-${VERSION}-${platform}-with-docs.tar.gz
                                
                                # 上传完整包
                                curl -v -u \${NEXUS_USER}:\${NEXUS_PASS} \
                                    --upload-file protobuf-c-${VERSION}-${platform}-with-docs.tar.gz \
                                    \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/protobuf-c-${VERSION}-${platform}-with-docs.tar.gz
                                
                                # 清理临时目录
                                rm -rf temp-package
                            """
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
        "main": "protobuf-c-${VERSION}-${platform}.tar.gz",
        "with_docs": "protobuf-c-${VERSION}-${platform}-with-docs.tar.gz",
        "docs_only": "docs.tar.gz"
    },
    "files": {
        "library": "lib/libprotobuf-c.so",
        "static_library": "lib/libprotobuf-c.a", 
        "header": "include/protobuf-c.h",
        "pkgconfig": "lib/pkgconfig/libprotobuf-c.pc",
        "executable": "bin/protoc-gen-c",
        "proto": "include/protobuf-c/protobuf-c.proto"
    }
}
EOF
                            
                            curl -v -u \${NEXUS_USER}:\${NEXUS_PASS} \
                                --upload-file version-info.json \
                                \${NEXUS_URL}/repository/\${NEXUS_RAW_REPO}/${versionPath}/version-info.json
                        """
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
                        archiveArtifacts artifacts: "protobuf-c-*.tar.gz", fingerprint: true, allowEmptyArchive: true

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
            always { cleanWs() }
        }
    } //pipeline
}

