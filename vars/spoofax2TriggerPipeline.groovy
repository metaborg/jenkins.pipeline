import mb.jenkins.pipeline.Options
import mb.jenkins.pipeline.ReadProperties
import mb.jenkins.pipeline.Slack

def call(Map args) {
  boolean slack
  String slackChannel

  pipeline {
    agent none
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
      skipDefaultCheckout()
    }
    stages {
      stage('Prepare') {
        steps {
          script {
            def options = new Options(args, new ReadProperties().readProps())
            slack = options.getBoolean('slack', false)
            slackChannel = options.getString('slackChannel', '#spoofax-dev')
          }
        }
      }
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
