def call(Map args) {
  boolean useWrapper
  boolean gradleRefreshDependencies
  boolean publish
  boolean publishTaggedOnly
  String publishCredentialsId
  String publishUsernameProperty
  String publishPasswordProperty

  String upstreamProjects
  
  String gradleCommand

  pipeline {
    agent any

    environment {
      JENKINS_NODE_COOKIE = 'dontKillMe' // Necessary for the Gradle daemon to be kept alive.
    }

    stages {
      stage('Prepare') {
        steps {
          script {
            def propsFile = 'jenkins.properties'
            def hasPropsFile = fileExists(propsFile)
            def props = hasPropsFile ? readProperties(file: propsFile) : new HashMap()

            println(props)

            if(props['useWrapper'] != null) {
              println(props['useWrapper'])
              useWrapper = props['useWrapper'] == 'true'
            } else if(args?.useWrapper != null) {
              println(args.useWrapper)
              useWrapper = args.useWrapper
            } else {
              println("$WORKSPACE/gradlew")
              println(fileExists('gradlew').exists())
              useWrapper = fileExists('gradlew')
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
              publishTaggedOnly = BRANCH_NAME == "master"
            }

            if(props['publishCredentialsId'] != null) {
              publishCredentialsId = props['publishCredentialsId'] == 'true'
            } else if(args?.publishCredentialsId != null) {
              publishCredentialsId = args.publishCredentialsId
            } else {
              publishCredentialsId = "54f4266c-9654-4a93-8ba1-cab34848d8f0"
            }

            if(props['publishUsernameProperty'] != null) {
              publishUsernameProperty = props['publishUsernameProperty'] == 'true'
            } else if(args?.publishUsernameProperty != null) {
              publishUsernameProperty = args.publishUsernameProperty
            } else {
              publishUsernameProperty = "publish.repository.metaborg.artifacts.username"
            }

            if(props['publishPasswordProperty'] != null) {
              publishPasswordProperty = props['publishPasswordProperty'] == 'true'
            } else if(args?.publishPasswordProperty != null) {
              publishPasswordProperty = args.publishPasswordProperty
            } else {
              publishPasswordProperty = "publish.repository.metaborg.artifacts.password"
            }
            
            
            if(props['upstreamProjects'] != null) {
              upstreamProjects = props['upstreamProjects']
            } else if(args?.upstreamProjects != null && args.upstreamProjects instanceof List<String> && args.upstreamProjects.length() > 0) {
              upstreamProjects = args.upstreamProjects.join(',')
            } else {
              upstreamProjects = ''
            }

            
            if(useWrapper) {
              gradleCommand = "./gradlew"
            } else {
              gradleCommand = "gradle"
            }
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

    triggers {
      upstream(upstreamProjects: upstreamProjects, threshold: hudson.model.Result.SUCCESS)
    }
  }
}
