package mrsc.pfp.sll.samples

import scala.collection.immutable.TreeSet

import mrsc.core._
import mrsc.pfp._
import mrsc.pfp.sll._

object Counting extends App {

  case class CountingResult(completed: Integer, residuals: Set[Expr])

  val program: Program =
    """
    gApp(Cons(u, us), vs) = Cons(u, gApp(us, vs));
    gApp(Nil(), vs) = vs;

    gRev(Cons(x, xs))= gApp(gRev(xs), Cons(x, Nil()));
    gRev(Nil()) = Nil();

    gFRev(Cons(x, xs), ys) = gFRev(xs, Cons(x, ys));
    gFRev(Nil(), ys) = ys;

    gLastNil(Cons(x, xs)) = gLastNil(xs);
    gLastNil(Nil()) = Nil(); 
    """

  val tasks = List(
    SLLTask("gApp(xs, ys)", program),
    SLLTask("gApp(xs, xs)", program),
    SLLTask("gLastNil(gApp(xs, xs))", program),

    SLLTask("gRev(xs)", program),
    SLLTask("gFRev(xs, Nil())", program),

    SLLTask("gRev(gRev(xs))", program),
    SLLTask("gFRev(gRev(xs), Nil())", program),
    SLLTask("gRev(gFRev(xs, Nil()))", program),
    SLLTask("gFRev(gFRev(xs, Nil()), Nil())", program),

    SLLTask("gLastNil(gRev(gRev(xs)))", program),
    SLLTask("gLastNil(gFRev(gRev(xs), Nil()))", program),
    SLLTask("gLastNil(gRev(gFRev(xs, Nil())))", program),
    SLLTask("gLastNil(gFRev(gFRev(xs, Nil()), Nil()))", program),

    SLLTask("gApp(gRev(xs), ys)", program),
    SLLTask("gApp(gRev(xs), xs)", program),
    SLLTask("gApp(xs, gRev(ys))", program),
    SLLTask("gApp(xs, gRev(xs))", program),
    SLLTask("gApp(gRev(xs), gRev(ys))", program),
    SLLTask("gApp(gRev(xs), gRev(xs))", program))

  implicit val exprOrdering: Ordering[Expr] = Ordering.by(_.size)

  def sc(gen: Iterator[SGraph[Expr, DriveInfo[Expr]]], limit: Int): Either[CountingResult, CountingResult] = {
    var completed = 0
    var unworkable = 0
    var residuals = TreeSet[Expr]()
    for (g <- gen) {
      completed += 1
      val tg = Transformations.transpose(g)
      val expr = SLLResiduator.residuate(tg)
      residuals += expr
      if (completed > limit) {
        return Left(CountingResult(completed, residuals))
      }
    }
    Right(CountingResult(completed, residuals))
  }

  def compareScWithBinaryWhistle(task: SLLTask, whistle: PartialOrdering[Expr], limit: Int = 5000): Unit = {
    println()
    print(task.target + " ")

    val machines = List(
      new SC1(task.program, whistle),
      new SC2(task.program, whistle),
      new SC3(task.program, whistle),
      new SC4(task.program, whistle),
      new SC5(task.program, whistle),
      new SC6(task.program, whistle),
      new SC7(task.program, whistle))

    machines.foreach { m =>
      val gen = new GraphGenerator(m, task.target)
      val res = sc(gen, limit)
      res match {
        case Left(res) =>
          print("& " + res.residuals.size + "/" + res.completed)
        case Right(res) =>
          print(" & " + res.residuals.size + "/" + res.completed)
      }
    }
    println(""" \\""")
  }

  println("======================")
  println("====   Coupling   ====")
  println("======================")
  tasks.foreach(compareScWithBinaryWhistle(_, HEByCouplingWhistle, 1000000))
  println("======================")
  println("==== CouplingRedex ====")
  println("======================")
  tasks.foreach(compareScWithBinaryWhistle(_, HEByCouplingWithRedexWhistle, 1000000))
}