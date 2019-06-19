def call(Map args) {
  String  upstreamProjects
  boolean deleteWorkspaceAfterBuild

  boolean gradleWrapper
  String  gradleJvmArgs
  boolean gradleBuildCache
  boolean gradleDaemon
  boolean gradleParallel
  boolean gradleRefreshDependencies

  boolean publish
  boolean publishTaggedOnly
  String  publishCredentialsId
  String  publishUsernameProperty
  String  publishPasswordProperty

  String gradleCommand

  pipeline {
    agent any

    environment {
      JENKINS_NODE_COOKIE = 'dontKillMe' // Necessary for the Gradle daemon to be kept alive.
      LC_ALL = 'C' // Fix assertion in locale stuff (https://stackoverflow.com/a/49796618/499240).
    }

    stages {
      stage('Prepare') {
        steps {
          script {
            def propsFile = 'jenkins.properties'
            def hasPropsFile = fileExists(propsFile)
            def props = hasPropsFile ? readProperties(file: propsFile) : new HashMap()


            if(props['upstreamProjects'] != null) {
              upstreamProjects = props['upstreamProjects']
            } else if(args?.upstreamProjects != null && args.upstreamProjects instanceof List<String> && args.upstreamProjects.length() > 0) {
              upstreamProjects = args.upstreamProjects.join(',')
            } else {
              upstreamProjects = ''
            }

            if(props['deleteWorkspaceAfterBuild'] != null) {
              deleteWorkspaceAfterBuild = props['deleteWorkspaceAfterBuild'] == 'true'
            } else if(args?.deleteWorkspaceAfterBuild != null) {
              deleteWorkspaceAfterBuild = args.deleteWorkspaceAfterBuild
            } else {
              deleteWorkspaceAfterBuild = false
            }


            if(props['gradleWrapper'] != null) {
              gradleWrapper = props['gradleWrapper'] == 'true'
            } else if(args?.gradleWrapper != null) {
              gradleWrapper = args.gradleWrapper
            } else {
              gradleWrapper = fileExists('gradlew')
            }

            if(props['gradleJvmArgs'] != null) {
              gradleJvmArgs = props['gradleJvmArgs']
            } else if(args?.gradleJvmArgs != null) {
              gradleJvmArgs = args.gradleJvmArgs
            } else {
              gradleJvmArgs = '-Xmx2G -Xss16M'
            }

            if(props['gradleBuildCache'] != null) {
              gradleBuildCache = props['gradleBuildCache'] == 'true'
            } else if(args?.gradleBuildCache != null) {
              gradleBuildCache = args.gradleBuildCache
            } else {
              gradleBuildCache = false
            }

            if(props['gradleDaemon'] != null) {
              gradleDaemon = props['gradleDaemon'] == 'true'
            } else if(args?.gradleDaemon != null) {
              gradleDaemon = args.gradleDaemon
            } else {
              gradleDaemon = true
            }

            if(props['gradleParallel'] != null) {
              gradleParallel = props['gradleParallel'] == 'true'
            } else if(args?.gradleParallel != null) {
              gradleParallel = args.gradleParallel
            } else {
              gradleParallel = false
            }

            if(props['gradleRefreshDependencies'] != null) {
              gradleRefreshDependencies = props['gradleRefreshDependencies'] == 'true'
            } else if(args?.gradleRefreshDependencies != null) {
              gradleRefreshDependencies = args.gradleRefreshDependencies
            } else {
              gradleRefreshDependencies = upstreamProjects != ''
            }


            if(props['publish'] != null) {
              publish = props['publish'] == 'true'
            } else if(args?.publish != null) {
              publish = args.publish
            } else {
              publish = true
            }

            if(props['publishTaggedOnly'] != null) {
              publishTaggedOnly = props['publishTaggedOnly'] == 'true'
            } else if(args?.publishTaggedOnly != null) {
              publishTaggedOnly = args.publishTaggedOnly
            } else {
              publishTaggedOnly = BRANCH_NAME == 'master'
            }

            if(props['publishCredentialsId'] != null) {
              publishCredentialsId = props['publishCredentialsId']
            } else if(args?.publishCredentialsId != null) {
              publishCredentialsId = args.publishCredentialsId
            } else {
              publishCredentialsId = 'metaborg-artifacts'
            }

            if(props['publishUsernameProperty'] != null) {
              publishUsernameProperty = props['publishUsernameProperty']
            } else if(args?.publishUsernameProperty != null) {
              publishUsernameProperty = args.publishUsernameProperty
            } else {
              publishUsernameProperty = 'publish.repository.metaborg.artifacts.username'
            }

            if(props['publishPasswordProperty'] != null) {
              publishPasswordProperty = props['publishPasswordProperty']
            } else if(args?.publishPasswordProperty != null) {
              publishPasswordProperty = args.publishPasswordProperty
            } else {
              publishPasswordProperty = 'publish.repository.metaborg.artifacts.password'
            }

            gradleCommand = "${gradleWrapper ? './gradlew' : 'gradle'} -Dorg.gradle.jvmargs='$gradleJvmArgs' -Dorg.gradle.caching=${String.valueOf(gradleBuildCache)} -Dorg.gradle.daemon=${String.valueOf(gradleDaemon)} -Dorg.gradle.parallel=${String.valueOf(gradleParallel)}"
          }
        }
      }

      stage('Refresh dependencies') {
        when { expression { return gradleRefreshDependencies } }
        steps {
          sh "$gradleCommand --refresh-dependencies"
        }
      }

      stage('Build') {
        steps {
          sh "$gradleCommand build"
        }
      }

      stage('Publish') {
        when {
          expression { return publish }
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
    }

    post {
      cleanup {
        script {
          if(deleteWorkspaceAfterBuild == true) {
            cleanWs()
          }
        }
      }
    }

    triggers {
      upstream(upstreamProjects: upstreamProjects, threshold: hudson.model.Result.SUCCESS)
    }
  }
}
