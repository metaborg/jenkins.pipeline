import mb.jenkins.pipeline.Options
import mb.jenkins.pipeline.ReadProperties
import mb.jenkins.pipeline.Slack

def call(Map args) {
  // General options
  /** Whether to enable upstream project triggers for tags.
   * (`enableUpstreamProjectsForTags`, boolean, default: false) */
  boolean enableUpstreamProjectsForTags
  /** A comma-separated list of upstream projects that would trigger a build of this project.
   *  (`upstreamProjects`, a single string or a list of strings, default: null) */
  String upstreamProjects
  /** Whether a GitHub push webhook message might trigger a build.
   * (`enableGitHubWebhook`, boolean, default: false) */
  boolean enableGitHubWebhook
  /** Whether to clean the workspace after a build finishes.
   * (`deleteWorkspaceAfterBuild`, boolean, default: false) */
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
  String gradleMaxWorkers
  boolean gradleRefreshDependencies

  // Release options
  String releaseTagPattern

  // Branch options
  String mainBranch
  String developBranch

  // Build options
  boolean build
  String preBuildCommand
  String preBuildTask
  boolean buildMainBranch
  boolean buildDevelopBranch
  boolean buildOtherBranch
  boolean buildTag
  boolean buildReleaseTag
  boolean buildChangeRequest

  // Publish options
  boolean publish
  boolean publishMainBranch
  boolean publishDevelopBranch
  boolean publishOtherBranch
  boolean publishReleaseTag
  String publishCredentialsId
  String publishUsernameProperty
  String publishPasswordProperty

  // Archive options
  boolean archive
  String archivePattern
  String archiveExcludes
  boolean archiveAllowEmpty

  // Slack options
  boolean slack
  String slackChannel

  // Derived options
  String gradleCommand

  pipeline {
    agent { label 'spoofax3' }
    // In Jenkins, under Tools, add a JDK Installation with:
    // - Name: JDK 11
    // - JAVA_HOME: /usr/lib/jvm/java-11-openjdk-amd64
    // - Install automatically: false
    // Ensure the JDK 11 is available in the Spoofax Docker image at the specified path.
    tools {
      jdk 'JDK 11'
    }
    environment {
      LC_ALL = 'C' // Fix assertion in locale stuff (https://stackoverflow.com/a/49796618/499240).
    }
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
      // Disable concurrently building the same project
      // Especially for `devenv`, concurrent builds can quickly overwhelm the available memory of the builder
      disableConcurrentBuilds()
    }
    stages {
      stage('Prepare') {
        steps {
          script {
            def options = new Options(args, new ReadProperties().readProps())
            // General options
            enableUpstreamProjectsForTags = options.getBoolean('enableUpstreamProjectsForTags', false)
            def upstreamProjectsInput = options.getObject('upstreamProjects', null)
            if(env.TAG_NAME && !enableUpstreamProjectsForTags) {
              upstreamProjects = ''
            } else if(upstreamProjectsInput != null) {
              if(upstreamProjectsInput instanceof String) {
                upstreamProjects = upstreamProjectsInput
              } else if(upstreamProjectsInput instanceof List<String> && upstreamProjectsInput.size() > 0) {
                upstreamProjects = upstreamProjectsInput.join(',')
              } else {
                upstreamProjects = upstreamProjectsInput.toString()
              }
            } else {
              upstreamProjects = ''
            }
            enableGitHubWebhook = options.getBoolean('enableGitHubWebhook', false)
            properties([pipelineTriggers(
              [upstream(
                upstreamProjects: upstreamProjects,
                threshold: 'SUCCESS'
              )] +
              (enableGitHubWebhook ? [githubPush()] : [])
            )])
            deleteWorkspaceAfterBuild = options.getBoolean('deleteWorkspaceAfterBuild', false)

            // Gradle options
            gradleWrapper = options.getBoolean('gradleWrapper', fileExists('gradlew'))
            gradleJvmArgs = options.getString('gradleJvmArgs', null)
            gradleArgs = options.getString('gradleArgs', '')
            gradleBuildTasks = options.getString('gradleBuildTasks', 'buildAll')
            gradlePublishTasks = options.getString('gradlePublishTasks', 'publishAll')
            gradleStacktrace = options.getBoolean('gradleStacktrace', true)
            gradleBuildCache = options.getBoolean('gradleBuildCache', false)
            gradleDaemon = options.getBoolean('gradleDaemon', true)
            gradleParallel = options.getBoolean('gradleParallel', true)
            gradleMaxWorkers = options.getString('gradleMaxWorkers', null)
            gradleRefreshDependencies = options.getBoolean('gradleRefreshDependencies', false)

            // Release options
            releaseTagPattern = options.getString('releaseTagPattern', '*release-*')

            // Branch options
            mainBranch = options.getString('mainBranch', 'master')
            developBranch = options.getString('developBranch', 'develop')

            // Build options
            build = options.getBoolean('build', true)
            preBuildCommand = options.getString('preBuildCommand', null)
            preBuildTask = options.getString('preBuildTask', null)
            buildMainBranch = options.getBoolean('buildMainBranch', true)
            buildDevelopBranch = options.getBoolean('buildDevelopBranch', true)
            buildOtherBranch = options.getBoolean('buildOtherBranch', true)
            buildTag = options.getBoolean('buildTag', true)
            buildReleaseTag = options.getBoolean('buildReleaseTag', true)
            buildChangeRequest = options.getBoolean('buildChangeRequest', false)

            // Publish options
            publish = options.getBoolean('publish', true)
            publishMainBranch = options.getBoolean('publishMainBranch', false)
            publishDevelopBranch = options.getBoolean('publishDevelopBranch', false)
            publishOtherBranch = options.getBoolean('publishOtherBranch', false)
            publishReleaseTag = options.getBoolean('publishReleaseTag', true)
            publishCredentialsId = options.getString('publishCredentialsId', 'metaborg-artifacts')
            publishUsernameProperty = options.getString('publishUsernameProperty', 'publish.repository.metaborg.artifacts.username')
            publishPasswordProperty = options.getString('publishPasswordProperty', 'publish.repository.metaborg.artifacts.password')

            // Archive options
            archive = options.getBoolean('archive', false)
            archivePattern = options.getString('archivePattern', null)
            archiveExcludes = options.getString('archiveExcludes', null)
            archiveAllowEmpty = options.getBoolean('archiveAllowEmpty', true)

            // Slack options
            slack = options.getBoolean('slack', false)
            slackChannel = options.getString('slackChannel', null)

            // Derived options
            gradleCommand = "${gradleWrapper ? './gradlew' : 'gradle'} ${gradleJvmArgs ? "-Dorg.gradle.jvmargs='$gradleJvmArgs'" : ''}$gradleArgs ${gradleStacktrace ? '--stacktrace' : ''} -Dorg.gradle.caching=${String.valueOf(gradleBuildCache)} -Dorg.gradle.daemon=${String.valueOf(gradleDaemon)} -Dorg.gradle.parallel=${String.valueOf(gradleParallel)}${gradleMaxWorkers ? " --max-workers=$gradleMaxWorkers" : ''}"
          }
        }
      }

      stage('Print versions') {
        steps {
          sh 'env'
          sh 'bash --version'
          sh 'git --version'
          sh 'java -version'
          sh 'javac -version'
          sh "${gradleWrapper ? './gradlew' : 'gradle'} --version"
        }
      }

      stage('Build') {
        when {
          expression { return build }
          anyOf {
            allOf { expression { return buildMainBranch }; branch mainBranch }
            allOf { expression { return buildDevelopBranch }; branch developBranch }
            allOf { expression { return buildOtherBranch }; not { branch mainBranch }; not { branch developBranch } }
            allOf { expression { return buildTag }; buildingTag() }
            allOf { expression { return buildReleaseTag }; tag releaseTagPattern }
            allOf { expression { return buildChangeRequest }; changeRequest() }
          }
        }
        steps {
          script {
            if(preBuildCommand != null || preBuildTask != null) { // Skip ssh-agent without pre-build actions.
              // Run under ssh-agent, as these commands typically check out additional code from git repositories.
              sshagent(['git-metaborgbot-ssh']) {
                if(preBuildCommand != null) {
                  sh preBuildCommand
                }
                if(preBuildTask != null) {
                  sh "$gradleCommand $preBuildTask"
                }
              }
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
            allOf { expression { return publishMainBranch }; branch mainBranch }
            allOf { expression { return publishDevelopBranch }; branch developBranch }
            allOf { expression { return publishOtherBranch }; not { branch mainBranch }; not { branch developBranch } }
            allOf { expression { return publishReleaseTag }; tag releaseTagPattern }
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
          archiveArtifacts(artifacts: archivePattern, excludes: archiveExcludes, allowEmptyArchive: archiveAllowEmpty, onlyIfSuccessful: true)
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
