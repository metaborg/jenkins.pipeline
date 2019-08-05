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
  // Gradle options
  boolean gradleWrapper
  String gradleJvmArgs
  boolean gradleBuildCache
  boolean gradleDaemon
  boolean gradleParallel
  boolean gradleRefreshDependencies
  // Publish options
  boolean publish
  boolean publishTaggedOnly
  String publishCredentialsId
  String publishUsernameProperty
  String publishPasswordProperty
  // Archive options
  boolean archive
  String archivePattern
  String archiveExcludes
  // Slack options
  boolean slackNotify
  String slackNotifyChannel
  // Derived options
  String gradleCommand

  pipeline {
    agent any
    environment {
      LC_ALL = 'C' // Fix assertion in locale stuff (https://stackoverflow.com/a/49796618/499240).
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
            // Gradle options
            gradleWrapper = options.getBoolean('gradleWrapper', fileExists('gradlew'))
            gradleJvmArgs = options.getString('gradleJvmArgs', '-Xmx2G -Xss16M')
            gradleBuildCache = options.getBoolean('gradleBuildCache', false)
            gradleDaemon = options.getBoolean('gradleDaemon', true)
            gradleParallel = options.getBoolean('gradleParallel', false)
            gradleRefreshDependencies = options.getBoolean('gradleRefreshDependencies', upstreamProjects != '')
            // Publish options
            publish = options.getBoolean('publish', true)
            publishTaggedOnly = options.getBoolean('publishTaggedOnly', BRANCH_NAME == 'master')
            publishCredentialsId = options.getString('publishCredentialsId', 'metaborg-artifacts')
            publishUsernameProperty = options.getString('publishUsernameProperty', 'publish.repository.metaborg.artifacts.username')
            publishPasswordProperty = options.getString('publishPasswordProperty', 'publish.repository.metaborg.artifacts.password')
            // Archive options
            archive = options.getBoolean('archive', false)
            archivePattern = options.getString('archivePattern', null)
            archiveExcludes = options.getString('archiveExcludes', null)
            // Slack options
            slackNotify = options.getBoolean('slackNotify', false)
            slackNotifyChannel = options.getString('slackNotifyChannel', null)
            // Derived options
            gradleCommand = "${gradleWrapper ? './gradlew' : 'gradle'} -Dorg.gradle.jvmargs='$gradleJvmArgs' -Dorg.gradle.caching=${String.valueOf(gradleBuildCache)} -Dorg.gradle.daemon=${String.valueOf(gradleDaemon)} -Dorg.gradle.parallel=${String.valueOf(gradleParallel)}"
          }
        }
      }

      stage('Build') {
        steps {
          sh "$gradleCommand${gradleRefreshDependencies ? ' --refresh-dependencies' : ''} build"
        }
      }

      stage('Publish') {
        when {
          expression { return publish }
          not { changeRequest() }
          anyOf {
            not { expression { return publishTaggedOnly } }
            allOf { expression { return publishTaggedOnly }; tag "*release-*" }
          }
        }
        steps {
          withCredentials([usernamePassword(credentialsId: publishCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sh "$gradleCommand publish -P$publishUsernameProperty=\$USERNAME -P$publishPasswordProperty=\$PASSWORD"
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
      always {
        junit testResults: '**/build/test-results/**/*.xml', allowEmptyResults: true
      }
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
