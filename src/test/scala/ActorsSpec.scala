package akka.scalaz.actors

import org.specs._

import scalaz._
import Scalaz._

import akka.actor.{Actor}
import Actor._
import akka.util.Logging

import akka.scalaz.futures._

class AkkaActorsSpec extends Specification with Logging {
  val test = "hello" // for weird emacs tab problem

  "akka actors" should {
    "map values" in {
      val a = actorOf[DoubleActor].start
      val k = a.kleisli
      val l = (1 to 10).toList

      (l map k sequence).getOrThrow must_== List(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)
      (l map (k >=> k) sequence).getOrThrow must_== List(4, 8, 12, 16, 20, 24, 28, 32, 36, 40)
      (l map (k &&& (k >=> k)) sequence).getOrThrow must_== List((2, 4), (4, 8), (6, 12), (8, 16), (10, 20), (12, 24), (14, 28), (16, 32), (18, 36), (20, 40))
      a.stop
    }
  }
}

class DoubleActor extends Actor {
  def receive = {
    case i: Int => self reply (i*2)
  }
}

