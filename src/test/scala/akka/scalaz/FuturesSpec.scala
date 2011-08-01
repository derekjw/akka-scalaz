package akka.scalaz.futures

import scalaz._
import Scalaz._

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary
import scalaz.scalacheck.{ ScalazProperty, ScalazArbitrary, ScalaCheckBinding }
import ScalaCheckBinding._
import ScalazArbitrary._

import akka.dispatch._
import akka.actor.Actor
import Actor._

class AkkaFuturesSpec extends WordSpec with ShouldMatchers with Checkers {

  implicit def FutureEqual[A: Equal] = Scalaz.equal[Future[A]](a1 => a2 => a1.get ≟ a2.get)

  implicit def FutureArbitrary[A](implicit arb: Arbitrary[A]): Arbitrary[Future[A]] = arb map ((a: A) => new KeptPromise(Right(a)))

  def aFunctor {
    import ScalazProperty.Functor._
    "satisfy the functor law of identity" in check(identity[Future, Int])
    //"satisfy the functor law of associativity" in check(associative[Future, Int, Int, Int])
  }

  def aMonad {
    import ScalazProperty.Monad._
    "satisfy the monad law of left identity" in check(leftIdentity[Future, Int, Int])
    "satisfy the monad law of right identity" in check(rightIdentity[Future, Int])
    "satisfy the monad law of associativity" in check(associativity[Future, Int, Int, Int])
  }

  def anApplicative {
    import ScalazProperty.Applicative._
    "satisfy the applicative law of identity" in check(identity[Future, Int])
    "satisfy the applicative law of composition" in check(composition[Future, Int, Int, Int])
    "satisfy the applicative law of homomorphism" in check(homomorphism[Future, Int, Int])
    "satisfy the applicative law of interchange" in check(interchange[Future, Int, Int])
  }

  def aSemigroup {
    import ScalazProperty.Semigroup._
    "satisfy the semigroup law of associativity" in check(associative[Future[Int]])
  }

  def aMonoid {
    import ScalazProperty.Monoid._
    "satisfy the monoid law of identity" in check(identity[Future[Int]])
  }

