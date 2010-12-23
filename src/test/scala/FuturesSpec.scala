package akka.scalaz

import org.specs._

import scalaz._
import Scalaz._

import akka.dispatch._
import akka.actor.Actor.TIMEOUT
import Futures.future

import akka.util.Logging

class AkkaFuturesSpec extends Specification with Logging {
  import AkkaFutures._

  def f[A](a: => A):Future[A] = future(TIMEOUT)(a)

  "akka futures" should {
    "have scalaz functor instance" in {
      val f1 = f(5 * 5)
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
      val f1 = f(5 * 5)
      val f2 = f1 >>= (x => f(x * 2))
      val f3 = f2 >>= (x => f(x * 10))
      val f4 = f1 >>= (x => f(x / 0))
      val f5 = f4 >>= (x => f(x * 10))

      f2.await.resultOrException must_== Some(50)
      f3.await.resultOrException must_== Some(500)
      f4.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
      f5.await.resultOrException must throwA(new ArithmeticException("/ by zero"))
    }

    "have scalaz apply instance" in {
      val f1 = f(5 * 5)
      val f2 = f1 ∘ (_ * 2)
      val f3 = f2 ∘ (_ / 0)

      (f1 ⊛ f2)(_ * _).await.resultOrException must_== Some(1250)
      (f1 ⊛ f2).tupled.await.resultOrException must_== Some((25,50))
      (f1 ⊛ f2 ⊛ f3)(_ * _ * _).await.resultOrException must throwA(new ArithmeticException("/ by zero"))
      (f3 ⊛ f2 ⊛ f1)(_ * _ * _).await.resultOrException must throwA(new ArithmeticException("/ by zero"))

      (f1 <|**|> (f2, f1)).await.resultOrException must_== Some((25,50,25))
    }

    "calculate fib seq" in {
      def seqFib(n: Int): Int = if (n < 2) n else seqFib(n - 1) + seqFib(n - 2)

      def fib(n: Int): Future[Int] =
        if (n < 30)
          f(seqFib(n))
        else
          (fib(n - 1) ⊛ fib(n - 2))(_ + _)

      fib(40).await.result must_== Some(102334155)
    }

    "sequence a list" in {
      val result = (1 to 10000).toList.map(_.pure[Future] ∘ (10 *)).sequence.await.result
      result.map(_.size) must_== Some(10000)
      result.map(_.head) must_== Some(10)
    }

    "map a list in parallel" in {
      val result = (1 to 10000).toList.futureMap(10*).await.result
      result.map(_.size) must_== Some(10000)
      result.map(_.head) must_== Some(10)
    }

    "reduce a list of futures" in {
      val list = (1 to 100).toList.map(_.pure[Future])
      list.reduceLeft((a,b) => (a ⊛ b)(_ + _)).await.result must_== Some(5050)
    }

    "fold into a future" in {
      val list = (1 to 100).toList
      list.foldLeftM(0)((b,a) => f(b + a)).await.result must_== Some(5050)
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

      r1.await.resultOrException must_== Some(196)

      val r2 = for {
        x1 <- f("34".toInt)
        x2 <- f("hello".toInt)
        x3 <- f("12".toInt)
      } yield x1 + x2 + x3

      r2.await.resultOrException must throwA(new NumberFormatException("For input string: \"hello\""))
    }

    // Taken from Haskell example, performance is very poor, this is only here as a test
    "quicksort a list" in {
      val rnd = new scala.util.Random(1)
      val list = List.fill(1000)(rnd.nextInt)

      def qsort[T](in: List[T])(implicit ord: math.Ordering[T]): Future[List[T]] = in match {
        case Nil ⇒ nil.pure[Future]
        case x :: xs ⇒ (qsort(xs.filter(ord.lt(_,x))) ⊛ x.pure[Future] ⊛ qsort(xs.filter(ord.gteq(_,x))))(_ ::: _ :: _)
      }

      qsort(list).await.result must beSome[List[Int]].which(_ == list.sorted)
    }

    "shakespeare wordcount" in {
      import java.io.{InputStreamReader, BufferedReader}
      val reader = new BufferedReader(new InputStreamReader(getClass.getClassLoader.getResourceAsStream("shakespeare.txt")))

      val lines = try {
        Stream.continually(Option(reader.readLine)).filterNot(_ == Some("")).takeWhile(_.isDefined).grouped(100).map(_.flatten.mkString(" ")).toList
      } finally {
        reader.close
      }

      def mapper(in: Seq[String]): Seq[Future[Seq[String]]] =
        in.map(s => f(s.toLowerCase.filter(c => c.isLetterOrDigit || c.isSpaceChar).split(' ').toSeq))

      def reducer(in: Seq[Future[Seq[String]]]): Future[Map[String, Int]] =
        in.foldLeft(f(Map[String,Int]().withDefaultValue(0)))((fm,fl) => fm >>= (m => fl ∘ (_.foldLeft(m)((a, b) => a + (b -> (a(b) + 1))))))

      def result(in: Future[Map[String, Int]]): Option[Map[String, Int]] =
        in.await.result

      def wordcount(in: List[String]): Option[Map[String, Int]] =
        result(reducer(mapper(in)))

      val wc = bench("Wordcount")(wordcount(lines))
      wc must beSome.which(_ must haveSize(28344))
      wc.flatMap(_.get("shakespeare")) must beSome.which(_ must_== 268)
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
