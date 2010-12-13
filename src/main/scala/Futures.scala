package akka.scalaz

import scalaz._
import Scalaz._

import akka.actor.Actor.{spawn, TIMEOUT}
import akka.dispatch._

import java.util.concurrent.TimeUnit

object AkkaFutures {
  private def nanosToMillis(in: Long): Long = TimeUnit.NANOSECONDS.toMillis(in)

  implicit object FutureFunctor extends Functor[Future] {
    def fmap[A, B](r: Future[A], f: A => B): Future[B] = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.fold(a => spawn(try {fb.completeWithResult(f(a))} catch {case e => fb.completeWithException(e)}),
                                         fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FutureBind extends Bind[Future] {
    def bind[A, B](r: Future[A], f: A => Future[B]) = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.fold(f(_).onComplete(fb.completeWith(_)),
                                         fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FuturePure extends Pure[Future] {
    def pure[A](a: => A) = {
      val f = new DefaultCompletableFuture[A](TIMEOUT)
      try { f completeWithResult a } catch { case e => f completeWithException e }
      f
    }

    //def pure[A](a: => A) = Futures.future(TIMEOUT)(a)
  }

  implicit val FutureApply = FunctorBindApply[Future]

  implicit object FutureEach extends Each[Future] {
    def each[A](e: Future[A], f: A => Unit) = e onComplete (_.result foreach f)
  }

  implicit def FutureTo[A](f: Future[A]): FutureW[A] = new FutureW[A] {
    val value = f
  }

  implicit def CompletableFutureTo[A](f: CompletableFuture[A]): CompletableFutureW[A] = new CompletableFutureW[A] {
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

sealed trait CompletableFutureW[A] extends PimpedType[CompletableFuture[A]] {
  def completeWith(validation: Validation[Throwable, A]): Unit =
    validation.fold(value.completeWithException, value.completeWithResult)
}