  "A Future" should {
    behave like aFunctor
    behave like aMonad
    behave like anApplicative
    behave like aSemigroup
    behave like aMonoid

    "have scalaz functor instance" in {
      val f1 = Future(5 * 5)
      val f2 = f1 map (_ * 2)
      val f3 = f2 map (_ * 10)
      val f4 = f1 map (_ / 0)
      val f5 = f4 map (_ * 10)

      f2.get should equal(50)
      f3.get should equal(500)
      evaluating(f4.get) should produce[ArithmeticException]
      evaluating(f5.get) should produce[ArithmeticException]
    }

    "have scalaz bind instance" in {
      val f1 = Future(5 * 5)
      val f2 = f1 >>= (n => Future(n * 2))
      val f3 = f2 >>= (n => Future(n * 10))
      val f4 = f1 >>= (n => Future(n / 0))
      val f5 = f4 >>= (n => Future(n * 10))

      f2.get should equal(50)
      f3.get should equal(500)
      evaluating(f4.get) should produce[ArithmeticException]
      evaluating(f5.get) should produce[ArithmeticException]
    }

    "have scalaz apply instance" in {
      val f1 = Future(5 * 5)
      val f2 = f1 map (_ * 2)
      val f3 = f2 map (_ / 0)

      (f1 |@| f2)(_ * _).get should equal(1250)
      (f1 |@| f2).tupled.get should equal(25, 50)
      evaluating((f1 |@| f2 |@| f3)(_ * _ * _).get) should produce[ArithmeticException]
      evaluating((f3 |@| f2 |@| f1)(_ * _ * _).get) should produce[ArithmeticException]
      (f1 <|*|> f2).get should equal(25, 50)
    }

/*    "have scalaz comonad instance" in {
      val f = Future("Result") =>> (_ map (_.toUpperCase)) >>= (_ map (s => s + s))
      f.get should equal("RESULTRESULT")
    }*/

    "calculate fib seq" in {
      def seqFib(n: Int): Int = if (n < 2) n else seqFib(n - 1) + seqFib(n - 2)

      def fib(n: Int): Future[Int] =
        if (n < 30)
          Future(seqFib(n))
        else
          (fib(n - 1) |@| fib(n - 2))(_ + _)

      fib(40).get should equal(102334155)
    }

    "traverse a list into a future" in {
      val result = (1 to 1000).toList.traverse(n => Future(n * 10)).get
      result should have size (1000)
      result.head should equal(10)
    }

    "reduce a list of futures" in {
      val list = (1 to 100).toList.fpoint[Future]
      list.reduceLeft((a, b) => (a |@| b)(_ + _)).get should equal(5050)
    }

    "fold into a future" in {
      val list = (1 to 100).toList
      list.foldlM(0)(b => a => Future(b + a)).get should equal(5050)
    }

    "convert to Validation" in {
      val r1 = (Future("34".toInt) |@| Future("150".toInt) |@| Future("12".toInt))(_ + _ + _)
      r1.liftValidation.get should equal(196.success)
      val r2 = (Future("34".toInt) |@| Future("hello".toInt) |@| Future("12".toInt))(_ + _ + _)
      r2.liftValidation.get.fail.map(_.toString).validation should equal(("java.lang.NumberFormatException: For input string: \"hello\"").fail)
    }

    "for-comprehension" in {
      val r1 = for {
        x1 <- Future("34".toInt)
        x2 <- Future("150".toInt)
        x3 <- Future("12".toInt)
      } yield x1 + x2 + x3

      r1.get should equal(196)

      val r2 = for {
        x1 <- Future("34".toInt)
        x2 <- Future("hello".toInt)
        x3 <- Future("12".toInt)
      } yield x1 + x2 + x3

      evaluating(r2.get) should produce[NumberFormatException]
    }

    "compose" in {
      val f = Kleisli(((_: String).toInt).future)
      val g = Kleisli(((_: Int) * 2).future)
      val h = Kleisli(((_: Int) * 10).future)

      (f run "3" get) should equal(3)
      (f >=> g run "3" get) should equal(6)
      (f >=> h run "3" get) should equal(30)
      (f >=> g >=> h run "3" get) should equal(60)
      (f >=> (g &&& h) run "3" get) should equal(6, 30)
      ((f *** f) >=> (g *** h) run ("3", "7") get) should equal(6, 70)
      evaluating(f >=> g >=> h run "blah" get) should produce[NumberFormatException]
      evaluating((f *** f) >=> (g *** h) run ("3", "blah") get) should produce[NumberFormatException]
    }

    "compose with actors" in {
      val a1 = actorOf[DoubleActor].start
      val a2 = actorOf[ToStringActor].start
      val k1 = Kleisli(a1.future)
      val k2 = Kleisli(a2.future)
      val l = (1 to 5).toList

      //a1(5).get should equal(10)

      (l traverse k1.run).get should equal(List(2, 4, 6, 8, 10))
      (l traverse (k1 >=> k2).run).get should equal(List("Int: 2", "Int: 4", "Int: 6", "Int: 8", "Int: 10"))

      val f = Kleisli(((_: String).toInt).future)
      val g = Kleisli(((_: Int) * 2).future)
      val h = Kleisli(((_: Int) * 10).future)

      val fn = (n: Int) => (k1 >=> k2) run n collect {
        case "Int: 10" => "10"
        case _         => "failure"
      } >>= (f >=> g).run
      fn(5).get should equal(20)
      evaluating(fn(10).get) should produce[NumberFormatException]

      a1.stop
      a2.stop
    }

    "Plus" in {
      val r1 = Future(1)
      val r2 = Future(2)
      val e1 = Future(1 / 0)
      val e2 = Future("Hello".toInt)

      (r1 <+> r2).get should equal(1)
      (r2 <+> e1).get should equal(2)
      (e2 <+> r1).get should equal(1)
      (e1 <+> e2 <+> r1 <+> r2).get should equal(1)
    }

    "Semigroups" in {
      (Future(3) |+| Future(4)).get should equal(7)
      (Future(List(1, 2, 3)) |+| Future(List(4, 5, 6))).get should equal(List(1, 2, 3, 4, 5, 6))
    }

    "Monoids" in {
      val doubler = ((_: Int) * 2).fpoint[Future]

      (List(1, 2, 3, 4, 5).fpoint[Future].suml).get should equal(15)
      (List(1, 2, 3, 4, 5) foldMapDefault doubler).get should equal(30)
      (nil[Int] foldMapDefault doubler).get should equal(0)

      (1 +>: 2 +>: 3 +>: doubler(10) |+| doubler(100) map (_ |+| 300)).get should equal(526)

      1.unfold[Future, String](x => (x < 5).option((x.toString, x + 1))).get should equal("1234")
    }

    // Taken from Haskell example, performance is very poor, this is only here as a test
    "quicksort a list" in {
      val rnd = new scala.util.Random(1)
      val list = List.fill(1000)(rnd.nextInt)

      def qsort[T: Order](in: List[T]): Future[List[T]] = in match {
        case Nil           => nil.point[Future]
        case x :: Nil      => List(x).point[Future]
        case x :: y :: Nil => (if (x lt y) List(x, y) else List(y, x)).point[Future]
        case x :: xs       => (Future(qsort(xs.filter(x.gt))).join |@| x.point[Future] |@| Future(qsort(xs.filter(x.lte))).join)(_ ::: _ :: _)
      }

      qsort(list).get should equal(list.sorted)
    }
  }
}

class DoubleActor extends Actor {
  def receive = {
    case i: Int => self reply (i * 2)
  }
}

class ToStringActor extends Actor {
  def receive = {
    case i: Int => self reply ("Int: " + i)
  }
}

