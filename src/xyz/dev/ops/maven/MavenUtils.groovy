package xyz.dev.ops.maven

class MavenUtils implements Serializable {
    private final def steps

    MavenUtils(def steps) {
        this.steps = steps
    }

    String evaluate(String expression, String additionalArgs = "") {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("expression must not be empty")
        }
        String cmd = "mvn -q -DforceStdout help:evaluate -Dexpression=${expression} ${additionalArgs}".trim()
        return steps.sh(returnStdout: true, script: cmd).trim()
    }

    String readArtifactId() {
        return evaluate("project.artifactId")
    }

    String readVersion() {
        return evaluate("project.version")
    }

    String readPackaging() {
        return evaluate("project.packaging")
    }
}


