package com.ensighten.android

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

class EnsightenPlugin implements Plugin<Project> {  
  @Override
  void apply(Project project) {
      final def variants
      final def plugin
      if (project.plugins.hasPlugin(AppPlugin)) {
          variants = project.android.applicationVariants
          plugin = project.plugins.getPlugin(AppPlugin)
      } else if (project.plugins.hasPlugin(LibraryPlugin)) {
          variants = project.android.libraryVariants
          plugin = project.plugins.getPlugin(LibraryPlugin)
      } else {
          throw new GradleException("The 'android' or 'android-library' plugin is required.")
      }

      project.repositories {
          mavenCentral()
      }
      project.dependencies {
          compile 'org.aspectj:aspectjrt:1.7.1'
      }

      project.afterEvaluate {
          variants.all { variant ->

              JavaCompile javaCompile = variant.javaCompile
              def bootClasspath
              if (plugin.properties['runtimeJarList']) {
                  bootClasspath = plugin.runtimeJarList
              } else {
                  bootClasspath = plugin.bootClasspath
              }

              def variantName = variant.name.capitalize()
              def taskName = "compile${variantName}EnsightenAspectj"

              def aspectjCompile = project.task(taskName, type: AspectjCompile) {
                  aspectpath = javaCompile.classpath
                  destinationDir = javaCompile.destinationDir
                  classpath = javaCompile.classpath
                  bootclasspath = bootClasspath.join(File.pathSeparator)
                  sourceroots = javaCompile.source
              }

              aspectjCompile.dependsOn(javaCompile)
              javaCompile.finalizedBy(aspectjCompile)
          }
      }
  }
}


class AspectjCompile extends DefaultTask {

    FileCollection aspectpath
    File destinationDir
    FileCollection classpath
    String bootclasspath
    def sourceroots

    @TaskAction
    def compile() {

        final def log = project.logger

        def sourceRoots = []
        sourceroots.sourceCollections.each {
            it.asFileTrees.each {
                sourceRoots << it.dir
            }
        }

        def String[] args = [
                "-Xlint:ignore",
                "-encoding", "UTF-8",
                "-" + project.android.compileOptions.sourceCompatibility,
                "-aspectpath", aspectpath.asPath,
                "-d", destinationDir.absolutePath,
                "-classpath", classpath.asPath,
                "-bootclasspath", bootclasspath,
                "-sourceroots", sourceRoots.join(File.pathSeparator)
        ]

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args, handler);
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ABORT:
                case IMessage.ERROR:
                case IMessage.FAIL:
                    log.error message.message, message.thrown
                    throw new GradleException(message.message, message.thrown)
                case IMessage.WARNING:
                    log.warn message.message, message.thrown
                    break;
                case IMessage.INFO:
                    log.info message.message, message.thrown
                    break;
                case IMessage.DEBUG:
                    log.debug message.message, message.thrown
                    break;
            }
        }

    }
}