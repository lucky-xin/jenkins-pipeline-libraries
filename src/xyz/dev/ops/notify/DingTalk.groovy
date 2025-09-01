package xyz.dev.ops.notify


def CreateMsg() {
    MAX_MSG_LEN = 100
    def changeString = ""
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            def outputDateFormat = "MM-dd HH:mm"
            def outputTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
            truncated_msg = entry.msg.take(MAX_MSG_LEN)
            commitTime = new Date(entry.timestamp).format(outputDateFormat, outputTimeZone)
            changeString += "> - ${truncated_msg} [${entry.author} ${commitTime}]\n"
        }
    }
    if (!changeString) {
        changeString = "> - No new changes"
    }
    return changeString
}

def post(String robotID, String jobName) {
    def changeString = CreateMsg()
    dingtalk(
            robot: robotID,
            type: 'MARKDOWN',
            title: "${jobName}",
            text: [
                    "# [${jobName}](${env.RUN_DISPLAY_URL})",
                    "------",
                    "- 任务：${env.JOB_NAME}",  // 部署至dev 任务 + 的环境
                    "- 状态：<font color=${currentBuild.currentResult == 'SUCCESS' ? '#00EE76' : '#EE0000'} >${currentBuild.currentResult}</font>", //
                    "- 执行：${currentBuild.buildCauses.shortDescription}",
                    "- 日志：[点击查看详情](${env.BUILD_URL}console)",
                    "### 更新记录:",
                    "${changeString}"
            ],
            // at: [
            //     '电话号码'
            // ]
    )
} 