package akka.scalaz.futures

import org.specs._

import scalaz._
import Scalaz._

import akka.dispatch._
import akka.actor.Actor
import Actor._

import akka.util.Logging

class AkkaFuturesSpec extends Specification with Logging {
  def f[A](a: => A) = (() => a).future

  "akka futures" should {
    "have scalaz functor instance" in {
      val f1 = f(5 * 5)
      val f2 = f1 ∘ (_ * 2)
      val f3 = f2 ∘ (_ * 10)
      val f4 = f1 ∘ (_ / 0)
      val f5 = f4 ∘ (_ * 10)

      f2.getOrThrow must_== 50
      f3.getOrThrow must_== 500
      f4.getOrThrow must throwA(new ArithmeticException("/ by zero"))
      f5.getOrThrow must throwA(new ArithmeticException("/ by zero"))
    }

    "have scalaz bind instance" in {
      val f1 = f(5 * 5)
      val f2 = f1 >>= ((_: Int) * 2).future
      val f3 = f2 >>= ((_: Int) * 10).future
      val f4 = f1 >>= ((_: Int) / 0).future
      val f5 = f4 >>= ((_: Int) * 10).future

      f2.getOrThrow must_== 50
      f3.getOrThrow must_== 500
      f4.getOrThrow must throwA(new ArithmeticException("/ by zero"))
      f5.getOrThrow must throwA(new ArithmeticException("/ by zero"))
    }

    "have scalaz apply instance" in {
      val f1 = f(5 * 5)
      val f2 = f1 ∘ (_ * 2)
      val f3 = f2 ∘ (_ / 0)

      (f1 ⊛ f2)(_ * _).getOrThrow must_== 1250
      (f1 ⊛ f2).tupled.getOrThrow must_== (25,50)
      (f1 ⊛ f2 ⊛ f3)(_ * _ * _).getOrThrow must throwA(new ArithmeticException("/ by zero"))
      (f3 ⊛ f2 ⊛ f1)(_ * _ * _).getOrThrow must throwA(new ArithmeticException("/ by zero"))

      (f1 <|**|> (f2, f1)).getOrThrow must_== (25,50,25)
    }

    "calculate fib seq" in {
      def seqFib(n: Int): Int = if (n < 2) n else seqFib(n - 1) + seqFib(n - 2)

      def fib(n: Int): Future[Int] =
        if (n < 30)
          f(seqFib(n))
        else
          (fib(n - 1) ⊛ fib(n - 2))(_ + _)

      fib(40).getOrThrow must_== 102334155
    }

    "sequence a list" in {
      val result = (1 to 1000).toList.map((10 * (_: Int)).future).sequence.getOrThrow
      result must haveSize(1000)
      result.head must_== 10
    }

    "map a list in parallel" in {
      val result = futureMap((1 to 1000).toList)(10*).getOrThrow
      result must haveSize(1000)
      result.head must_== 10
    }

    "reduce a list of futures" in {
      val list = (1 to 100).toList.fpure[Future]
      list.reduceLeft((a,b) => (a ⊛ b)(_ + _)).getOrThrow must_== 5050
    }

    "fold into a future" in {
      val list = (1 to 100).toList
      list.foldLeftM(0)((b,a) => f(b + a)).getOrThrow must_== 5050
    }

    "have a resetable timeout" in {
      f("test").timeout(100).await mustNot throwA(new FutureTimeoutException("Futures timed out after [100] milliseconds"))
      f({Thread.sleep(500);"test"}).timeout(100).await must throwA(new FutureTimeoutException("Futures timed out after [100] milliseconds"))
    }

    "convert to Validation" in {
      val r1 = (f("34".toInt) ⊛ f("150".toInt) ⊛ f("12".toInt))(_ + _ + _)
      r1.toValidation must_== Success(196)
      val r2 = (f("34".toInt) ⊛ f("hello".toInt) ⊛ f("12".toInt))(_ + _ + _)
      r2.toValidation.fail.map(_.toString).validation must_== Failure("java.lang.NumberFormatException: For input string: \"hello\"")
    }

    "for-comprehension" in {
      val r1 = for {
        x1 <- f("34".toInt)
        x2 <- f("150".toInt)
        x3 <- f("12".toInt)
      } yield x1 + x2 + x3

      r1.getOrThrow must_== 196

      val r2 = for {
        x1 <- f("34".toInt)
        x2 <- f("hello".toInt)
        x3 <- f("12".toInt)
      } yield x1 + x2 + x3

      r2.getOrThrow must throwA(new NumberFormatException("For input string: \"hello\""))
    }

    "Kleisli composition" in {
      val f = ((_: String).toInt).future
      val g = ((_: Int) * 2).future
      val h = ((_: Int) * 10).future

      (f apply "3" get) must_== success(3)
      (f >=> g apply "3" get) must_== success(6)
      (f >=> h apply "3" getOrThrow) must_== 30
      (f >=> g >=> h apply "3" getOrThrow) must_== 60
      (f >=> g >=> h apply "blah" getOrThrow) must throwA(new NumberFormatException("For input string: \"blah\""))
      (f >=> (g &&& h) apply "3" getOrThrow) must_== (6, 30)
      ((f *** f) >=> (g *** h) apply ("3", "7") getOrThrow) must_== (6, 70)
      ((f *** f) >=> (g *** h) apply ("3", "blah") getOrThrow) must throwA(new NumberFormatException("For input string: \"blah\""))
    }

    "Kleisli composition with actors" in {
      val a1 = actorOf[DoubleActor].start
      val a2 = actorOf[ToStringActor].start
      val k1 = a1.kleisli
      val k2 = a2.kleisli
      val l = (1 to 5).toList

      (l map k1 sequence).getOrThrow must_== List(2, 4, 6, 8, 10)
      (l map (k1 >=> k2) sequence).getOrThrow must_== List("Int: 2", "Int: 4", "Int: 6", "Int: 8", "Int: 10")
      (l map (k1 &&& (k1 >=> k2)) sequence).getOrThrow must_== List((2, "Int: 2"), (4, "Int: 4"), (6, "Int: 6"), (8, "Int: 8"), (10, "Int: 10"))

      val f = ((_: String).toInt).future
      val g = ((_: Int) * 2).future
      val h = ((_: Int) * 10).future

      ((f *** f) >=> (g *** h) >=> (k1 *** k2) apply ("3", "7") getOrThrow) must_== (12, "Int: 70")

      a1.stop
      a2.stop
    }

    // Taken from Haskell example, performance is very poor, this is only here as a test
    "quicksort a list" in {
      val rnd = new scala.util.Random(1)
      val list = List.fill(1000)(rnd.nextInt)

      def qsort[T](in: List[T])(implicit ord: math.Ordering[T]): Future[List[T]] = in match {
        case Nil => nil.pure[Future]
        case x :: Nil => List(x).pure[Future]
        case x :: y :: Nil => (if (ord.lt(x,y)) List(x,y) else List(y,x)).pure[Future]
        case x :: xs => (qsort(xs.filter(ord.lt(_,x))) ⊛ x.pure[Future] ⊛ qsort(xs.filter(ord.gteq(_,x))))(_ ::: _ :: _)
      }

      qsort(list).getOrThrow must containInOrder(list.sorted)
    }

    "shakespeare wordcount" in {
      import java.io.{InputStreamReader, BufferedReader}
      val reader = new BufferedReader(new InputStreamReader(getClass.getClassLoader.getResourceAsStream("shakespeare.txt")))

      val lines = try {
        Stream.continually(Option(reader.readLine)).filterNot(_ == Some("")).takeWhile(_.isDefined).grouped(500).map(_.flatten.mkString(" ")).toList
      } finally {
        reader.close
      }

      def mapper[M[_]: Monad: Foldable](in: M[String]) =
        in map ((_: String).toLowerCase.filter(c => c.isLetterOrDigit || c.isSpaceChar).split(' '): Seq[String]).future

      def reducer[M[_]: Monad: Foldable, N[_]: Foldable, A](in: M[Future[N[A]]]) =
        in.foldl(Map[A,Int]().pure[Future])((fr, fn) => fr flatMap (r => fn map (_.foldl(r)(incr(_,_)))))

      def incr[A](m: Map[A, Int], a: A) = m + (a -> (m.getOrElse(a, 0) + 1))

      def wordcount[M[_]: Monad: Foldable](in: M[String]) =
        reducer(mapper(in)).getOrThrow

      val wc = bench("Wordcount")(wordcount(lines))
      wc must haveSize(28344)
      wc must havePair("shakespeare", 268)
    }
  }

  def bench[A](name: String)(a: => A): A = {
    val startTime = System.currentTimeMillis
    val result = a
    val endTime = System.currentTimeMillis
    log.info("%s took %dms", name, endTime - startTime)
    result
  } 
}

class DoubleActor extends Actor {
  def receive = {
    case i: Int => self reply (i*2)
  }
}

class ToStringActor extends Actor {
  def receive = {
    case i: Int => self reply ("Int: "+ i)
  }
}

