package akka.scalaz.futures
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.Promise

sealed trait PromiseW[A] {
  def underlying: Promise[A]

  def complete(validation: Validation[Throwable, A]): Unit =
    underlying.complete(validation.either)
}

trait Promises {
  implicit def PromiseTo[A](f: Promise[A]): PromiseW[A] = new PromiseW[A] {
    val underlying = f
  }
}
