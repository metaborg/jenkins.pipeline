def call(Map args) {
  boolean publish
  boolean publishTaggedOnly
  String upstreamProjects
  boolean gradleRefreshDependencies

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
            def hasPropsFile = new File("$WORKSPACE/$propsFile").exists()
            def props = hasPropsFile ? readProperties(file: propsFile) : new HashMap()

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

            if(props['upstreamProjects'] != null) {
              upstreamProjects = props['upstreamProjects']
            } else if(args?.upstreamProjects != null && args.upstreamProjects instanceof List<String> && args.upstreamProjects.length() > 0) {
              upstreamProjects = args.upstreamProjects.join(',')
            } else {
              upstreamProjects = ''
            }

            if(props['gradleRefreshDependencies'] != null) {
              gradleRefreshDependencies = props['gradleRefreshDependencies'] == 'true'
            } else if(args?.gradleRefreshDependencies != null) {
              gradleRefreshDependencies = args.gradleRefreshDependencies
            } else {
              gradleRefreshDependencies = upstreamProjects != ''
            }
          }
        }
      }

      stage('Refresh dependencies') {
        when { expression { return gradleRefreshDependencies } }
        steps {
          sh 'gradle --refresh-dependencies'
        }
      }

      stage('Build') {
        steps {
          sh 'gradle build'
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
          withCredentials([usernamePassword(credentialsId: 'artifactory', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sh 'gradle publish -Ppublish.repository.Artifactory.username=$USERNAME -Ppublish.repository.Artifactory.password=$PASSWORD'
          }
        }
      }
    }

    triggers {
      upstream(upstreamProjects: upstreamProjects, threshold: hudson.model.Result.SUCCESS)
    }
  }
}
