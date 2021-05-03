plugins {
  id("org.metaborg.gradle.config.root-project") version "0.4.4"
  id("org.metaborg.gitonium") version "0.1.4"
  groovy
}

sourceSets {
  main {
    withConvention(GroovySourceSet::class) {
      groovy {
        setSrcDirs(listOf("src", "vars"))
      }
    }
  }

  test {
    withConvention(GroovySourceSet::class) {
      groovy {
        setSrcDirs(listOf("test"))
      }
    }
  }
}

dependencies {
  implementation("org.codehaus.groovy:groovy-all:3.0.4")
}
