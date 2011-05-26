import sbt._
 
class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val akkaRepo   = "Akka Repository" at "http://akka.io/repository"
  val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "2.0-SNAPSHOT"
}
