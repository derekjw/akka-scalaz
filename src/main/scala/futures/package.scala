package akka.scalaz

import scalaz._
import Scalaz._

import akka.actor.Actor.TIMEOUT
import akka.dispatch.{Future, DefaultCompletableFuture}
import akka.config.Config._

import java.util.concurrent.TimeUnit

import futures.conversions._

package object futures extends Futures
    with CompletableFutures
    with ActorRefs
    with conversions.Promises
    with conversions.Function0s
    with conversions.Function1s {

  sealed trait Exec {
    def apply(f: => Unit): Unit
  }

  object AkkaSpawn extends Exec {
    import akka.actor.Actor.spawn
    def apply(f: => Unit): Unit = spawn(f)
  }

  object HawtQueue extends Exec {
    import org.fusesource.hawtdispatch.ScalaDispatch._
    val queue = globalQueue
    def apply(f: => Unit): Unit = queue(f)
  }

  private val exec: Exec = config.getString("akka.scalaz.executer", "akka") match {
    case "akka" => AkkaSpawn
    case "hawt" => HawtQueue
    case _ => error("Invalid config for akka.scalaz.executer")
  }

  def nanosToMillis(in: Long): Long = TimeUnit.NANOSECONDS.toMillis(in)

  implicit object FutureFunctor extends Functor[Future] {
    def fmap[A, B](r: Future[A], f: A => B): Future[B] = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.cata(a => exec(try {fb.completeWithResult(f(a))} catch {case e => fb.completeWithException(e)}),
        fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FutureBind extends Bind[Future] {
    def bind[A, B](r: Future[A], f: A => Future[B]) = {
      val fb = new DefaultCompletableFuture[B](nanosToMillis(r.timeoutInNanos))
      r onComplete (fa => fa.result.cata(a => exec(try {f(a).onComplete(fb.completeWith(_))} catch {case e => fb.completeWithException(e)}),
        fa.exception.foreach(fb.completeWithException)))
      fb
    }
  }

  implicit object FuturePure extends Pure[Future] {
    def pure[A](a: => A) = {
      val f = new DefaultCompletableFuture[A](TIMEOUT)
      exec(try {f.completeWithResult(a)} catch {case e => f.completeWithException(e)})
      f
    }
  }

  implicit val FutureApply = FunctorBindApply[Future]

  implicit object FutureEach extends Each[Future] {
    def each[A](e: Future[A], f: A => Unit) = e onComplete (_.result foreach f)
  }

  def futureMap[M[_], A, B](ma: M[A])(f: A => B)(implicit t: Traverse[M]): Future[M[B]] =
    ma ∘ (f.future) sequence

  def futureBind[M[_], A, B](ma: M[A])(f: A => M[B])(implicit m: Monad[M], t: Traverse[M]): Future[M[B]] =
    futureMap(ma)(f).map(_.join)
}
