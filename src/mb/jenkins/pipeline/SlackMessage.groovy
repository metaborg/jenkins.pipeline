package mb.jenkins.pipeline

class SlackMessage {
  def static create(String message, Map env) {
    return "${env.JOB_NAME} - ${env.BUILD_NUMBER} - ${message} (<${env.BUILD_URL}|Status> <${env.BUILD_URL}console|Console>)"
  }
}
