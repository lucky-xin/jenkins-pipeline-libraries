# Jenkins Pipeline Shared Library

本项目是 Jenkins Groovy 共享库，提供构建/发布常用能力：
- Kubernetes 部署工具：`xyz.dev.ops.deploy.K8sDeployService`
- 钉钉通知工具：`xyz.dev.ops.notify.DingTalk`

请在 Jenkins 全局系统配置中添加：
- 全局共享库（指向本仓库）
- Credentials：`k8s-config`（kubeconfig）、其他私有仓库凭证按需
- Config File Provider：部署模板（deployment.tpl 对应的 fileId）

## 引用共享库

在 Jenkinsfile 顶部：

```groovy
@Library('your-shared-lib') _
```

## 使用说明

### 1) 钉钉通知 `DingTalk`

```groovy
import xyz.dev.ops.notify.DingTalk

pipeline {
  agent any
  stages {
    stage('Notify') {
      steps {
        script {
          new DingTalk().post([
            robotId    : 'your-robot-id',
            jobName    : env.JOB_NAME,
            reason     : '',                 // 失败原因（可选）
            title      : env.JOB_NAME,       // 自定义标题（可选）
            sqServerUrl: 'https://sonar.xx'  // SonarQube地址（可选）
          ])
        }
      }
    }
  }
}
```

### 2) Kubernetes 部署 `K8sDeployService`

要求：
- Jenkins 凭据中存在 `k8s-config` kubeconfig
- Jenkins Config File Provider 中存在部署模板（fileId 见下示例）

部署模板需要包含以下占位符：`${APP_NAME}` `${NAMESPACE}` `${DOCKER_REPOSITORY}` `${IMAGE_NAME}` `${VERSION}`

```groovy
import xyz.dev.ops.deploy.K8sDeployService

pipeline {
  agent any
  stages {
    stage('K8s Deploy') {
      steps {
        script {
          def k8s = new K8sDeployService(this)
          k8s.deploy([
            robotId               : 'your-robot-id',
            serviceName           : 'demo-svc',
            namespace             : 'dev',
            dockerRepository      : 'registry.example.com:5000',
            imageName             : 'micro-svc/demo-svc',
            version               : '1.0.0-abcdef12',
            k8sServerUrl          : 'https://kubernetes.default.svc.cluster.local',
            k8sDeployImage        : 'bitnami/kubectl:latest',
            k8sDeployArgs: '-u root:root --entrypoint ""',
            k8sDeployFileId   : 'deployment-micro-svc-template'
          ])
        }
      }
    }
  }
}
```

> 提示：示例中注释掉的 `kubectl apply` 可在 `K8sDeployService` 中开启以实际执行部署。

## 迁移说明（vars → 类调用）

历史上的 `vars/*.groovy` 已移除。可参考如下迁移：
- 原 `mvn_micro_svc(...)` → 使用 Maven 步骤 + `K8sDeployService.deploy(map)`
- 原 `go_micro_svc(...)`  → 使用 Go 构建步骤 + `K8sDeployService.deploy(map)`
- 原 `mvn_library(...)`   → 纯 Maven 发布，用 `DingTalk().post(map)` 做构建状态通知

## 开发约定
- 统一使用 Map 作为方法入参，提供默认值并做关键字段断言
- 共享逻辑沉淀在 `src/` 下类中，Jenkinsfile 通过导入类调用
- 重要步骤失败时务必补充钉钉告警

## FrontendDeploymentConfigurator 使用说明

FrontendDeploymentConfigurator 是一个用于动态配置前端部署模板的工具类。它可以根据给定的配置修改 Kubernetes Ingress 资源中的 nginx 配置片段和路由规则。

### 主要功能

1. 动态生成 nginx 配置片段 (`nginx.ingress.kubernetes.io/configuration-snippet`)
2. 动态配置 Ingress 路径规则 (`rules.http.paths`)

### 使用方法

#### 1. 导入类

```groovy

```

#### 2. 调用 configureDeploymentTemplate 方法
```groovy
String result = FrontendDeploymentConfigurator.configureDeploymentTemplate(templateContent, config)
```

参数说明:
- `templateContent`: 原始的 YAML 模板内容
- `config`: 配置映射，包含以下键值:
  - `serviceMappings`: 服务映射配置 (Map<String, String>)
  - `backendServiceName`: 后端服务名称 (String)

### 使用样例

```groovy

// 读取模板文件
def templateFile = new File('resources/template/deployment-front-end-template.yaml')
def templateContent = templateFile.text

// 定义配置
def config = [
        backendServiceName: 'my-backend-service',
        serviceMappings   : [
                'user-service'   : 'user-svc',
                'order-service'  : 'order-svc',
                'product-service': 'product-svc'
        ]
]

// 配置模板
String configuredYaml = FrontendDeploymentConfigurator.configureDeploymentTemplate(templateContent, config)

// 保存配置后的文件
def outputFile = new File('output/deployment-configured.yaml')
outputFile.text = configuredYaml
```

### 配置说明

#### serviceMappings
`serviceMappings` 是一个 Map，定义了路径前缀到服务名称的映射关系:
```groovy
serviceMappings: [
    'user-service': 'user-svc',     // /svc/user-service/* -> user-svc
    'order-service': 'order-svc',   // /svc/order-service/* -> order-svc
]
```

这将生成:
1. nginx 配置片段中的路径识别和重写规则
2. Ingress 规则中的相应路径配置

### 注意事项
1. 模板文件必须包含 Ingress 资源定义
2. 确保模板中包含必要的占位符变量如 `${APP_NAME}`、`${NAMESPACE}` 等
3. 生成的配置会替换模板中原有的 Ingress 配置部分
