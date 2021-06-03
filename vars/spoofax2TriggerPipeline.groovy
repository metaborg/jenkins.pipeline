import mb.jenkins.pipeline.Slack

def call(Map args) {
  boolean slack = false
  String slackChannel = '#spoofax-dev'

  pipeline {
    agent { label 'spoofax-buildenv-jenkins' }
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
      skipDefaultCheckout()
    }
    stages {
      stage('Trigger') {
        steps {
          build job: '/spoofax-trigger', propagate: false, wait: false
        }
      }
    }
    post {
      failure {
        script {
          if(slack) {
            new Slack().sendFailure(slackChannel)
          }
        }
      }
      fixed {
        script {
          if(slack) {
            new Slack().sendFixed(slackChannel)
          }
        }
      }
    }
  }
}
