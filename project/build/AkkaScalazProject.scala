import sbt._

class AkkaScalazProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {

  override def compileOptions = Optimize :: Unchecked :: super.compileOptions.toList

  val scalazCore = "org.scalaz" %% "scalaz-core" % "6.0.RC2"
  val scalazScalacheck = "org.scalaz" %% "scalaz-scalacheck-binding" % "6.0.RC2" % "test"
  val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.9" % "test"
  val scalatest = "org.scalatest" %% "scalatest" % "1.4.1" % "test"

  val scalaToolsSnapshots     = ScalaToolsSnapshots
}
