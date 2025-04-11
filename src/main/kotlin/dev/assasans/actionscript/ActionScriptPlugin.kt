package dev.assasans.actionscript

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec

// TODO: No obvious replacement for deprecated [dependencyProject] API
@Suppress("DEPRECATION")
private fun makeDependentAndCollectOutput(task: Task, dependency: Dependency): FileCollection {
  return when(dependency) {
    is ProjectDependency        -> {
      val inner = dependency.dependencyProject.tasks.getByName("compileSwc")
      task.dependsOn(inner)
      inner.outputs.files
    }

    is FileCollectionDependency -> dependency.files
    else                        -> throw IllegalArgumentException("Unsupported dependency type: ${dependency::class.java.name}")
  }
}

class ActionScriptPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("actionscript", ActionScriptExtension::class.java, project)

    val sdk = project.rootProject.file(requireNotNull(project.rootProject.property("dev.assasans.actionscript.sdk")) {
      "Missing ActionScript SDK path. Set the 'actionscript.sdk' property in your gradle.properties file."
    })
    if(!sdk.exists()) throw IllegalArgumentException("ActionScript SDK path does not exist: $sdk")

    val sdkDir = sdk.absolutePath
    val buildDir = project.layout.buildDirectory.get().asFile.absolutePath

    project.configurations.create("compileOnly")
    project.configurations.create("implementation")

    project.tasks.register("clean", Delete::class.java).configure { task ->
      task.description = "Cleans the ActionScript build directory"
      task.delete(project.layout.buildDirectory)
    }

    val prepareSources = project.tasks.register("prepareSources") { task ->
      task.group = "actionscript"
      task.description = "Prepares ActionScript sources before compilation"
    }

    val compileSwc = project.tasks.register("compileSwc", JavaExec::class.java) { task ->
      task.group = "actionscript"
      task.description = "Compiles ActionScript project into an SWC file"
      task.dependsOn(prepareSources)

      extension.sources.forEach { source -> task.inputs.dir(source) }
      extension.configs.forEach { config -> task.inputs.file(config) }

      task.outputs.file(project.layout.buildDirectory.file("libs/library.swc"))

      val staticLibraries = project.configurations.getByName("implementation").dependencies.flatMap { dependency ->
        makeDependentAndCollectOutput(task, dependency)
      }

      val externalLibraries = project.configurations.getByName("compileOnly").dependencies.flatMap { dependency ->
        makeDependentAndCollectOutput(task, dependency)
      }

      (staticLibraries + externalLibraries).forEach { library ->
        task.inputs.file(library)
      }

      val args = buildList {
        add("-load-config=$sdkDir/frameworks/air-config.xml")
        addAll(extension.configs.map { "-load-config+=${it.absolutePath}" })
        addAll(extension.defines.map { "-define+=${it.first},${it.second}" })
        addAll(extension.sources.map { "-source-path+=${it.absolutePath}" })
        if(staticLibraries.isNotEmpty()) add("-include-libraries+=${staticLibraries.joinToString(",") { it.absolutePath }}")
        if(externalLibraries.isNotEmpty()) add("-external-library-path+=${externalLibraries.joinToString(",") { it.absolutePath }}")
        if(extension.sources.isNotEmpty()) add("-include-sources+=${extension.sources.joinToString(",") { it.absolutePath }}")
        addAll(extension.options)
        add("-output=$buildDir/libs/library.swc")
      }

      task.classpath = project.files("$sdkDir/lib/compc-cli.jar")
      task.systemProperties["flexlib"] = "$sdkDir/frameworks"
      task.args(args)
    }

    val generateClassList = project.tasks.register("generateClassList", GenerateClassListTask::class.java) { task ->
      task.group = "actionscript"
      task.description = "Generates a list of classes for the ActionScript project"

      task.sourceDirs.setFrom(extension.sources)
      task.outputFile.set(project.layout.buildDirectory.file("tmp/classes.xml"))
    }

    val compileSwf = project.tasks.register("compileSwf", JavaExec::class.java) { task ->
      task.group = "actionscript"
      task.description = "Compiles ActionScript project into an SWF file"

      if(extension.swfIncludeAllClasses) task.dependsOn(generateClassList)
      task.dependsOn(prepareSources)

      extension.sources.forEach { source -> task.inputs.dir(source) }
      extension.configs.forEach { config -> task.inputs.file(config) }

      task.outputs.file(project.layout.buildDirectory.file("libs/executable.swf"))

      val staticLibraries = project.configurations.getByName("implementation").dependencies.flatMap { dependency ->
        makeDependentAndCollectOutput(task, dependency)
      }

      val externalLibraries = project.configurations.getByName("compileOnly").dependencies.flatMap { dependency ->
        makeDependentAndCollectOutput(task, dependency)
      }

      (staticLibraries + externalLibraries).forEach { library ->
        task.inputs.file(library)
      }

      val mainClassPath = requireNotNull(extension.mainClass) {
        "Missing main class. Set the 'actionscript.mainClass'."
      }

      val args = buildList {
        add("-load-config=$sdkDir/frameworks/air-config.xml")
        addAll(extension.configs.map { "-load-config+=${it.absolutePath}" })
        addAll(extension.defines.map { "-define+=${it.first},${it.second}" })
        addAll(extension.sources.map { "-source-path+=${it.absolutePath}" })
        if(extension.swfIncludeAllClasses) add("-load-config+=$buildDir/tmp/classes.xml")
        if(staticLibraries.isNotEmpty()) add("-include-libraries+=${staticLibraries.joinToString(",") { it.absolutePath }}")
        if(externalLibraries.isNotEmpty()) add("-external-library-path+=${externalLibraries.joinToString(",") { it.absolutePath }}")
        addAll(extension.options)
        add("-output=$buildDir/libs/executable.swf")
        // TODO(Assasans): Check all sources
        add(extension.sources[0].resolve(mainClassPath.replace(".", File.separator) + ".as"))
      }

      task.classpath = project.files("$sdkDir/lib/mxmlc-cli.jar")
      task.systemProperties["flexlib"] = "$sdkDir/frameworks"
      task.args(args)
    }

    val extractSwc = project.tasks.register("extractSwc", Copy::class.java) { task ->
      task.group = "actionscript"
      task.description = "Extracts SWF file from SWC"

      task.mustRunAfter(compileSwc)

      task.from(project.zipTree(project.layout.buildDirectory.file("libs/library.swc")))
      task.into(project.layout.buildDirectory.dir("tmp"))

      task.doLast {
        project.copy {
          it.from(project.layout.buildDirectory.file("tmp/library.swf"))
          it.into(project.layout.buildDirectory.dir("libs/"))
        }
      }
    }

    fun collectDependencies(project: Project, collected: MutableSet<Dependency>): Set<Dependency> {
      val dependencies = project.configurations.getByName("implementation").dependencies +
                         project.configurations.getByName("compileOnly").dependencies
      dependencies.forEach { dependency ->
        collected.add(dependency)
        collectDependencies((dependency as ProjectDependency).dependencyProject, collected)
      }

      return collected
    }

    project.tasks.register("generateIdeaModule") { task ->
      task.group = "idea"
      task.description = "Generates IDEA module file for ActionScript project"

      task.doLast {
        val staticLibraries = project.configurations.getByName("implementation").dependencies
        val externalLibraries = project.configurations.getByName("compileOnly").dependencies
        val transitiveDependencies = collectDependencies(project, mutableSetOf()) - (staticLibraries + externalLibraries)

        val name = project.name
        val path = project.path.replace(":", "/")
        val dots = "/..".repeat(1 + project.path.split(':').size)

        val xml = buildString {
          appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
          appendLine("""<module type="Flex" version="4">""")
          appendLine("""  <component name="FlexBuildConfigurationManager" active="$name">""")
          appendLine("""    <configurations>""")
          appendLine("""      <configuration name="$name" target-platform="Desktop" pure-as="true" output-type="Library" skip-build="true">""")
          appendLine("""        <dependencies target-player="32.0">""")
          appendLine("""          <entries>""")

          staticLibraries.forEach { library ->
            appendLine("            <entry module-name=\"${library.name}\" build-configuration-name=\"${library.name}\">")
            appendLine("              <dependency linkage=\"Merged\" />")
            appendLine("            </entry>")
          }

          (externalLibraries + transitiveDependencies).forEach { library ->
            appendLine("            <entry module-name=\"${library.name}\" build-configuration-name=\"${library.name}\">")
            appendLine("              <dependency linkage=\"External\" />")
            appendLine("            </entry>")
          }

          appendLine("          </entries>")
          appendLine("          <sdk name=\"SDK\" />")
          appendLine("        </dependencies>")
          appendLine("        <compiler-options>")
          appendLine("          <option name=\"additionalConfigFilePath\" value=\"\$MODULE_DIR\$/config.xml\" />")
          appendLine("        </compiler-options>")
          appendLine("        <packaging-air-desktop />")
          appendLine("        <packaging-android />")
          appendLine("        <packaging-ios />")
          appendLine("      </configuration>")
          appendLine("    </configurations>")
          appendLine("    <compiler-options />")
          appendLine("  </component>")
          appendLine("  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">")
          appendLine("    <exclude-output />")
          appendLine("    <content url=\"file://\$MODULE_DIR\$$dots$path\">")

          extension.sources.forEach { source ->
            val relativePath = source.relativeTo(project.projectDir)
            appendLine("      <sourceFolder url=\"file://\$MODULE_DIR\$$dots$path/$relativePath\" isTestSource=\"false\" />")
          }

          appendLine("    </content>")
          appendLine("    <orderEntry type=\"jdk\" jdkName=\"SDK\" jdkType=\"Flex SDK Type (new)\" />")
          appendLine("    <orderEntry type=\"sourceFolder\" forTests=\"false\" />")

          (staticLibraries + externalLibraries + transitiveDependencies).forEach { library ->
            appendLine("    <orderEntry type=\"module\" module-name=\"${library.name}\" exported=\"\" />")
          }

          appendLine("""  </component>""")
          appendLine("""</module>""")
        }
        println(xml)

        val dir = project.rootProject.rootDir.resolve(".idea/modules${path}/")
        dir.mkdirs()
        dir.resolve("${project.name}.iml").writeText(xml)
      }
    }

    project.dependencies.extensions.create("actionscript", ActionScriptConfigurationDependencyHandler::class.java, project.dependencies)

    project.tasks.register("build") { task ->
      task.group = "build"
      task.description = "Builds the ActionScript project"

      if(!extension.swc && extension.swf == SwfType.None) {
        throw GradleException("Nothing to build. Set either 'actionscript.swc' or 'actionscript.swf'.")
      }

      if(extension.swc) {
        task.dependsOn(compileSwc)
      }

      if(extension.swf == SwfType.Swc) {
        check(extension.swc) { "SWF type is set to SWC, but SWC compilation is disabled" }
        task.dependsOn(extractSwc)
      } else if(extension.swf == SwfType.Entry) {
        task.dependsOn(compileSwf)
      }
    }
  }
}

