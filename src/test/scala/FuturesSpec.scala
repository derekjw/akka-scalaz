package akka.scalaz

import org.specs._

import scalaz._
import Scalaz._

import akka.dispatch._
import akka.actor.Actor.TIMEOUT
import Futures.future

class AkkaFuturesSpec extends Specification {
  import AkkaFutures._

  "akka futures" should {
    "have scalaz functor instance" in {
      val f1 = future(TIMEOUT)(5 * 5)
      val f2 = f1 ∘ (_ * 2)
      val f3 = f2 ∘ (_ * 10)
      val f4 = f1 ∘ (_ / 0)
      val f5 = f4 ∘ (_ * 10)

      f2.await.resultOrException must_== Some(50)
      f3.await.resultOrException must_== Some(500)
      f4.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
      f5.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
    }

    "have scalaz bind instance" in {
      val f1 = future(TIMEOUT)(5 * 5)
      val f2 = f1 flatMap (x => future(TIMEOUT)(x * 2))
      val f3 = f2 flatMap (x => future(TIMEOUT)(x * 10))
      val f4 = f1 flatMap (x => future(TIMEOUT)(x / 0))
      val f5 = f4 flatMap (x => future(TIMEOUT)(x * 10))

      f2.await.resultOrException must_== Some(50)
      f3.await.resultOrException must_== Some(500)
      f4.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
      f5.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
    }

    "have scalaz apply instance" in {
      val f1 = future(TIMEOUT)(5 * 5)
      val f2 = f1 ∘ (_ * 2)
      val f3 = f2 ∘ (_ / 0)

      (f1 |@| f2)(_ * _).await.resultOrException must_== Some(1250)
      (f1 |@| f2).tupled.await.resultOrException must_== Some((25,50))
      (f1 |@| f2 |@| f3)(_ * _ * _).await.resultOrException must throwA(new ArithmeticException("/ by zero"))
      (f3 |@| f2 |@| f1)(_ * _ * _).await.resultOrException must throwA(new ArithmeticException("/ by zero"))
    }

    "calculate fib seq" in {
      def seqFib(n: Int): Int = if (n < 2) n else seqFib(n - 1) + seqFib(n - 2)

      def fib(n: Int): Future[Int] =
        if (n < 30)
          future(TIMEOUT)(seqFib(n))
        else
          fib(n - 1).<**>(fib(n - 2))(_ + _)

      fib(40).await.result must_== Some(102334155)
    }

    "sequence a list" in {
      val list = (1 to 100).toList.map(_.pure[Future] ∘ (10 *))
      list.sequence.await.result must_== Some((10 to 1000 by 10).toList)
    }

    "map a list in parallel" in {
      (1 to 100).toList.futureMap(10*).await.result must_== Some((10 to 1000 by 10).toList)
    }

    "reduce a list of futures" in {
      val list = (1 to 100).toList.map(_.pure[Future])
      list.reduceLeft((a,b) => (a |@| b)(_ + _)).await.result must_== Some(5050)
    }

    "have a resetable timeout" in {
      future(5000)("test").timeout(100).await mustNot throwA(new FutureTimeoutException("Futures timed out after [100] milliseconds"))
      future(5000)({Thread.sleep(500);"test"}).timeout(100).await must throwA(new FutureTimeoutException("Futures timed out after [100] milliseconds"))
    }

    // Taken from Haskell example, performance is very poor, this is only here as a test
    "quicksort a list" in {
      val rnd = new scala.util.Random(1)
      val list = List.fill(1000)(rnd.nextInt)

      def qsort[T](in: List[T])(implicit ord: math.Ordering[T]): Future[List[T]] = in match {
        case Nil => nil.pure[Future]
        case x :: xs => (qsort(xs.filter(ord.lt(_,x))) |@| x.pure[Future] |@| qsort(xs.filter(ord.gteq(_,x))))(_ ::: _ :: _)
      }

      qsort(list).await.result must beSome[List[Int]].which(_ == list.sorted)
    }
  }
}
