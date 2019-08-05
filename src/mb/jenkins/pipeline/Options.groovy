package mb.jenkins.pipeline



class Options {
  private final Map args
  private final Map props

  Options(Map args, Map props) {
    this.args = args ? args : new HashMap()
    this.props = props ? props : new HashMap()
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
