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

  implicit val FutureApply = FunctorBindApply[Future]
}
