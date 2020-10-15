pipeline {
  agent { label 'Linux-Office' }
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
          withEnv(["MVN_HOME=${tool name: 'Maven 3', type: 'hudson.tasks.Maven$MavenInstallation'}", "JAVA_HOME=${tool name: 'Corretto 11', type: 'jdk'}"]) {
            sh "$MVN_HOME/bin/mvn -Dmaven.test.skip=true -DARCHITECT_P2_DIR=/srv/architect_p2 -DARCHITECT_DEPS_P2_DIR=/srv/architect_p2 clean package"
          }
        }
        archiveArtifacts artifacts: 'org.sonarlint.eclipse.site/target/*.zip'
      }
    }
  }
}
