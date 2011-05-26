package akka.scalaz.futures
package conversions

import scalaz._
import Scalaz._

import akka.dispatch.{ Future, DefaultPromise, KeptPromise, FutureTimeoutException }

sealed trait FutureW[A] extends PimpedType[Future[A]] {

  def lift: Future[Either[Throwable, A]] =
    value map (_.right) failure { case e: Exception => e.left }

  def liftValidation: Future[Validation[Throwable, A]] =
    value map (_.success) failure { case e: Exception => e.fail }

  def liftValidationNel: Future[Validation[NonEmptyList[Throwable], A]] =
    value map (_.successNel) failure { case e: Exception => e.failNel }

  def getOrElse[B >: A](default: => B): B = try {
    value.await.value.flatMap(_.right.toOption) getOrElse default
  } catch {
    case f: FutureTimeoutException => default
  }

  def orElse[B >: A](b: => Future[B]): Future[B] = {
    (value map (r => new KeptPromise[B](Right(r)): Future[B]) failure {
      case e: Exception => (b failure { case _ => throw e }): Future[B]
    }).join
  }

}

trait Futures {
  implicit def FutureTo[A](f: Future[A]): FutureW[A] = new FutureW[A] {
    val value = f
  }
}
