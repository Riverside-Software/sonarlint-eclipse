// Syntax check with this command line
// curl -k -X POST -F "jenkinsfile=<Jenkinsfile" https://ci.rssw.eu/pipeline-model-converter/validate

pipeline {
  agent { label 'master' }
  options {
    disableConcurrentBuilds()
    skipDefaultCheckout()
    timeout(time: 20, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }
  stages {
    stage('Build') {
      steps {
        checkout([$class: 'GitSCM', branches: scm.branches, extensions: scm.extensions + [[$class: 'CleanCheckout']], userRemoteConfigs: scm.userRemoteConfigs])
        script {
          withEnv(["PATH+MAVEN=${tool name: 'Maven 3', type: 'hudson.tasks.Maven$MavenInstallation'}/bin"]) {
            sh "mvn -Dmaven.test.skip=true -DARCHITECT_P2_DIR=/architect_p2 -DARCHITECT_DEPS_P2_DIR=/architect_p2 clean package"
          }
        }
        archiveArtifacts artifacts: 'org.sonarlint.eclipse.site/target/*.zip'
      }
    }
  }
}
