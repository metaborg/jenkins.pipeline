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
  // Maven options
  String mavenBuildLifecycles
  String mavenGlobalSettingsFilePath
  String mavenSettingsFilePath
  String mavenGlobalSettingsConfig
  String mavenSettingsConfig
  String mavenOpts
  // Deploy options
  boolean deploy
  String deployServerId
  boolean deployRelease
  String deployReleaseTagPattern
  String deployReleaseServerId
  String deployReleaseUrl
  boolean deploySnapshot
  String deploySnapshotBranchPattern
  String deploySnapshotServerId
  String deploySnapshotUrl
  // Archive options
  boolean archive
  String archivePattern
  String archiveExcludes
  // Slack options
  boolean slack
  String slackChannel
  // Derived options
  String mavenCommand
  String eclipseQualifier
  String deployCommandSuffix

  pipeline {
    agent { label 'spoofax-buildenv-jenkins' }
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
    triggers {
      upstream(upstreamProjects: upstreamProjects, threshold: hudson.model.Result.SUCCESS)
    }
    options {
      buildDiscarder logRotator(artifactNumToKeepStr: '3')
      disableConcurrentBuilds()
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
            mavenGlobalSettingsConfig = options.getString('mavenGlobalSettingsConfig', mavenGlobalSettingsFilePath == null ? 'metaborg-release-snapshot-global-maven-config' : null)
            mavenSettingsConfig = options.getString('mavenSettingsConfig', mavenSettingsFilePath == null ? 'metaborg-deploy-maven-config' : null)
            mavenOpts = options.getString('mavenOpts', '-Xmx1G -Xss16M')
            // Deploy options
            deploy = options.getBoolean('deploy', false)
            deployServerId = options.getString('deployServerId', 'metaborg-nexus')
            deployRelease = options.getBoolean('deployRelease', deploy)
            deployReleaseTagPattern = options.getString('deployReleaseTagPattern', 'v*')
            deployReleaseServerId = options.getString('deployReleaseServerId', deployServerId)
            deployReleaseUrl = options.getString('deployReleaseUrl', null)
            deploySnapshot = options.getBoolean('deploySnapshot', deploy)
            deploySnapshotBranchPattern = options.getString('deploySnapshotBranchPattern', 'master')
            deploySnapshotServerId = options.getString('deploySnapshotServerId', deployServerId)
            deploySnapshotUrl = options.getString('deploySnapshotUrl', null)
            // Archive options
            archive = options.getBoolean('archive', true)
            archivePattern = options.getString('archivePattern', '**/target/site/')
            archiveExcludes = options.getString('archiveExcludes', null)
            // Slack options
            slack = options.getBoolean('slack', false)
            slackChannel = options.getString('slackChannel', null)
            // Derived options
            mavenCommand = "mvn -B -e"
            eclipseQualifier = sh(returnStdout: true, script: 'date +%Y%m%d%H%M').trim()
            deployCommandSuffix = "-DskipTests -Dmaven.test.skip=true -DforceContextQualifier=$eclipseQualifier${(deployReleaseServerId != null && deployReleaseUrl != null) ? " -DaltReleaseDeploymentRepository='$deployReleaseServerId::default::$deployReleaseUrl'" : ''}${(deploySnapshotServerId != null && deploySnapshotUrl != null) ? " -DaltSnapshotDeploymentRepository='$deploySnapshotServerId::default::$deploySnapshotUrl'" : ''}"
          }
        }
      }

      stage('Print versions') {
        steps {
          withMaven(
            globalMavenSettingsFilePath: mavenGlobalSettingsFilePath,
            mavenSettingsFilePath: mavenSettingsFilePath,
            globalMavenSettingsConfig: mavenGlobalSettingsConfig,
            mavenSettingsConfig: mavenSettingsConfig,
            mavenOpts: mavenOpts
          ) {
            sh 'env'
            sh 'bash --version'
            sh 'git --version'
            sh "$JAVA_HOME/bin/java -version"
            sh "$JAVA_HOME/bin/javac -version"
            sh "$mavenCommand --version"
          }
        }
      }

      stage('Build') {
        steps {
          withMaven(
            globalMavenSettingsFilePath: mavenGlobalSettingsFilePath,
            mavenSettingsFilePath: mavenSettingsFilePath,
            globalMavenSettingsConfig: mavenGlobalSettingsConfig,
            mavenSettingsConfig: mavenSettingsConfig,
            mavenOpts: mavenOpts
          ) {
            sh "$mavenCommand -U $mavenBuildLifecycles -DforceContextQualifier=$eclipseQualifier"
          }
        }
      }

      stage('Deploy Release') {
        when {
          expression { return deploy }
          expression { return deployRelease }
          tag deployReleaseTagPattern
          not { changeRequest() }
        }
        steps {
          withMaven(
            globalMavenSettingsFilePath: mavenGlobalSettingsFilePath,
            mavenSettingsFilePath: mavenSettingsFilePath,
            globalMavenSettingsConfig: mavenGlobalSettingsConfig,
            mavenSettingsConfig: mavenSettingsConfig,
            mavenOpts: mavenOpts
          ) {
            sh "$mavenCommand deploy -P release $deployCommandSuffix"
          }
        }
      }

      stage('Deploy Snapshot') {
        when {
          expression { return deploy }
          expression { return deploySnapshot }
          branch deploySnapshotBranchPattern
          not { changeRequest() }
        }
        steps {
          withMaven(
            globalMavenSettingsFilePath: mavenGlobalSettingsFilePath,
            mavenSettingsFilePath: mavenSettingsFilePath,
            globalMavenSettingsConfig: mavenGlobalSettingsConfig,
            mavenSettingsConfig: mavenSettingsConfig,
            mavenOpts: mavenOpts
          ) {
            sh "$mavenCommand deploy $deployCommandSuffix"
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
