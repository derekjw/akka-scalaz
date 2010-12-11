import sbt._

class AkkaScalazProject(info: ProjectInfo) extends DefaultProject(info) with AkkaBaseProject {

  override def compileOptions = Optimize :: Unchecked :: super.compileOptions.toList

  val akkaActor = "se.scalablesolutions.akka" % "akka-actor"  % "1.0-RC1"
  val scalazCore = "com.googlecode.scalaz" %% "scalaz-core" % "5.1-SNAPSHOT"
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.6" % "test"

  val scalaToolsSnapshots     = ScalaToolsSnapshots
  val akkaModuleConfig        = ModuleConfiguration("se.scalablesolutions.akka", AkkaRepositories.AkkaRepo)
}
