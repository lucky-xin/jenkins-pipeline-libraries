#!/usr/bin/env groovy
package xyz.dev.ops.deploy

import groovy.json.JsonOutput

def DeployReq_k8s(String __docker_tag, String __service_name, String __docker_repository, String __deploy_environment) {
    DOCKER_IMAGE = ("${__docker_repository}/${__service_name}:${__docker_tag}")
    // sh "mkdir -p ${env.WORKSPACE}deploy/roles/pistonint-ops_docker-container/tasks/"

    echo "\n========================================================================\n\n [  开始 Deploying  ] \n\n========================================================================\n"
    // def filename = 'deploy/pistonint-cloud.yaml'
    // def data = readYaml file: filename

    def __projects = readYaml text: libraryResource('projects.yaml')
    def __template = __projects["template"]


    def __svc_name = __service_name.replace("pistonint-", "")

    // echo "${__projects}"
    def __map, __namespace_name, __service, __spec_replicas, __spec_revisionHistoryLimit, __publish, __resources, __envFrom, __env
    __map = __projects[__service_name]["deploy_environment"][__deploy_environment]
    // echo "${__map}"
    __namespace_name = "namespace" in __map ? __map["namespace"] : __template["namespace"]
    __service = "service" in __map ? __map["service"] : __template["service"]
    __publish = "publish" in __map ? __map["publish"] : 'false'
    __spec_replicas = "replicas" in __map ? __map["replicas"] : __template["replicas"]
    __spec_revisionHistoryLimit = "revisionHistoryLimit" in __map ? __map["revisionHistoryLimit"] : __template["revisionHistoryLimit"]
    __resources = "resources" in __map ? __map["resources"] : __template["resources"]
    __envFrom = "envFrom" in __map ? __map["envFrom"] : __template["envFrom"]
    __env = "env" in __map ? __map["env"] : __template["env"]

    // echo "${__publish}"
    // echo "${__resources}"
    // echo "${__envFrom}"
    // echo "${__env}"

    def __Namespace = readYaml text: libraryResource('deploy/namespace.yaml')
    def __Deployment = readYaml text: libraryResource('deploy/deployment.yaml')
    def __Service = readYaml text: libraryResource('deploy/service.yaml')
    def __Ingress = readYaml text: libraryResource('deploy/ingress.yaml')
    def __IngressClass = readYaml text: libraryResource('deploy/ingress-class.yaml')

    // echo "${__Deployment}"

    // 部署变量

    def __kubeconfigId = ''
    def __dev_kubeconfigId = '8682018c-fff9-4684-b7c7-82af45bc31eb'
    def __pre_kubeconfigId = '4030a65f-e7d4-4ed3-be26-60d3a2cb26d9'

    def __pdns_content = ""
    def __dev_pdns_content = "d-k8s-ingress.dev.pistonint.com."  //dev入口网关地址
    def __pre_pdns_content = "p-k8s-ingress.pre.pistonint.com."  //pre入口网关地址

    def __pdns_records = readJSON text: '{"rrsets": [ { "name": "templet", "changetype": "REPLACE", "records": [ { "content": "templet", "disabled": false } ], "ttl": 300, "type": "CNAME" } ] }'

    if (__deploy_environment == "dev") {
        __spec_replicas = "replicas" in __map ? __map["replicas"] : 2
        __spec_revisionHistoryLimit = "revisionHistoryLimit" in __map ? __map["revisionHistoryLimit"] : 3
        __kubeconfigId = "${__dev_kubeconfigId}"
        __pdns_content = "${__dev_pdns_content}"
    } else if (__deploy_environment == "pre") {
        __namespace_name = "${__namespace_name}-pre"
        __spec_replicas = "replicas" in __map ? __map["replicas"] : 1
        __spec_revisionHistoryLimit = "revisionHistoryLimit" in __map ? __map["revisionHistoryLimit"] : 3
        __kubeconfigId = "${__pre_kubeconfigId}"
        __pdns_content = "${__pre_pdns_content}"
    } else {
        echo "Yes! you are right, is nothing to change you belive, bye..."
    }

    //修改命名空间
    __Namespace["metadata"]["name"] = "${__namespace_name}"
    __Namespace["metadata"]["labels"]["kubernetes.io/metadata.name"] = "${__namespace_name}"

    __Deployment["metadata"]["name"] = "${__service_name}"
    __Deployment["metadata"]["namespace"] = "${__namespace_name}"
    __Deployment["metadata"]["labels"]["app"] = "${__service_name}"
    __Deployment["metadata"]["labels"]["svc_name"] = "${__svc_name}"
    __Deployment["metadata"]["labels"]["version"] = "${__docker_tag}"
    __Deployment["spec"]["replicas"] = "${__spec_replicas}" as int
    __Deployment["spec"]["revisionHistoryLimit"] = "${__spec_revisionHistoryLimit}" as int

    __Deployment["spec"]["selector"]["matchLabels"]["app"] = "${__service_name}"
    __Deployment["spec"]["selector"]["matchLabels"]["svc_name"] = "${__svc_name}"

    __Deployment["spec"]["template"]["metadata"]["labels"]["app"] = "${__service_name}"
    __Deployment["spec"]["template"]["metadata"]["labels"]["svc_name"] = "${__svc_name}"
    __Deployment["spec"]["template"]["metadata"]["labels"]["version"] = "${__docker_tag}"
    // echo "${__Deployment["spec"]["template"]["metadata"]["labels"]["version"]}"

    //["containers"]:[[name:pistonint-customization-global-ucar, image:reg.pistonint.com/pistonint-customization-global-ucar:v1.0.0.2, imagePullPolicy:Always, envFrom:[[configMapRef:[name:spring-boot-config]], [secretRef:[name:spring-boot-secret]]], resources:[limits:[cpu:500m, memory:2048Mi], requests:[cpu:500m, memory:1048Mi]], livenessProbe:[httpGet:[path:/actuator/health, port:http-port], initialDelaySeconds:30, timeoutSeconds:5, periodSeconds:30, successThreshold:1, failureThreshold:5], readinessProbe:[httpGet:[path:/actuator/health, port:http-port], initialDelaySeconds:30, timeoutSeconds:5, periodSeconds:10, successThreshold:1, failureThreshold:5], ports:[[name:http-port, containerPort:19084, protocol:TCP]]]]
    //["containers"][0]:[name:pistonint-customization-global-ucar, image:reg.pistonint.com/pistonint-customization-global-ucar:v1.0.0.2, imagePullPolicy:Always, envFrom:[[configMapRef:[name:spring-boot-config]], [secretRef:[name:spring-boot-secret]]], resources:[limits:[cpu:500m, memory:2048Mi], requests:[cpu:500m, memory:1048Mi]], livenessProbe:[httpGet:[path:/actuator/health, port:http-port], initialDelaySeconds:30, timeoutSeconds:5, periodSeconds:30, successThreshold:1, failureThreshold:5], readinessProbe:[httpGet:[path:/actuator/health, port:http-port], initialDelaySeconds:30, timeoutSeconds:5, periodSeconds:10, successThreshold:1, failureThreshold:5], ports:[[name:http-port, containerPort:19084, protocol:TCP]]]
    // echo "${__Deployment["spec"]["template"]["spec"]["containers"]["name"][0]}"
    __Deployment["spec"]["template"]["spec"]["containers"][0]["name"] = "${__service_name}"
    __Deployment["spec"]["template"]["spec"]["containers"][0]["image"] = "${DOCKER_IMAGE}"
    // __Deployment["spec"]["template"]["spec"]["containers"][0]["resources"] = __resources 
    __Deployment["spec"]["template"]["spec"]["containers"][0]["envFrom"] = __envFrom
    __Deployment["spec"]["template"]["spec"]["containers"][0]["env"] = __env
    // __Deployment["spec"]["template"]["spec"]["containers"][0]["livenessProbe"] =
    // __Deployment["spec"]["template"]["spec"]["containers"][0]["readinessProbe"] =
    // __Deployment["spec"]["template"]["spec"]["containers"][0]["ports"] = "${__Service}["ports"]"

    // echo "${__service_port}"

    // __Deployment["spec"]["template"]["spec"]["containers"][0]["ports"][0]["containerPort"] = "${__service_port}" as int
    // echo "${__Deployment["spec"]["template"]["spec"]["containers"][0]["ports"][0]}"
    // __Deployment["spec"]["template"]["spec"]["containers"]["image"] = "${DOCKER_IMAGE}"
    // __Deployment["spec"]["template"]["spec"]["containers"]["ports"]["containerPort"] = "${__service_port}"

    __Service["metadata"]["name"] = "${__service_name}"
    __Service["metadata"]["namespace"] = "${__namespace_name}"
    __Service["spec"]["selector"]["app"] = "${__service_name}"
    __Service["spec"]["selector"]["svc_name"] = "${__svc_name}"

    __Ingress["metadata"]["name"] = "${__service_name}"
    __Ingress["metadata"]["namespace"] = "${__namespace_name}"

    __Ingress["spec"]["rules"][0]["host"] = "${__deploy_environment}-${__svc_name}.svc.pistonint.com"
    __Ingress["spec"]["rules"][0]["http"]["paths"][0]["backend"]["service"]["name"] = "${__service_name}"

    //  writeYaml 不能覆盖,先删除。
    sh(script: "rm -rf namespace.yaml deployment.yaml service.yaml ingress.yaml ingress-class.yaml data.json", returnStdout: true, returnStatus: true)

    writeYaml file: 'namespace.yaml', data: __Namespace
    writeYaml file: 'deployment.yaml', data: __Deployment
    writeYaml file: 'service.yaml', data: __Service
    writeYaml file: 'ingress.yaml', data: __Ingress
    writeYaml file: 'ingress-class.yaml', data: __IngressClass

    // sh "cat deployment.yaml"
    // def __base_url = __project_info["metadata"]["labels"]["svc_name"]
    // echo "${__project_info["metadata"]["labels"]}"
    // echo "${__base_url}"
    // def __api_url = __project_info[__Project_Name]["deploy_environment"][__branch]["api_url"]
    // def __project_port = __project_info[__Project_Name]["deploy_environment"][__branch]["container_port"]


    // sh "rm $filename"
    // writeYaml file: filename, data: data

    archiveArtifacts(artifacts: 'deployment.yaml', fingerprint: true)
    archiveArtifacts(artifacts: 'service.yaml', fingerprint: true)

    withCredentials([file(credentialsId: "${__kubeconfigId}", variable: 'KUBECONFIG')]) {

        // sh 'kubectl --kubeconfig $KUBECONFIG get pod -A'

        sh """
            kubectl apply -f namespace.yaml
            kubectl apply -f deployment.yaml
            kubectl apply -f service.yaml
        """
        // 暂时全发布
        // if ( __publish == "true" ){
        archiveArtifacts(artifacts: 'ingress.yaml', fingerprint: true)
        sh """
                kubectl apply -f ingress.yaml    
                kubectl apply -f ingress-class.yaml    
            """
        // }
    }

    __pdns_records["rrsets"][0]["records"][0]["content"] = "${__pdns_content}" as String

    __pdns_records["rrsets"][0]["name"] = "${__deploy_environment}-${__svc_name}.svc.pistonint.com." as String

    //到字符串
    //String __pdns_records_json = writeJSON returnText: true, json: __pdns_records
    writeJSON file: 'data.json', json: __pdns_records
    withCredentials([string(credentialsId: '57a59f5f-333f-4da0-bf31-4f32f86c13c9', variable: 'PDNSAPITOKEN')]) {
        sh '''
        curl -X PATCH -d @data.json -H X-API-Key:$PDNSAPITOKEN "http://192.168.1.5:8081/api/v1/servers/localhost/zones/svc.pistonint.com"
        '''
        // 上面dns 更新地址也需要匿掉 cat ？ env ？ 
    }


//    automated_test_endpoint = env.AUTOMATED_TEST_ENDPOINT
    automated_test_url = "https://dev-automated-test.svc.pistonint.com/job"

    def connection = new URL("${automated_test_url}").openConnection()
    connection.with {
        doOutput = true
        requestContentType = 'application/json'
        requestMethod = 'POST'
        outputStream.withWriter {
            writer ->
                writer.write(JsonOutput.toJson([
                        "project_name": "${__service_name}",
                        "branch"      : "dev",
                        "seconds"     : 60
                ]))
        }
    }
    echo "\n=================================❤️🉐🏍️🌰🔄🅾🅾=======================================\n"
}


                