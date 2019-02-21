plugins {
//  id("org.metaborg.gradle.config.root-project") version "0.5.0"
//  id("org.metaborg.gitonium") version "0.3.0"
  groovy
}

group = "org.metaborg"
version = "master-SNAPSHOT"

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

tasks {
  wrapper {
    gradleVersion = "5.2.1"
    distributionType = Wrapper.DistributionType.ALL
    setJarFile(".gradlew/wrapper/gradle-wrapper.jar")
  }
}

repositories {
  jcenter()
}
