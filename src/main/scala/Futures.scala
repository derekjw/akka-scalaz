package akka.scalaz

import scalaz._
import Scalaz._

import akka.actor.Actor.spawn
import akka.dispatch._

import java.util.concurrent.TimeUnit

object AkkaFutures {
  private def nanosToMillis(in: Long): Long = TimeUnit.NANOSECONDS.toMillis(in)

  implicit def FutureFunctor: Functor[Future] = new Functor[Future] {
    def fmap[A, B](r: Future[A], f: A => B): Future[B] = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete { fa =>
        if (fa.result.isDefined) {
          fa.result.foreach{
            a => spawn({
              try {
                fb.completeWithResult(f(a))
              } catch {
                case e => fb.completeWithException(e)
              }
            })
          }
        } else {
          fa.exception.foreach(fb.completeWithException)
        }
      }
      fb
    }
  }

  implicit def FutureBind: Bind[Future] = new Bind[Future] {
    def bind[A, B](r: Future[A], f: A => Future[B]) = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete { fa =>
        if (fa.result.isDefined) {
          fa.result.foreach(a => f(a).onComplete(fb.completeWith(_)))
        } else {
          fa.exception.foreach(fb.completeWithException)
        }
      }
      fb
    }
  }

  implicit def FuturePure: Pure[Future] = new Pure[Future] {
    def pure[A](a: => A) = Futures.future(5000)(a)
  }

  implicit val FutureApply = FunctorBindApply[Future]

  implicit def FutureTo[A](f: Future[A]): FutureW[A] = new FutureW[A] {
    val value = f
  }
}

sealed trait FutureW[A] extends PimpedType[Future[A]] {
  def toValidation: Validation[Throwable, A] =
    if (value.await.result.isDefined) {
      success(value.result.get)
    } else {
      failure(value.exception.get)
    }
}
