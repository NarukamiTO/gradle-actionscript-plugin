plugins {
  alias(libs.plugins.kotlin.jvm)
  `java-gradle-plugin`
  `maven-publish`
  signing
  id("org.jetbrains.dokka") version "2.0.0"
}

group = "dev.assasans.actionscript"
version = "1.0.5-SNAPSHOT"

gradlePlugin {
  val actionscript by plugins.registering {
    id = "dev.assasans.actionscript"
    implementationClass = "dev.assasans.actionscript.ActionScriptPlugin"
  }
}

tasks {
  val sourcesJar by registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
  }

  val dokkaJar by registering(Jar::class) {
    dependsOn(named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/javadoc"))
  }

  artifacts {
    archives(sourcesJar)
    archives(dokkaJar)
    archives(jar)
  }

  val signingTasks: TaskCollection<Sign> = withType<Sign>()
  withType<PublishToMavenRepository>().configureEach { mustRunAfter(signingTasks) }
  withType<PublishToMavenLocal>().configureEach { mustRunAfter(signingTasks) }
}

publishing {
  publications {
    withType<MavenPublication>().configureEach {
      artifact(tasks["sourcesJar"])
      artifact(tasks["dokkaJar"])

      pom {
        name.set("Gradle ActionScript Plugin")
        description.set("Gradle support for ActionScript projects and SWF / SWC compilation.")
        url.set("https://github.com/NarukamiTO/gradle-actionscript-plugin")

        licenses {
          license {
            name.set("MIT License")
            url.set("https://opensource.org/license/MIT")
          }
        }

        developers {
          developer {
            id.set("assasans")
            name.set("Daniil Pryima")
            email.set("swimmin2@gmail.com")
          }
        }

        scm {
          connection.set("scm:git:git://github.com/NarukamiTO/gradle-actionscript-plugin.git")
          developerConnection.set("scm:git:ssh://github.com:NarukamiTO/gradle-actionscript-plugin.git")
          url.set("https://github.com/NarukamiTO/gradle-actionscript-plugin")
        }
      }
    }
  }

  repositories {
    mavenLocal()
    maven(layout.buildDirectory.dir("local-dist")) {
      name = "local-dist"
    }

    maven("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/") {
      name = "ossrh-staging-api"
      credentials {
        username = project.findProperty("mavenCentralUsername") as String?
        password = project.findProperty("mavenCentralPassword") as String?
      }
    }

    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      name = "maven-snapshots"
      credentials {
        username = project.findProperty("mavenCentralUsername") as String?
        password = project.findProperty("mavenCentralPassword") as String?
      }
    }
  }
}

afterEvaluate {
  signing {
    useGpgCmd()
    sign(publishing.publications["pluginMaven"])
    sign(publishing.publications["actionscriptPluginMarkerMaven"])
  }
}

repositories {
  mavenCentral()
}
