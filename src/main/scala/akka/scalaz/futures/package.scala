package akka.scalaz

import scalaz._
import Scalaz._

import akka.actor.Actor.TIMEOUT
import akka.dispatch.{ Future, Promise, KeptPromise }

import java.util.concurrent.TimeUnit
import TimeUnit.{ NANOSECONDS => NANOS, MILLISECONDS => MILLIS }

import futures.conversions._

package object futures extends Futures
    with Promises
    with ActorRefs
    with conversions.Function0s
    with conversions.Function1s {

  implicit val FutureFunctor = new Functor[Future] {
    def fmap[A, B](f: A => B) = _ map f
  }

  implicit val FutureBind = new Bind[Future] {
    def bind[A, B](f: A => Future[B]) = _ flatMap f
  }

  implicit val FuturePointed = new Pointed[Future] {
    def point[A](a: => A): Future[A] =
      new KeptPromise[A](try { Right(a) } catch { case e => Left(e) })
  }

  implicit val FuturePointedFunctor = PointedFunctor.pointedFunctor[Future]

  implicit val FutureApplic = new Applic[Future] {
    def applic[A, B](f: Future[A => B]) =
      a => f flatMap (a map _)
  }

  implicit val FutureApplicFunctor = ApplicFunctor.applicFunctor[Future]

  implicit val FutureApplicative = Applicative.applicative[Future]

  implicit val FutureMonad = Monad.monadBP[Future]

  implicit val FutureEach = new Each[Future] {
    def each[A](f: A => Unit) = _ foreach f
  }

  implicit val FuturePlus = new Plus[Future] {
    def plus[A](a1: Future[A], a2: => Future[A]): Future[A] =
      (a1 map (r => new KeptPromise[A](Right(r))) recover {
        case e: Exception => (a2 recover { case _ => throw e })
      }).join

  }

  implicit def FutureSemigroup[A: Semigroup]: Semigroup[Future[A]] =
    semigroup(fa => fb => for (a <- fa; b <- fb) yield (a |+| b))

  implicit def FutureZero[A: Zero]: Zero[Future[A]] = zero(∅[A].point[Future])

  implicit def FutureMonoid[A](implicit m: Monoid[A]) = {
    implicit val s = m.semigroup
    implicit val z = m.zero
    Monoid.monoid[Future[A]]
  }

  implicit def FutureCoJoin: CoJoin[Future] = new CoJoin[Future] {
    def coJoin[A] = _.point[Future]
  }

  implicit def FutureCoPointed: CoPointed[Future] = new CoPointed[Future] {
    def coPoint[A] = _.get
  }
}
