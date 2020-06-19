import mb.jenkins.pipeline.Slack

def call(Map args) {
  boolean slack = false
  String slackChannel = '#spoofax-dev'

  pipeline {
    agent none
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
      skipDefaultCheckout()
    }
    stages {
      stage('Trigger') {
        steps {
          build '/spoofax-trigger'
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
