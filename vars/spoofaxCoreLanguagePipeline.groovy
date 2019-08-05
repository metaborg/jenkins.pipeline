import mb.jenkins.pipeline.Options
import mb.jenkins.pipeline.ReadProperties
import mb.jenkins.pipeline.SlackMessage

def call(Map args) {
  // General options
  // Upstream projects are determined before stages execute, so cannot read from properties, only read from arguments.
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
  String mavenGlobalSettingsFilePath
  String mavenSettingsFilePath
  String mavenGlobalSettingsConfig
  String mavenSettingsConfig
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
            def options = new Options(args, new ReadProperties().readProps())
            // General options
            deleteWorkspaceAfterBuild = options.getBoolean('deleteWorkspaceAfterBuild', false)
            // Maven options
            mavenBuildLifecycles = options.getString('mavenBuildLifecycles', 'clean verify')
            mavenGlobalSettingsFilePath = options.getString('mavenGlobalSettingsFilePath', null)
            mavenSettingsFilePath = options.getString('mavenSettingsFilePath', null)
            mavenGlobalSettingsConfig = options.getString('mavenGlobalSettingsConfig', mavenGlobalSettingsFilePath == null ? 'metaborg-mirror-global-maven-config' : null)
            mavenSettingsConfig = options.getString('mavenSettingsConfig', mavenSettingsFilePath == null ? 'metaborg-release-snapshot-maven-config' : null)
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
          not { changeRequest() }
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
      fixed {
        script {
          if(slackNotify) {
            slackSend(channel: slackNotifyChannel, color: 'good', message: SlackMessage.create('fixed :party_parrot:', env))
          }
        }
      }
      failure {
        script {
          if(slackNotify) {
            slackSend(channel: slackNotifyChannel, color: 'danger', message: SlackMessage.create('failed :facepalm:', env))
          }
        }
      }
      cleanup {
        script {
          if(deleteWorkspaceAfterBuild) {
            cleanWs()
          }
        }
      }
    }
  }
}
