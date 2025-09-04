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
            k8sDeployContainerArgs: '-u root:root --entrypoint ""',
            k8sDeploymentFileId   : 'deployment-micro-svc-template'
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
