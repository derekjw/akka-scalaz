package akka.scalaz.actors
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.{Future}
import akka.actor.{ActorRef}

sealed trait ActorRefW extends PimpedType[ActorRef] {
  def kleisli: Kleisli[Future, Any, Any] = Scalaz.kleisli((a: Any) => value !!! a)
}

trait ActorRefs {
  implicit def ActorRefTo(a: ActorRef): ActorRefW = new ActorRefW {
    val value = a
  }
}
