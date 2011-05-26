package akka.scalaz.futures
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.Promise

sealed trait PromiseW[A] extends PimpedType[Promise[A]] {
  def complete(validation: Validation[Throwable, A]): Unit =
    value.complete(validation.either)
}

trait Promises {
  implicit def PromiseTo[A](f: Promise[A]): PromiseW[A] = new PromiseW[A] {
    val value = f
  }
}
