# Jenkins 共享库 Groovy 脚本使用说明

这是一个 Jenkins 共享库项目，提供了多种类型的微服务流水线脚本和运维工具，支持 Maven、Vue3、Golang、C++ 等不同技术栈的构建和部署。

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
- `./build.sh` - 构建并推送镜像到仓库（默认）
- `./build.sh -l` - 仅构建本地镜像（不推送）
- `./build.sh -t v1.0.0` - 指定镜像标签
- `./build.sh --platforms linux/amd64` - 指定平台

**镜像信息：**
- 镜像名称：`xin8/sonar-scanner-cli:latest`
- 支持平台：`linux/arm64,linux/amd64`
- 用途：代码质量扫描

### 第二步：构建 C++ 构建环境镜像

执行 C++ 开发环境镜像构建：

```bash
cd build-env/cplus
./build-docker-image.sh
```

**镜像信息：**
- 镜像名称：`xin8/dev-env/cplus:latest`
- 支持平台：`linux/amd64,linux/arm64/v8`
- 包含工具：autoconf, automake, libtool, protoc, cmake
- 用途：C++ 项目构建环境

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
    robotId: 'your-dingtalk-robot-id',
    svcName: 'your-service-name',
    dockerRepository: 'your-registry.com:5001',
    k8sServerUrl: 'https://your-k8s-api:6443'
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
    robotId: 'your-dingtalk-robot-id',
    svcName: 'your-frontend-service',
    version: '1.0.0',
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
    robotId: 'your-dingtalk-robot-id',
    svcName: 'your-frontend-service',
    version: '1.0.0',
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
    robotId: 'your-dingtalk-robot-id',
    svcName: 'your-go-service',
    version: '1.0.0',
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

### 5. CPlusLibraryPipeline.groovy

**功能：** C++ 库项目发布流水线
- 支持 Autotools 和 CMake 构建系统
- 生成文档（Doxygen）
- 上传到 Nexus 仓库
- 多平台支持

**使用方法：**
```groovy
@Library('jenkins-pipeline-libraries') _

CPlusLibraryPipeline([
    robotId: 'your-dingtalk-robot-id',
    projectName: 'protobuf-c',
    version: '1.0.0',
    buildSystem: 'both', // autotools, cmake, both
    buildType: 'Release',
    enableTests: 'true',
    enableDocs: 'true'
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
- `builderImage` - 构建镜像（默认：xin8/protobuf-c-builder:latest）
- `enableTests` - 是否运行测试（默认：true）
- `enableDocs` - 是否生成文档（默认：true）
- `cmakeFlags` - 自定义 CMake 参数

### 6. MavenLibraryPipeline.groovy

**功能：** Maven 库项目发布流水线
- Maven 构建与部署（deploy）
- 推送到 Maven 仓库
- 钉钉通知

**使用方法：**
```groovy
@Library('jenkins-pipeline-libraries') _

MavenLibraryPipeline([
    robotId: 'your-dingtalk-robot-id',
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

**使用方法：**
```groovy
import xyz.dev.ops.deploy.K8sDeployService

def k8sDeployService = new K8sDeployService(this)
k8sDeployService.deploy([
    robotId: 'your-dingtalk-robot-id',
    serviceName: 'your-service',
    namespace: 'micro-svc-dev',
    dockerRepository: 'your-registry.com:5001',
    imageName: 'micro-svc/your-service',
    version: '1.0.0-abc12345',
    k8sServerUrl: 'https://your-k8s-api:6443',
    k8sDeployFileId: 'deployment-micro-svc-template'
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
- 执行 Maven 命令

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

**使用方法：**
```groovy
import xyz.dev.ops.notify.DingTalk

def dingTalk = new DingTalk()
dingTalk.post([
    robotId: 'your-dingtalk-robot-id',
    jobName: 'your-job-name',
    sqServerUrl: 'http://your-sonar-server:9000'
])
```

**参数说明：**
- `robotId` - 钉钉机器人ID（必填）
- `jobName` - 任务名称（必填）
- `reason` - 失败原因（可选）
- `title` - 自定义标题（可选）
- `sqServerUrl` - SonarQube 服务地址（可选）

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

### 完整的 Jenkinsfile 示例

```groovy
@Library('jenkins-pipeline-libraries') _

pipeline {
    agent any
    
    parameters {
        string(name: 'SERVICE_NAME', defaultValue: 'my-service', description: '服务名称')
        string(name: 'VERSION', defaultValue: '1.0.0', description: '版本号')
        choice(name: 'PIPELINE_TYPE', choices: ['maven', 'vue3', 'golang'], description: '流水线类型')
    }
    
    stages {
        stage('选择流水线') {
            steps {
                script {
                    switch(params.PIPELINE_TYPE) {
                        case 'maven':
                            MavenMicroSvcPipeline([
                                robotId: 'your-dingtalk-robot-id',
                                svcName: params.SERVICE_NAME,
                                dockerRepository: 'your-registry.com:5001'
                            ])
                            break
                        case 'vue3':
                            Vue3MicroSvcPipeline([
                                robotId: 'your-dingtalk-robot-id',
                                svcName: params.SERVICE_NAME,
                                version: params.VERSION,
                                dockerRepository: 'your-registry.com:5001'
                            ])
                            break
                        case 'golang':
                            GolangMicroSvcPipeline([
                                robotId: 'your-dingtalk-robot-id',
                                svcName: params.SERVICE_NAME,
                                version: params.VERSION,
                                dockerRepository: 'your-registry.com:5001'
                            ])
                            break
                    }
                }
            }
        }
    }
}
```

## 🚨 注意事项

1. **镜像构建顺序**：必须先构建 SonarQube 扫描器镜像，再构建 C++ 构建环境镜像
2. **权限配置**：确保 Jenkins 节点有 Docker 和 Kubernetes 操作权限
3. **网络访问**：确保能够访问 Docker Registry、SonarQube、Kubernetes API
4. **模板文件**：确保 Config File Provider 中的模板文件配置正确
5. **凭据安全**：妥善保管各种认证凭据，避免泄露

## 📞 支持

如有问题或建议，请联系开发团队或提交 Issue。

---

**版本：** 1.0.0  
**更新日期：** 2024年12月
