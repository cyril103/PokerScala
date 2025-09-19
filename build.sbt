import java.io.File

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "io.scalaholdem"
ThisBuild / version      := "0.1.0"

lazy val javafxModules = Seq("javafx.controls", "javafx.graphics", "javafx.media")

lazy val root = (project in file(".")).
  settings(
    name := "ScalaHoldemFX",
    Compile / run / fork := true,
    Test / fork := true,
    Compile / run / javaOptions ++= javafxRuntimeOptions.value,
    Test / javaOptions ++= javafxRuntimeOptions.value,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.openjfx" % "javafx-controls" % "21.0.3" classifier osClassifier.value,
      "org.openjfx" % "javafx-graphics" % "21.0.3" classifier osClassifier.value,
      "org.openjfx" % "javafx-media" % "21.0.3" classifier osClassifier.value
    )
  )

def osClassifier = Def.setting {
  val os = System.getProperty("os.name").toLowerCase
  val arch = System.getProperty("os.arch").toLowerCase
  if (os.contains("win")) "win"
  else if (os.contains("mac") && arch.contains("aarch64")) "mac-aarch64"
  else if (os.contains("mac")) "mac"
  else "linux"
}

lazy val javafxRuntimeOptions = Def.task {
  val javaFxJars = (Compile / dependencyClasspath).value
    .map(_.data)
    .filter(_.getName.startsWith("javafx"))

  if (javaFxJars.isEmpty) Seq.empty[String]
  else {
    val modulePath = javaFxJars.map(_.getAbsolutePath).distinct.mkString(File.pathSeparator)
    Seq(s"--module-path=$modulePath", s"--add-modules=${javafxModules.mkString(",")}")
  }
}
