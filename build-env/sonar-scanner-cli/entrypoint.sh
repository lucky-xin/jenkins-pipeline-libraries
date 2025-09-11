#!/bin/bash

set -euo pipefail

declare -a args=()

add_env_var_as_env_prop() {
  if [[ -n "$1" ]]; then
    args+=("-D$2=$1")
  fi
}

# If there are certificates in /tmp/cacerts we will import them into the scanner truststore for backward compatibility
if [[ -d /tmp/cacerts ]]; then
  # shellcheck disable=SC2312
  if [[ -n "$(ls -A /tmp/cacerts 2>/dev/null)" ]]; then
    mkdir -p $SONAR_USER_HOME/ssl
    echo "WARNING: Importing certificates from /tmp/cacerts is deprecated. You should put your certificates into $SONAR_USER_HOME/ssl/truststore.p12"
    # we can't use the default "sonar" password as keytool requires a password with at least 6 characters
    args+=("-Dsonar.scanner.truststorePassword=changeit")
    # for older SQ versions < 10.6
    export SONAR_SCANNER_OPTS="${SONAR_SCANNER_OPTS:-} -Djavax.net.ssl.trustStore=$SONAR_USER_HOME/ssl/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
    for f in /tmp/cacerts/*
    do
      echo "Importing certificate: ${f}"
      keytool -importcert -storetype PKCS12 -file "${f}" -alias "$(basename "${f}")" -keystore $SONAR_USER_HOME/ssl/truststore.p12 -storepass changeit -trustcacerts -noprompt
    done
  fi
fi

# if nothing is passed, assume we want to run sonar-scanner
if [[ "$#" == 0 ]]; then
  set -- sonar-scanner
fi

# if first arg looks like a flag, assume we want to run sonar-scanner with flags
if [[ "${1#-}" != "${1}" ]] || ! command -v "${1}" > /dev/null; then
  set -- sonar-scanner "$@"
fi

if [[ "$1" = 'sonar-scanner' ]]; then
  # 基础认证配置
  add_env_var_as_env_prop "${SONAR_TOKEN:-}" "sonar.token"
  add_env_var_as_env_prop "${SONAR_LOGIN:-}" "sonar.login"
  add_env_var_as_env_prop "${SONAR_PASSWORD:-}" "sonar.password"
  add_env_var_as_env_prop "${SONAR_HOST_URL:-}" "sonar.host.url"
  
  # 项目基础配置
  add_env_var_as_env_prop "${SONAR_PROJECT_KEY:-}" "sonar.projectKey"
  add_env_var_as_env_prop "${SONAR_PROJECT_NAME:-}" "sonar.projectName"
  add_env_var_as_env_prop "${SONAR_PROJECT_VERSION:-}" "sonar.projectVersion"
  add_env_var_as_env_prop "${SONAR_PROJECT_BASE_DIR:-}" "sonar.projectBaseDir"
  add_env_var_as_env_prop "${SONAR_SOURCES:-}" "sonar.sources"
  add_env_var_as_env_prop "${SONAR_TESTS:-}" "sonar.tests"
  
  # 编码和语言配置
  add_env_var_as_env_prop "${SONAR_SOURCE_ENCODING:-UTF-8}" "sonar.sourceEncoding"
  add_env_var_as_env_prop "${SONAR_JAVA_BINARIES:-}" "sonar.java.binaries"
  add_env_var_as_env_prop "${SONAR_JAVA_LIBRARIES:-}" "sonar.java.libraries"
  add_env_var_as_env_prop "${SONAR_JAVA_SOURCE:-}" "sonar.java.source"
  add_env_var_as_env_prop "${SONAR_JAVA_TARGET:-}" "sonar.java.target"
  
  # Go 语言特定配置
  add_env_var_as_env_prop "${SONAR_GO_COVERAGE_REPORT_PATHS:-}" "sonar.go.coverage.reportPaths"
  add_env_var_as_env_prop "${SONAR_GO_TESTS_REPORT_PATHS:-}" "sonar.go.tests.reportPaths"
  
  # JavaScript/TypeScript 配置
  add_env_var_as_env_prop "${SONAR_JAVASCRIPT_LCOV_REPORTPATHS:-}" "sonar.javascript.lcov.reportPaths"
  add_env_var_as_env_prop "${SONAR_TYPESCRIPT_LCOV_REPORTPATHS:-}" "sonar.typescript.lcov.reportPaths"
  
  # Python 配置
  add_env_var_as_env_prop "${SONAR_PYTHON_COVERAGE_REPORTPATHS:-}" "sonar.python.coverage.reportPaths"
  add_env_var_as_env_prop "${SONAR_PYTHON_XUNIT_REPORTPATHS:-}" "sonar.python.xunit.reportPaths"
  
  # C# 配置
  add_env_var_as_env_prop "${SONAR_CS_DOTCOVER_REPORTPATHS:-}" "sonar.cs.dotcover.reportPaths"
  add_env_var_as_env_prop "${SONAR_CS_OPENCOVER_REPORTPATHS:-}" "sonar.cs.opencover.reportPaths"
  add_env_var_as_env_prop "${SONAR_CS_VSTEST_REPORTPATHS:-}" "sonar.cs.vstest.reportPaths"
  
  # 排除和包含配置
  add_env_var_as_env_prop "${SONAR_EXCLUSIONS:-}" "sonar.exclusions"
  add_env_var_as_env_prop "${SONAR_INCLUSIONS:-}" "sonar.inclusions"
  add_env_var_as_env_prop "${SONAR_TEST_EXCLUSIONS:-}" "sonar.test.exclusions"
  add_env_var_as_env_prop "${SONAR_TEST_INCLUSIONS:-}" "sonar.test.inclusions"
  add_env_var_as_env_prop "${SONAR_COVERAGE_EXCLUSIONS:-}" "sonar.coverage.exclusions"
  
  # 质量门和分支配置
  add_env_var_as_env_prop "${SONAR_BRANCH_NAME:-}" "sonar.branch.name"
  add_env_var_as_env_prop "${SONAR_PULLREQUEST_KEY:-}" "sonar.pullrequest.key"
  add_env_var_as_env_prop "${SONAR_PULLREQUEST_BRANCH:-}" "sonar.pullrequest.branch"
  add_env_var_as_env_prop "${SONAR_PULLREQUEST_BASE:-}" "sonar.pullrequest.base"
  
  # 扫描器配置
  add_env_var_as_env_prop "${SCANNER_WORKDIR_PATH:-}" "sonar.working.directory"
  add_env_var_as_env_prop "${SONAR_SCANNER_OPTS:-}" "sonar.scanner.opts"
  add_env_var_as_env_prop "${SONAR_VERBOSE:-}" "sonar.verbose"
  add_env_var_as_env_prop "${SONAR_LOG_LEVEL:-}" "sonar.log.level"
  
  # 组织和项目配置
  add_env_var_as_env_prop "${SONAR_ORGANIZATION:-}" "sonar.organization"
  add_env_var_as_env_prop "${SONAR_PROJECT_DESCRIPTION:-}" "sonar.projectDescription"
  
  # 数据库配置（如果需要）
  add_env_var_as_env_prop "${SONAR_JDBC_URL:-}" "sonar.jdbc.url"
  add_env_var_as_env_prop "${SONAR_JDBC_USERNAME:-}" "sonar.jdbc.username"
  add_env_var_as_env_prop "${SONAR_JDBC_PASSWORD:-}" "sonar.jdbc.password"
  
  # 代理配置
  add_env_var_as_env_prop "${SONAR_PROXY_HOST:-}" "sonar.proxyHost"
  add_env_var_as_env_prop "${SONAR_PROXY_PORT:-}" "sonar.proxyPort"
  add_env_var_as_env_prop "${SONAR_PROXY_USER:-}" "sonar.proxyUser"
  add_env_var_as_env_prop "${SONAR_PROXY_PASSWORD:-}" "sonar.proxyPassword"
  
  # 如果有额外的参数，则重新构建命令
  if [[ ${#args[@]} -ne 0 ]]; then
    set -- sonar-scanner "${args[@]}" "${@:2}"
  fi
fi

exec "$@"
