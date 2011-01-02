package akka.scalaz

import scalaz._
import Scalaz._

import akka.actor.Actor.{spawn, TIMEOUT}
import akka.dispatch._
import Futures.future

import java.util.concurrent.TimeUnit

object AkkaFutures {
  private def nanosToMillis(in: Long): Long = TimeUnit.NANOSECONDS.toMillis(in)

  implicit object FutureFunctor extends Functor[Future] {
    def fmap[A, B](r: Future[A], f: A => B): Future[B] = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.cata(a => spawn(try {fb.completeWithResult(f(a))} catch {case e => fb.completeWithException(e)}),
                                         fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FutureBind extends Bind[Future] {
    def bind[A, B](r: Future[A], f: A => Future[B]) = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.cata(a => spawn(try {f(a).onComplete(fb.completeWith(_))} catch {case e => fb.completeWithException(e)}),
                                         fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FuturePure extends Pure[Future] {
    def pure[A](a: => A) = Futures.future(TIMEOUT)(a)
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

  implicit def maFutureImplicit[M[_], A](a: M[A]): MAFuture[M, A] = new MAFuture[M, A] {
    val value = a
  }

  implicit def Function0ToFuture[A](f: () => A): Function0Future[A] = new Function0Future[A] {
    val k = f
  }

  implicit def Function1ToFuture[T, R](f: T => R): Function1Future[T, R] = new Function1Future[T, R] {
    val k = f
  }

  implicit def Function1FromFuture[T, R](f: Function1Future[T, R]): T => R = f.k
}

sealed trait FutureW[A] extends PimpedType[Future[A]] {
  def toValidation: Validation[Throwable, A] =
    value.await.result.cata(success(_), failure(value.exception.get))

  def timeout(t: Long): Future[A] = {
    val f = new DefaultCompletableFuture[A](t)
    value onComplete (f.completeWith(_))
    f
  }
}

sealed trait CompletableFutureW[A] extends PimpedType[CompletableFuture[A]] {
  def completeWith(validation: Validation[Throwable, A]): Unit =
    validation.fold(value.completeWithException, value.completeWithResult)
}

sealed trait MAFuture[M[_], A] extends PimpedType[M[A]] {
  import AkkaFutures._

  def futureMap[B](f: A => B)(implicit t: Traverse[M]): Future[M[B]] =
    value ∘ (f.future) sequence

  def futureBind[B](f: A => M[B])(implicit m: Monad[M], t: Traverse[M]): Future[M[B]] =
    futureMap(f).map(_.join)
}

sealed trait Function0Future[A] {
  import AkkaFutures._

  val k: () => A

  def future: Future[A] = implicitly[Pure[Future]] pure k.apply
}

sealed trait Function1Future[T, R] {
  import AkkaFutures._

  val k: T => R

  def future: Kleisli[Future, T, R] = k.kleisli[Future]
}
