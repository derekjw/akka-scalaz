import sbt._

class AkkaScalazProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {

  override def compileOptions = Optimize :: Unchecked :: super.compileOptions.toList

  val scalazCore = "com.googlecode.scalaz" %% "scalaz-core" % "5.1-SNAPSHOT"
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.6" % "test"

  val scalaToolsSnapshots     = ScalaToolsSnapshots
}
