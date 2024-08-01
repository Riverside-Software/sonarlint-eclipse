pipeline {
  agent { label 'Windows-Office02' }
  options {
    buildDiscarder(logRotator(daysToKeepStr:'10'))
    timeout(time: 15, unit: 'MINUTES')
    skipDefaultCheckout()
    disableConcurrentBuilds()
  }

  stages {
    stage ('Build sonarlint-eclipse') {
      steps {
        checkout([$class: 'GitSCM', branches: scm.branches, extensions: scm.extensions + [[$class: 'CleanCheckout']], userRemoteConfigs: scm.userRemoteConfigs])
        script {
          withEnv(["MVN_HOME=${tool name: 'Maven 3', type: 'hudson.tasks.Maven$MavenInstallation'}", "JAVA_HOME=${tool name: 'JDK17', type: 'jdk'}", "JAVA_HOME_11_X64=${tool name: 'Corretto 11', type: 'jdk'}", "JAVA_HOME_17_X64=${tool name: 'JDK17', type: 'jdk'}"]) {
            withCredentials([string(credentialsId: 'KEYSTORE_ALIAS', variable: 'KEYSTORE_ALIAS'), string(credentialsId: 'KEYSTORE_PASS', variable: 'KEYSTORE_PASS')]) {
              bat "$MVN_HOME\\bin\\mvn --toolchains .cirrus/toolchains.xml -Dmaven.test.skip=true -DARCHITECT_P2_DIR=/C:/Java/architect_p2 -DARCHITECT_DEPS_P2_DIR=/C:/Java/architect_p2 -P sign clean verify"
            }
          }
        }
        archiveArtifacts artifacts: 'org.sonarlint.eclipse.site/target/*.zip'
      }
    }
  }
}
