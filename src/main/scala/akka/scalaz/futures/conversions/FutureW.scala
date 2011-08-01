package akka.scalaz.futures
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.{ Future }

sealed trait FutureW[A] {
  def underlying: Future[A]

  def lift: Future[Either[Exception, A]] =
    underlying map (_.right) recover { case e: Exception => e.left }

  def liftValidation: Future[Validation[Exception, A]] =
    underlying map (_.success[Exception]) recover { case e: Exception => e.fail }

  def liftValidationNel: Future[Validation[NonEmptyList[Exception], A]] =
    underlying map (_.success[NonEmptyList[Exception]]) recover { case e: Exception => e.wrapNel.fail }

}

trait Futures {
  implicit def FutureTo[A](f: Future[A]): FutureW[A] = new FutureW[A] {
    val underlying = f
  }
}
