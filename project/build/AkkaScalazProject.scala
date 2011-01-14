import sbt._

class AkkaScalazProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {

  override def compileOptions = Optimize :: Unchecked :: super.compileOptions.toList

  val scalazCore = "com.googlecode.scalaz" %% "scalaz-core" % "5.1-SNAPSHOT"
  val scalazScalacheck = "com.googlecode.scalaz" %% "scalaz-scalacheck-binding" % "5.1-SNAPSHOT" % "test"
  val scalatest = "org.scalatest" % "scalatest" % "1.2.1-SNAPSHOT" % "test"

  //override def testOptions = TestArgument(TestFrameworks.ScalaTest, "-oD") :: super.testOptions.toList

  override def managedStyle = ManagedStyle.Maven
  val publishUser = "derek"
  val publishKeyFile = new java.io.File("/home/derek/.ssh/id_rsa")
  val publishTo = projectVersion.value match {
    case BasicVersion(_,_,_,Some("SNAPSHOT")) =>
      Resolver.sftp("Fyrie Snapshots SFTP", "repo.fyrie.net", "/home/repo/snapshots") as(publishUser, publishKeyFile)
    case _ =>
      Resolver.sftp("Fyrie Releases SFTP", "repo.fyrie.net", "/home/repo/releases") as(publishUser, publishKeyFile)
  }

  val fyrieReleases           = "Fyrie releases" at "http://repo.fyrie.net/releases"
  val fyrieSnapshots          = "Fyrie snapshots" at "http://repo.fyrie.net/snapshots"

  val scalaToolsSnapshots     = ScalaToolsSnapshots
  val akkaModuleConfig        = ModuleConfiguration("se.scalablesolutions.akka", AkkaRepositories.AkkaRepo)
}
