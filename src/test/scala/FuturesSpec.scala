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
      val list = (1 to 100).toList.map(future(TIMEOUT)(_)).map(_.map(10 *))
      list.sequence.await.result must_== Some((10 to 1000 by 10).toList)
    }
  }
}
