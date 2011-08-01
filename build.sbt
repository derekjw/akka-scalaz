

scalaVersion := "2.9.0-1"

name := "akka-scalaz"

organization := "se.scalablesolutions"

version := "2.0-SNAPSHOT"

libraryDependencies ++= Seq("se.scalablesolutions.akka" % "akka-actor" % "2.0-SNAPSHOT" % "compile",
                            "org.scalaz" %% "scalaz-core" % "7.0-SNAPSHOT",
                            "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0-SNAPSHOT" % "test",
                            "org.scala-tools.testing" %% "scalacheck" % "1.9" % "test",
                            "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test")

autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.9.0-1")

scalacOptions += "-P:continuations:enable"

parallelExecution in Test := true
