import mb.jenkins.pipeline.Options
import mb.jenkins.pipeline.ReadProperties
import mb.jenkins.pipeline.Slack

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
  String gradleArgs
  String gradleBuildTasks
  String gradlePublishTasks
  boolean gradleStacktrace
  boolean gradleBuildCache
  boolean gradleDaemon
  boolean gradleParallel
  boolean gradleRefreshDependencies
  // Release options
  String releaseTagPattern
  // Build options
  boolean buildMasterBranch
  boolean buildDevelopBranch
  boolean buildOtherBranch
  boolean buildTag
  boolean buildReleaseTag
  boolean buildChangeRequest
  // Publish options
  boolean publish
  boolean publishReleaseTagOnly
  String publishCredentialsId
  String publishUsernameProperty
  String publishPasswordProperty
  // Archive options
  boolean archive
  String archivePattern
  String archiveExcludes
  // Slack options
  boolean slack
  String slackChannel
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
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
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
            gradleArgs = options.getString('gradleArgs', '')
            gradleBuildTasks = options.getString('gradleBuildTasks', 'buildAll')
            gradlePublishTasks = options.getString('gradlePublishTasks', 'publishAll')
            gradleStacktrace = options.getBoolean('gradleStacktrace', true)
            gradleBuildCache = options.getBoolean('gradleBuildCache', false)
            gradleDaemon = options.getBoolean('gradleDaemon', true)
            gradleParallel = options.getBoolean('gradleParallel', true)
            gradleRefreshDependencies = options.getBoolean('gradleRefreshDependencies', upstreamProjects != '')
            // Release options
            releaseTagPattern = options.getString('releaseTagPattern', '*release-*')
            // Build options
            buildMasterBranch = options.getBoolean('buildMasterBranch', true)
            buildDevelopBranch = options.getBoolean('buildDevelopBranch', true)
            buildOtherBranch = options.getBoolean('buildOtherBranch', true)
            buildTag = options.getBoolean('buildTag', true)
            buildReleaseTag = options.getBoolean('buildReleaseTag', true)
            buildChangeRequest = options.getBoolean('buildChangeRequest', false)
            // Publish options
            publish = options.getBoolean('publish', true)
            publishReleaseTagOnly = options.getBoolean('publishReleaseTagOnly', BRANCH_NAME == 'master')
            publishCredentialsId = options.getString('publishCredentialsId', 'metaborg-artifacts')
            publishUsernameProperty = options.getString('publishUsernameProperty', 'publish.repository.metaborg.artifacts.username')
            publishPasswordProperty = options.getString('publishPasswordProperty', 'publish.repository.metaborg.artifacts.password')
            // Archive options
            archive = options.getBoolean('archive', false)
            archivePattern = options.getString('archivePattern', null)
            archiveExcludes = options.getString('archiveExcludes', null)
            // Slack options
            slack = options.getBoolean('slack', false)
            slackChannel = options.getString('slackChannel', null)
            // Derived options
            gradleCommand = "${gradleWrapper ? './gradlew' : 'gradle'} -Dorg.gradle.jvmargs='$gradleJvmArgs' $gradleArgs ${gradleStacktrace ? '--stacktrace' : ''} -Dorg.gradle.caching=${String.valueOf(gradleBuildCache)} -Dorg.gradle.daemon=${String.valueOf(gradleDaemon)} -Dorg.gradle.parallel=${String.valueOf(gradleParallel)}"
          }
        }
      }

      stage('Build') {
        steps {
          when {
            anyOf {
              allOf { expression { return buildMasterBranch }; branch 'master' }
              allOf { expression { return buildDevelopBranch }; branch 'develop' }
              allOf { expression { return buildOtherBranch }; not { branch 'master' }; not { branch 'develop' } }
              allOf { expression { return buildTag }; buildingTag() }
              allOf { expression { return buildReleaseTag }; tag releaseTagPattern }
              allOf { expression { return buildChangeRequest }; changeRequest() }
            }
          }
          sh "$gradleCommand${gradleRefreshDependencies ? ' --refresh-dependencies' : ''} $gradleBuildTasks"
        }
      }

      stage('Publish') {
        when {
          expression { return publish }
          not { changeRequest() }
          anyOf {
            not { expression { return publishReleaseTagOnly } }
            allOf { expression { return publishReleaseTagOnly }; tag releaseTagPattern }
          }
        }
        steps {
          withCredentials([usernamePassword(credentialsId: publishCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sh "$gradleCommand $gradlePublishTasks -P$publishUsernameProperty=\$USERNAME -P$publishPasswordProperty=\$PASSWORD"
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
