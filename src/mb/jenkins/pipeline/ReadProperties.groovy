package mb.jenkins.pipeline

def readProps(String propsFile = 'jenkins.properties') {
  def hasPropsFile = fileExists(propsFile)
  return hasPropsFile ? readProperties(file: propsFile) : null
}

return this
