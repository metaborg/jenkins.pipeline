package mb.jenkins.pipeline

class Options {
  private final Map args
  private final Map props

  Options(Map args, Map props) {
    this.args = args ? args : new HashMap()
    this.props = props ? props : new HashMap()
  }

  boolean getBoolean(String name, boolean d) {
    def prop = getProp(name)
    if(prop != null) {
      return prop == 'true'
    }
    def arg = getArg(name)
    if(arg != null) {
      return arg
    }
    return d
  }

  String getString(String name, String d) {
    def prop = getProp(name)
    if(prop != null) {
      return prop
    }
    def arg = getArg(name)
    if(arg != null) {
      return arg
    }
    return d
  }

  Object getObject(String name, Object d) {
    def prop = getProp(name)
    if(prop != null) {
      return prop
    }
    def arg = getArg(name)
    if(arg != null) {
      return arg
    }
    return d
  }


  private Object get(Object obj) {
    if(obj == null) return null
    if(obj instanceof Closure) {
      return obj()
    }
    return obj
  }

  private Object getProp(String name) {
    return get(props[name])
  }

  private Object getArg(String name) {
    return get(args[name])
  }
}