enum class SwfType {
  /**
   * Do not generate SWF file.
   */
  None,

  /**
   * Invoke `mxmlc` to generate SWF file.
   * SWF will contain an entry point.
   */
  Entry,

  /**
   * Extract SWF file from SWC generated by `compc`.
   * This keeps all classes in the SWC, but SWF will contain no entry point.
   */
  Swc
}

open class ActionScriptExtension(private val project: Project) {
  val sources = mutableListOf<File>()
  val configs = mutableListOf<File>()
  val defines = mutableListOf<Pair<String, String>>()
  val options = mutableListOf<String>()

  var mainClass: String? = null
  var swfIncludeAllClasses: Boolean = true

  /**
   * Whether to generate SWC file.
   */
  var swc: Boolean = false
  var swf: SwfType = SwfType.None

  fun source(name: String) {
    sources.add(project.file(name))
  }

  fun config(name: String) {
    configs.add(project.file(name))
  }

  fun define(name: String, value: String) {
    defines.add(name to value)
  }

  fun option(option: String) {
    options.add(option)
  }
}

open class ActionScriptConfigurationDependencyHandler(private val dependencyHandler: DependencyHandler) {
  fun compileOnly(dependency: Any) {
    dependencyHandler.add("compileOnly", dependency)
  }

  fun implementation(dependency: Any) {
    dependencyHandler.add("implementation", dependency)
  }
}
