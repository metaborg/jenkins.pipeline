package mb.jenkins.pipeline

def send(String channel, String color, String message) {
  slackSend(channel: channel, color: color, message: "${env.JOB_NAME} - ${env.BUILD_NUMBER} - ${message} (<${env.BUILD_URL}|Status> <${env.BUILD_URL}console|Console>)")
}

def sendFailure(String channel) {
  this.send(channel, 'danger', 'failed :facepalm:')
}

def sendFixed(String channel) {
  this.send(channel, 'danger', 'fixed :party_parrot:')
}

return this
