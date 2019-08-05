package mb.jenkins.pipeline

class Options {
  private final Map args
  private final Map props

  Options(Map args) {
    this.args = args ? args : new HashMap()

    def propsFile = 'jenkins.properties'
    def hasPropsFile = fileExists(propsFile)
    props = hasPropsFile ? readProperties(file: propsFile) : new HashMap()
  }

  boolean getBoolean(String name, boolean d) {
    if(props[name] != null) {
      return props[name] == 'true'
    } else if(args[name] != null) {
      return args[name]
    } else {
      return d
    }
  }

  String getString(String name, String d) {
    if(props[name] != null) {
      return props[name]
    } else if(args[name] != null) {
      return args[name]
    } else {
      return d
    }
  }
}
