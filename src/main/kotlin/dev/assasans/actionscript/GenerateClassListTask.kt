package dev.assasans.actionscript

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateClassListTask : DefaultTask() {
  @get:InputFiles
  abstract val sourceDirs: ConfigurableFileCollection

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun generate() {
    val config = buildString {
      appendLine("<flex-config>")
      appendLine("  <includes>")

      sourceDirs.files.forEach { source ->
        source.walkTopDown()
          .filter { it.isFile && it.name.endsWith(".as") }
          .forEach { file ->
            val relative = file.relativeTo(source)
            val packageName = relative.parentFile?.path?.replace(File.separator, ".")
            val className = relative.nameWithoutExtension

            if(packageName == null) {
              appendLine("    <symbol>$className</symbol>")
            } else {
              appendLine("    <symbol>$packageName.$className</symbol>")
            }
          }
      }

      appendLine("  </includes>")
      appendLine("</flex-config>")
    }

    outputFile.get().asFile.writeText(config)
  }
}
