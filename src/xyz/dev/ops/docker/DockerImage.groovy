#!/usr/bin/env groovy
package xyz.dev.ops.docker

def BuildReq(String __version, String __service_name, String __packaging, String __docker_repository, String __docker_tag) {
    // sh "curl -O -u '$RAW_REPOSITORY_CREDS_USR:$RAW_REPOSITORY_CREDS_PSW' https://nex.pistonint.com/repository/raw/'$POM_ARTIFACTID/$POM_VERSION/$POM_ARTIFACTID.$POM_PACKAGING'"
    APPLICATION_NAME = ("$__service_name")
    DOCKER_IMAGE = ("${__docker_repository}/${__service_name}:${__docker_tag}")
    JAR_FILE = ("${__service_name}-${__version}.${__packaging}")
    echo "\n========================================================================\n\n [  开始构建 Docker镜像  ] \n\n========================================================================\n"
    docker.withRegistry('https://reg.pistonint.com/v2/', '3dab90c9-59ba-4219-b163-70d6d04a8310') {
        def customImage = docker.build("${DOCKER_IMAGE}", "--build-arg JAR_FILE='${JAR_FILE}' --build-arg APPLICATION_NAME='${APPLICATION_NAME}' .")
        customImage.push()
    }

    echo "\n=================================❤️🉐🏍️🌰🔄🅾🅾=======================================\n"
}
