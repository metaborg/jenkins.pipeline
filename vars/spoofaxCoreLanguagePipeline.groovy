import mb.jenkins.pipeline.Options
import mb.jenkins.pipeline.SlackMessage

def call(Map args) {
  // General options
  String upstreamProjects
  if(args?.upstreamProjects != null) {
    if(args.upstreamProjects instanceof String) {
      upstreamProjects = args.upstreamProjects
    } else if(args.upstreamProjects instanceof List<String> && args.upstreamProjects.size() > 0) {
      upstreamProjects = args.upstreamProjects.join(',')
    }
  } else {
    upstreamProjects = ''
  }
  boolean deleteWorkspaceAfterBuild
  // Maven options
  String mavenBuildLifecycles
  String mavenGlobalSettingsConfig
  String mavenGlobalSettingsFilePath
  String mavenSettingsConfig
  String mavenSettingsFilePath
  String mavenOpts
  // Deploy options
  boolean deploy
  boolean deployTaggedOnly
  // Archive options
  boolean archive
  String archivePattern
  String archiveExcludes
  // Slack options
  boolean slackNotify
  String slackNotifyChannel
  // Derived options
  String mavenCommand
  String eclipseQualifier

  pipeline {
    agent any
    environment {
      LC_ALL = 'C' // Fix assertion in locale stuff (https://stackoverflow.com/a/49796618/499240).
    }
    options {
      buildDiscarder(logRotator(artifactNumToKeepStr: '3'))
      disableConcurrentBuilds()
    }
    triggers {
      upstream(upstreamProjects: upstreamProjects, threshold: hudson.model.Result.SUCCESS)
    }

    stages {
      stage('Prepare') {
        steps {
          script {
            def options = new Options(args)
            // General options
            deleteWorkspaceAfterBuild = options.getBoolean('deleteWorkspaceAfterBuild', false)
            // Maven options
            mavenBuildLifecycles = options.getString('mavenBuildLifecycles', 'clean verify')
            mavenGlobalSettingsConfig = options.getString('mavenGlobalSettingsConfig', 'metaborg-mirror-global-maven-config')
            mavenGlobalSettingsFilePath = options.getString('mavenGlobalSettingsFilePath', null)
            mavenSettingsConfig = options.getString('mavenSettingsConfig', 'metaborg-release-snapshot-maven-config')
            mavenSettingsFilePath = options.getString('mavenSettingsFilePath', null)
            mavenOpts = options.getString('mavenOpts', '-Xmx1G -Xss16M')
            // Deploy options
            deploy = options.getBoolean('deploy', false)
            deployTaggedOnly = options.getBoolean('deployTaggedOnly', BRANCH_NAME == 'master')
            // Archive options
            archive = options.getBoolean('archive', true)
            archivePattern = options.getString('archivePattern', '**/target/site/')
            archiveExcludes = options.getString('archiveExcludes', null)
            // Slack options
            slackNotify = options.getBoolean('slackNotify', false)
            slackNotifyChannel = options.getString('slackNotifyChannel', null)
            // Derived options
            mavenCommand = "mvn -B -e"
            eclipseQualifier = sh(returnStdout: true, script: 'date +%Y%m%d%H%M').trim()
          }
        }
      }

      stage('Build') {
        steps {
          withMaven() {
            sh "$mavenCommand -U $mavenBuildLifecycles -DforceContextQualifier=$eclipseQualifier"
          }
        }
      }

      stage('Deploy') {
        when {
          expression { return deploy }
          not(changeRequest())
          anyOf {
            not { expression { return deployTaggedOnly } }
            allOf { expression { return deployTaggedOnly }; tag "*release-*" }
          }
        }
        steps {
          withMaven() {
            sh "$mavenCommand deploy -DforceContextQualifier=$eclipseQualifier"
          }
        }
      }

      stage('Archive') {
        when {
          expression { return archive }
        }
        steps {
          archiveArtifacts(artifacts: archivePattern, excludes: archiveExcludes, onlyIfSuccessful: true)
        }
      }
    }

    post {
      cleanup {
        script {
          if(deleteWorkspaceAfterBuild) {
            cleanWs()
          }
        }
      }
      fixed {
        script {
          if(slackNotify) {
            slackSend(channel: slackNotifyChannel, color: 'good', message: SlackMessage.create('fixed :party_parrot:'))
          }
        }
      }
      failure {
        script {
          if(slackNotify) {
            slackSend(channel: slackNotifyChannel, color: 'danger', message: SlackMessage.create('failed :facepalm:'))
          }
        }
      }
    }
  }
}
