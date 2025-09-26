# Jenkins 共享库 Groovy 脚本使用说明

这是一个 Jenkins 共享库项目，提供了多种类型的微服务流水线脚本和运维工具，支持 Maven、Vue3、Golang、C++ 等不同技术栈的构建和部署。

## 🏗️ 项目架构

### 核心组件

- **vars/**: Jenkins 流水线脚本，提供开箱即用的 CI/CD 流水线
- **src/**: 运维工具类库，提供可复用的工具函数
- **build-env/**: 构建环境镜像，包含预配置的开发环境
- **resources/**: 配置模板文件，用于 Kubernetes 部署

### 技术栈支持

| 技术栈         | 流水线脚本                    | 构建环境                             | 部署方式                |
|-------------|--------------------------|----------------------------------|---------------------|
| **Maven**   | `MavenMicroSvcPipeline`  | `maven:3.9.11-amazoncorretto-17` | Kubernetes + Docker |
| **Vue3**    | `Vue3MicroSvcPipeline`   | `node:24.6.0-alpine`             | Kubernetes + Nginx  |
| **Golang**  | `GolangMicroSvcPipeline` | `golang:1.25`                    | Kubernetes + Alpine |
| **Python**  | `PythonMicroSvcPipeline` | `xin8/devops/python:latest`      | Kubernetes + Alpine |
| **Python库** | `PythonLibraryPipeline`  | `xin8/devops/python:latest`      | Nexus 仓库            |
| **C++**     | `CXXLibraryPipeline`     | `xin8/devops/cxx:latest`         | Nexus 仓库            |

### 核心特性

- ✅ **多平台支持**: Docker 多架构镜像构建 (AMD64/ARM64)
- ✅ **代码质量**: 集成 SonarQube 代码扫描
- ✅ **自动化部署**: Kubernetes 模板化部署
- ✅ **通知集成**: 钉钉机器人通知
- ✅ **报告生成**: 覆盖率报告、测试报告
- ✅ **环境隔离**: Docker 容器化构建环境

## 📋 目录

- [环境准备](#环境准备)
- [镜像构建](#镜像构建)
- [vars 流水线脚本](#vars-流水线脚本)
- [ops 运维工具](#ops-运维工具)
- [配置要求](#配置要求)
- [使用示例](#使用示例)

## 🚀 环境准备

### 前置条件

1. **Jenkins 环境**
    - Jenkins 2.400+
    - Pipeline 插件
    - Docker Pipeline 插件
    - Config File Provider 插件
    - Kubernetes CLI 插件

2. **Docker 环境**
    - Docker 19.03+ (支持 buildx)
    - Docker Registry 访问权限

3. **Kubernetes 环境**
    - kubectl 工具
    - kubeconfig 配置文件

## 🐳 镜像构建

### 第一步：构建 SonarQube 扫描器镜像

执行 SonarQube 代码审核客户端镜像构建：

```bash
cd build-env/sonar-scanner-cli
./build.sh
```

**构建选项：**

- `./build.sh` - 构建并推送镜像到仓库（默认，使用 ARM64 Dockerfile）
- `./build.sh -l` - 仅构建本地镜像（不推送）
- `./build.sh -t v1.0.0` - 指定镜像标签
- `./build.sh --platforms linux/amd64` - 指定平台
- `./build.sh -f Dockerfile_AMD64` - 使用 AMD64 Dockerfile 构建
- `./build.sh -r myregistry` - 指定镜像仓库地址
- `./build.sh --help` - 显示帮助信息

**架构选择说明：**

- `Dockerfile_ARM64`：适用于 ARM64 架构的处理器（如 Apple Silicon、ARM 服务器）
- `Dockerfile_AMD64`：适用于 x86_64 架构的处理器（如 Intel、AMD 处理器）
- 默认使用 ARM64 架构，如需构建 AMD64 版本请使用 `-f Dockerfile_AMD64`

**镜像信息：**

- 镜像名称：`xin8/devops/sonar-scanner-cli:latest`
- 支持平台：`linux/arm64,linux/amd64`
- 用途：代码质量扫描
- 包含工具：SonarQube Scanner CLI

### 第二步：构建 C++ 构建环境镜像

执行 C++ 开发环境镜像构建：

```bash
cd build-env/cxx
./build.sh
```

**构建选项：**

- `./build.sh` - 使用默认参数构建
- `./build.sh -r myregistry` - 指定 Docker 仓库地址
- `./build.sh -t v1.0.0` - 指定镜像标签
- `./build.sh --help` - 显示帮助信息

**镜像信息：**

- 镜像名称：`xin8/devops/cxx:latest`
- 支持平台：`linux/amd64,linux/arm64/v8`
- 包含工具：autoconf, automake, libtool, protoc, cmake, pkg-config
- 用途：C++ 项目构建环境
- 支持构建系统：Autotools、CMake

## 📦 vars 流水线脚本

### 1. MavenMicroSvcPipeline.groovy

**功能：** Maven 微服务通用流水线

- Maven 构建与 SonarQube 扫描
- Docker 多架构镜像构建与推送
- Kubernetes 发布（模板渲染）

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

MavenMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'your-service-name',
        dockerRepository: 'your-registry.com:5001',
        k8sServerUrl    : 'https://your-k8s-api:6443'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImage` - 基础镜像（默认：openjdk:17.0-slim）
- `buildImage` - 构建镜像（默认：maven:3.9.11-amazoncorretto-17）
- `svcName` - 服务名（为空则取 pom.artifactId）
- `dockerRepository` - 镜像仓库地址
- `k8sServerUrl` - k8s API 地址
- `k8sDeployImage` - kubectl 镜像
- `k8sDeployArgs` - kubectl 容器参数

### 2. Vue3MicroSvcPipeline.groovy

**功能：** Vue3 微服务前端流水线

- Node.js 构建（npm）
- SonarQube 代码扫描
- Docker 多架构镜像构建与推送
- Kubernetes 发布

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

Vue3MicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'your-frontend-service',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImage` - Nginx 基础镜像（默认：nginx:1.27-alpine）
- `buildImage` - Node 构建镜像（默认：node:24.6.0-alpine）
- `svcName` - 服务名（必填）
- `version` - 大版本号（默认：1.0.0）
- `dockerRepository` - 镜像仓库地址
- `sqServerUrl` - SonarQube 内网地址
- `sqDashboardUrl` - SonarQube 外网地址

### 3. Vue3MicroSvcDistPipeline.groovy

**功能：** Vue3 微服务前端分发流水线

- 验证预构建的 dist 目录
- SonarQube 代码扫描
- Docker 多架构镜像构建与推送
- Kubernetes 发布

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

Vue3MicroSvcDistPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'your-frontend-service',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001'
])
```

**参数说明：** 与 Vue3MicroSvcPipeline 相同

### 4. GolangMicroSvcPipeline.groovy

**功能：** Golang 微服务通用流水线

- SonarQube 代码扫描
- Go 交叉编译（linux/arm64，禁用 CGO）
- Docker 多架构镜像构建与推送
- Kubernetes 发布

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

GolangMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'your-go-service',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImage` - 基础镜像（默认：alpine:latest）
- `buildImage` - 构建镜像（默认：golang:1.25）
- `svcName` - 服务名（必填）
- `version` - 大版本号（默认：1.0.0）
- `sqServerUrl` - SonarQube 内网地址
- `sqDashboardUrl` - SonarQube 外网地址
- `dockerRepository` - 镜像仓库地址

### 5. CXXLibraryPipeline.groovy

**功能：** C++ 库项目发布流水线

- 支持 Autotools 和 CMake 构建系统
- 代码覆盖率测试（LCOV）
- 静态代码分析（cppcheck）
- SonarQube 代码质量扫描
- 生成文档（Doxygen）
- 上传到 Nexus 仓库
- 多平台支持

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

CXXLibraryPipeline([
        robotId    : 'your-dingtalk-robot-id',
        projectName: 'protobuf-c',
        version    : '1.0.0',
        buildSystem: 'both', // autotools, cmake, both
        buildType  : 'Release',
        enableTests: 'true',
        enableDocs : 'true'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `projectName` - 项目名称（必填）
- `version` - 版本号（默认：1.0.0）
- `buildDir` - 构建输出目录（默认：build）
- `buildType` - 构建类型（默认：Release）
- `buildSystem` - 构建系统（autotools/cmake/both）
- `installDir` - 安装目录（默认：install）
- `buildImage` - 构建镜像（默认：xin8/devops/cxx:latest）
- `sqCliImage` - SonarQube 扫描镜像（默认：xin8/devops/sonar-scanner-cli:latest）
- `enableTests` - 是否运行测试（默认：true）
- `enableDocs` - 是否生成文档（默认：true）
- `cmakeFlags` - 自定义 CMake 参数
- `sqServerUrl` - SonarQube 内网地址
- `sqDashboardUrl` - SonarQube 外网地址
- `nexusUrl` - Nexus 仓库地址
- `nexusRepo` - Nexus 仓库名称

### 6. PythonMicroSvcPipeline.groovy

**功能：** Python 微服务通用流水线

- Python 构建与测试（支持 pip/uv 依赖管理）
- SonarQube 代码扫描
- PyInstaller 打包（可选）
- Docker 多架构镜像构建与推送
- Kubernetes 发布

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

PythonMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'your-python-service',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001',
        k8sServerUrl    : 'https://your-k8s-api:6443'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImage` - 基础镜像（默认：python:3.12-alpine）
- `buildImage` - 构建镜像（默认：xin8/devops/python:latest）
- `svcName` - 服务名（必填）
- `version` - 大版本号（默认：1.0.0）
- `mainFilePath` - 主文件路径（默认：main.py）
- `sourceDir` - 源码目录（默认：src）
- `testDir` - 测试目录（默认：test）
- `sqServerUrl` - SonarQube 内网地址
- `sqDashboardUrl` - SonarQube 外网地址
- `dockerRepository` - 镜像仓库地址
- `k8sServerUrl` - k8s API 地址

### 7. PythonLibraryPipeline.groovy

**功能：** Python 公共依赖项目通用流水线

- Python 构建与测试（支持 pip/uv 依赖管理）
- SonarQube 代码扫描
- Python 包构建与发布（sdist + wheel）
- 推送到 Nexus 私有仓库

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

PythonLibraryPipeline([
        robotId : 'your-dingtalk-robot-id',
        svcName : 'your-python-library',
        version : '1.0.0',
        nexusUrl: 'http://your-nexus:8081'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImage` - 基础镜像（默认：python:3.12-alpine）
- `buildImage` - 构建镜像（默认：xin8/devops/python:latest）
- `svcName` - 服务名（必填）
- `version` - 大版本号（默认：1.0.0）
- `sourceDir` - 源码目录（默认：src）
- `testDir` - 测试目录（默认：test）
- `sqServerUrl` - SonarQube 内网地址
- `sqDashboardUrl` - SonarQube 外网地址
- `nexusUrl` - Nexus 仓库地址

### 8. MavenLibraryPipeline.groovy

**功能：** Maven 库项目发布流水线

- Maven 构建与部署（deploy）
- 推送到 Maven 仓库
- 钉钉通知

**使用方法：**

```groovy
@Library('jenkins-pipeline-libraries') _

MavenLibraryPipeline([
        robotId     : 'your-dingtalk-robot-id',
        builderImage: 'maven:3.9.11-amazoncorretto-17'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `baseImg` - 基础镜像（默认：openjdk:17.0-slim）
- `builderImage` - 构建镜像（默认：maven:3.9.11-amazoncorretto-17）
- `sqDashboardUrl` - SonarQube 外网地址

## 🔧 ops 运维工具

### 1. K8sDeployService.groovy

**功能：** Kubernetes 部署服务

- 根据模板渲染部署文件
- 执行 k8s 部署操作
- 支持前端和后端服务部署
- 集成钉钉通知

**使用方法：**

```groovy
import xyz.dev.ops.deploy.K8sDeployService

def k8sDeployService = new K8sDeployService(this)
k8sDeployService.deploy([
        robotId         : 'your-dingtalk-robot-id',
        serviceName     : 'your-service',
        namespace       : 'micro-svc-dev',
        dockerRepository: 'your-registry.com:5001',
        imageName       : 'micro-svc/your-service',
        version         : '1.0.0-abc12345',
        k8sServerUrl    : 'https://your-k8s-api:6443',
        k8sDeployFileId : 'deployment-micro-svc-template'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（可选）
- `serviceName` - 服务名（必填）
- `namespace` - 命名空间（必填）
- `dockerRepository` - 镜像仓库地址（必填）
- `imageName` - 镜像名（必填）
- `version` - 镜像版本（必填）
- `k8sServerUrl` - k8s API 地址（可选）
- `k8sDeployFileId` - Config File Provider 模板文件ID（必填）

### 2. K8sDeployConfigTool.groovy

**功能：** Kubernetes 配置工具

- 模板占位符替换
- Ingress 配置修改
- Nginx 路由规则生成

**主要方法：**

- `create(Map<String, Object> config)` - 创建部署配置
- `generateNginxConfigSnippet(List<Map<String, String>> backendServices)` - 生成 Nginx 配置
- `generateIngressPaths(List<Map<String, String>> backendServices)` - 生成 Ingress 路径

### 3. MavenUtils.groovy

**功能：** Maven 工具类

- 读取 POM 信息
- 执行 Maven 表达式

**主要方法：**

- `readArtifactId()` - 读取 artifactId
- `readVersion()` - 读取版本号
- `readPackaging()` - 读取打包类型
- `evaluate(String expression)` - 执行 Maven 表达式

**使用方法：**

```groovy
import xyz.dev.ops.maven.MavenUtils

def mvnUtils = new MavenUtils(this)
def artifactId = mvnUtils.readArtifactId()
def version = mvnUtils.readVersion()
```

### 4. DingTalk.groovy

**功能：** 钉钉通知工具

- 生成变更记录
- 发送 Markdown 格式通知
- 支持成功/失败状态通知
- 集成 SonarQube 质量报告链接

**使用方法：**

```groovy
import xyz.dev.ops.notify.DingTalk

def dingTalk = new DingTalk()
dingTalk.post([
        robotId    : 'your-dingtalk-robot-id',
        jobName    : 'your-job-name',
        sqServerUrl: 'http://your-sonar-server:9000'
])
```

**参数说明：**

- `robotId` - 钉钉机器人ID（必填）
- `jobName` - 任务名称（必填）
- `reason` - 失败原因（可选）
- `title` - 自定义标题（可选）
- `sqServerUrl` - SonarQube 服务地址（可选）

### 5. LcovReportToSonarReportAdapter.groovy

**功能：** LCOV 到 SonarQube 覆盖率报告转换适配器

- 解析 LCOV 格式的覆盖率数据文件
- 转换为 SonarQube 兼容的 XML 格式覆盖率报告
- 提供覆盖率统计信息

**使用方法：**

```groovy
import xyz.dev.ops.adapter.LcovCoverageReportAdapter

def stats = LcovCoverageReportAdapter.convert(
        'reports/coverage.info',
        'reports/sonar-coverage.xml'
)
```

**返回统计信息：**

- `totalFiles` - 总文件数
- `totalLines` - 总行数
- `coveredLines` - 覆盖行数
- `coveragePercent` - 覆盖率百分比
- `err` - 错误信息

### 6. UnitTestReportToSonarReportAdapter.groovy

**功能：** 单元测试报告转换适配器

- 解析测试日志文件
- 转换为 SonarQube 兼容的 XML 格式测试报告
- 提供测试统计信息

**使用方法：**

```groovy
import xyz.dev.ops.adapter.LcovUnitTestReportAdapter

def testStats = LcovUnitTestReportAdapter.convert(
        'test-suite.log',
        'reports/test-results.xml'
)
```

## 🐳 Docker Compose 部署

### Jenkins 服务部署

项目提供了 `docker-compose.jenkins.yml` 文件，用于快速部署 Jenkins 服务：

```bash
# 创建网络（如果不存在）
docker network create registry-net

# 启动 Jenkins 服务
docker-compose -f docker-compose.jenkins.yml up -d
```

**服务配置：**

- **端口映射**: `8666:8080` (Web 访问端口)
- **数据持久化**: `./data/jenkins/jenkins_home:/var/jenkins_home`
- **Docker 支持**: 挂载 Docker 命令和 socket
- **特权模式**: 支持 Docker-in-Docker 构建

**访问地址：**

- Jenkins Web UI: `http://localhost:8666`
- 初始密码：查看容器日志 `docker logs jenkins`

### 环境要求

- Docker 19.03+ (支持 buildx)
- Docker Compose 1.27+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

## ⚙️ 配置要求

### Jenkins 凭据配置

1. **docker-registry-secret** - Docker 仓库认证
2. **sonarqube-token-secret** - SonarQube Token
3. **gitlab-secret** - GitLab 认证（Golang 项目）
4. **nexus-credentials** - Nexus 仓库认证（C++ 项目）
5. **k8s-config** - Kubernetes 配置文件

### Config File Provider 配置

1. **deployment-micro-svc-template** - 微服务部署模板
2. **deployment-front-end-template** - 前端部署模板
3. **42697037-54bd-44a1-80c2-7a97d30f2266** - Maven settings.xml

### 环境变量

- `DOCKER_REPOSITORY` - Docker 仓库地址
- `K8S_SERVER_URL` - Kubernetes API 地址
- `SONAR_SERVER_URL` - SonarQube 服务器地址

## 📝 使用示例

### 1. Maven 微服务项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

MavenMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'user-service',
        dockerRepository: 'your-registry.com:5001',
        k8sServerUrl    : 'https://your-k8s-api:6443'
])
```

### 2. Vue3 前端项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

Vue3MicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'frontend-admin',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001',
        sqServerUrl     : 'http://172.29.35.103:9000',
        sqDashboardUrl  : 'http://8.145.35.103:9000'
])
```

### 3. Golang 微服务项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

GolangMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'api-gateway',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001'
])
```

### 4. Python 微服务项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

PythonMicroSvcPipeline([
        robotId         : 'your-dingtalk-robot-id',
        svcName         : 'python-api-service',
        version         : '1.0.0',
        dockerRepository: 'your-registry.com:5001',
        k8sServerUrl    : 'https://your-k8s-api:6443'
])
```

### 5. Python 库项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

PythonLibraryPipeline([
        robotId : 'your-dingtalk-robot-id',
        svcName : 'python-common-utils',
        version : '1.0.0',
        nexusUrl: 'http://your-nexus:8081'
])
```

### 6. C++ 库项目 Jenkinsfile

```groovy
@Library('jenkins-pipeline-libraries') _

CXXLibraryPipeline([
        robotId       : 'your-dingtalk-robot-id',
        projectName   : 'protobuf-c',
        version       : '1.0.0',
        buildSystem   : 'both',
        buildType     : 'Release',
        enableTests   : 'true',
        enableDocs    : 'true',
        sqServerUrl   : 'http://172.29.35.103:9000',
        sqDashboardUrl: 'http://8.145.35.103:9000',
        nexusUrl      : 'http://172.29.35.103:8081',
        nexusRepo     : 'c-cpp-raw-hosted'
])
```

### 7. 多技术栈统一流水线

```groovy
@Library('jenkins-pipeline-libraries') _

pipeline {
    agent any

    parameters {
        string(name: 'SERVICE_NAME', defaultValue: 'my-service', description: '服务名称')
        string(name: 'VERSION', defaultValue: '1.0.0', description: '版本号')
        choice(name: 'PIPELINE_TYPE', choices: ['maven', 'vue3', 'golang', 'python', 'python-lib', 'cxx'], description: '流水线类型')
    }

    stages {
        stage('选择流水线') {
            steps {
                script {
                    def commonConfig = [
                            robotId         : 'your-dingtalk-robot-id',
                            dockerRepository: 'your-registry.com:5001'
                    ]

                    switch (params.PIPELINE_TYPE) {
                        case 'maven':
                            MavenMicroSvcPipeline(commonConfig + [
                                    svcName: params.SERVICE_NAME
                            ])
                            break
                        case 'vue3':
                            Vue3MicroSvcPipeline(commonConfig + [
                                    svcName: params.SERVICE_NAME,
                                    version: params.VERSION
                            ])
                            break
                        case 'golang':
                            GolangMicroSvcPipeline(commonConfig + [
                                    svcName: params.SERVICE_NAME,
                                    version: params.VERSION
                            ])
                            break
                        case 'python':
                            PythonMicroSvcPipeline(commonConfig + [
                                    svcName: params.SERVICE_NAME,
                                    version: params.VERSION
                            ])
                            break
                        case 'python-lib':
                            PythonLibraryPipeline(commonConfig + [
                                    svcName: params.SERVICE_NAME,
                                    version: params.VERSION
                            ])
                            break
                        case 'cxx':
                            CXXLibraryPipeline(commonConfig + [
                                    projectName: params.SERVICE_NAME,
                                    version    : params.VERSION,
                                    buildSystem: 'both'
                            ])
                            break
                    }
                }
            }
        }
    }
}
```

## 🎯 最佳实践

### 1. 项目结构建议

```
your-project/
├── Jenkinsfile              # 流水线配置
├── Dockerfile              # 容器化配置
├── docker-compose.yml      # 本地开发环境
├── src/                    # 源代码
├── tests/                  # 测试代码
├── docs/                   # 文档
└── .gitignore              # Git 忽略文件
```

### 2. 分支策略

- **main/master**: 生产环境分支
- **pre**: 预发布分支
- **develop**: 开发分支
- **feature/***: 功能分支

### 3. 版本管理

- **生产版本**: `v1.0.0` (pre 分支)
- **开发版本**: `1.0.0-abc12345` (其他分支)

### 4. 环境配置

- **开发环境**: `micro-svc-dev` 命名空间
- **测试环境**: `micro-svc-test` 命名空间
- **生产环境**: `micro-svc-prod` 命名空间

### 5. 监控和通知

- 集成钉钉机器人通知
- 配置 SonarQube 质量门禁
- 设置构建失败自动回滚
- 定期清理构建历史

## 🚨 注意事项

### 1. 镜像构建顺序

- 必须先构建 SonarQube 扫描器镜像 (`build-env/sonar-scanner-cli`)
- 再构建 C++ 构建环境镜像 (`build-env/cxx`)

### 2. 权限配置

- 确保 Jenkins 节点有 Docker 和 Kubernetes 操作权限
- 配置 Docker Registry 访问权限
- 设置 SonarQube Token 权限

### 3. 网络访问

- 确保能够访问 Docker Registry、SonarQube、Kubernetes API
- 配置防火墙规则允许必要端口访问
- 设置代理服务器（如需要）

### 4. 模板文件

- 确保 Config File Provider 中的模板文件配置正确
- 验证 Kubernetes 部署模板的占位符
- 检查 Maven settings.xml 配置

### 5. 凭据安全

- 妥善保管各种认证凭据，避免泄露
- 定期轮换密码和 Token
- 使用 Jenkins 凭据管理功能

### 6. 性能优化

- 配置 Docker 构建缓存
- 设置 Maven/NPM 本地缓存
- 优化 Jenkins 节点资源配置

## 🔧 故障排除

### 常见问题

1. **Docker 构建失败**
    - 检查 Docker daemon 是否运行
    - 验证 buildx 插件是否安装
    - 确认镜像仓库访问权限

2. **SonarQube 扫描失败**
    - 检查 SonarQube 服务是否可访问
    - 验证 Token 是否有效
    - 确认项目配置是否正确

3. **Kubernetes 部署失败**
    - 检查 kubeconfig 配置
    - 验证命名空间是否存在
    - 确认镜像拉取权限

4. **钉钉通知失败**
    - 检查机器人 Webhook URL
    - 验证网络连接
    - 确认消息格式正确

## 📞 支持

如有问题或建议，请联系开发团队或提交 Issue。

---

**版本：** 2.0.0  
**更新日期：** 2024年12月  
**维护团队：** DevOps Team
