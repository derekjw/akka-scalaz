package akka.scalaz.futures
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.Future
import akka.actor.ActorRef

sealed trait ActorRefW {
  def underlying: ActorRef

  def future: Any => Future[Any] = underlying ? _
}

trait ActorRefs {
  implicit def ActorRefTo(a: ActorRef): ActorRefW = new ActorRefW {
    val underlying = a
  }
}
