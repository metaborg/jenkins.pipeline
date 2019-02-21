plugins {
  id("org.metaborg.gradle.config.root-project") version "0.2.3"
  id("org.metaborg.gitonium") version "0.1.0"
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
  implementation("org.codehaus.groovy:groovy-all:2.5.5")
}
