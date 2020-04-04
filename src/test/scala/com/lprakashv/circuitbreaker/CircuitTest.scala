package com.lprakashv.circuitbreaker

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CircuitTest extends AnyFunSuite {
  test("test successes with CircuitImplicits") {

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

    val sampleCircuit: Circuit[Int] =
      new Circuit[Int]("sample-circuit", 5, 5.seconds, IO.pure(1))

    // to test concurrent executions
    val resultsF: IO[List[CircuitResult[Int]]] =
      (1 to 500).toList
        .map(_ => sampleCircuit.withCircuitBreaker(IO.pure(2 + 2)))
        .sequence

    assert(
      sampleCircuit
        .withCircuitBreaker(IO.pure(1 * 34 / 3))
        .unsafeRunSync()
        .isSuccess
    )

    assert(resultsF.unsafeRunSync().flatMap(_.toOption).count(_ == 4) == 500)
  }

  test(
    "[on continuous invalid execution] " +
      "failures 5 times"
  ) {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

    val sampleCircuit: Circuit[Int] =
      new Circuit[Int]("sample-circuit", 5, 5.seconds, IO.pure(1))

    val x = 5
    val y = x - 2 - 3

    val resultsF: List[IO[CircuitResult[Int]]] = (for {
      _ <- 1 to 5
    } yield sampleCircuit.withCircuitBreaker(IO.apply(1 / y))).toList

    resultsF.sequence
      .map(list => assert(list.forall(_.isFailed)))
      .unsafeRunSync()
  }

  test(
    "[on continuous invalid execution] " +
      "success with default answer after failure 5 times"
  ) {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

    val sampleCircuit: Circuit[Int] =
      new Circuit[Int]("sample-circuit", 5, 5.seconds, IO.pure(1))

    val x = 0

    val results: List[CircuitResult[Int]] =
      (1 to 6)
        .map(_ => sampleCircuit.withCircuitBreaker(IO.apply(1 / x)))
        .toList
        .sequence
        .unsafeRunSync()

    Thread.sleep(1000)

    val results2: List[CircuitResult[Int]] =
      (1 to 6)
        .map(_ => sampleCircuit.withCircuitBreaker(IO.apply(1 / x)))
        .toList
        .sequence
        .unsafeRunSync()

    assert(
      results.flatMap(_.toOption).isEmpty,
      "failed to verify 7 successes (default case in open circuit) after 5 failures on 12 invalid executions"
    )

    assert(
      results2.flatMap(_.toOption).forall(_ == -1),
      "failed to verify 7 successes (default case in open circuit) after 5 failures on 12 invalid executions"
    )
  }

  test(
    "[on continuous invalid execution] " +
      "failure 5 times and then successes then failure after timeout (5 sec) and then successes again"
  ) {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

    val sampleCircuit: Circuit[Int] =
      new Circuit[Int]("sample-circuit", 5, 5.seconds, IO.pure(1))

    val x = 0

    // 5 failures and then 5 successes
    val results: List[CircuitResult[Int]] =
      ((1 to 10)
        .map(_ => sampleCircuit.withCircuitBreaker(IO.apply(1 / x))))
        .toList
        .sequence
        .unsafeRunSync()

    assert(
      results.flatMap(_.toOption).count(_ == -1) == 5,
      "failed to verify - 5 failures and 5 successes for 10 invalid executions"
    )

    Thread.sleep(5100)

    assert(
      sampleCircuit.withCircuitBreaker(IO.apply(1 / x)).unsafeRunSync.isFailed,
      "failed to verify failure after timeout (showing half-open try)"
    )

    assert(
      sampleCircuit
        .withCircuitBreaker(IO.apply(1 / x))
        .unsafeRunSync
        .toOption
        .contains(-1),
      "failed to verify success showing open (default case) after half-open failure"
    )

    Thread.sleep(5200)

    val results2: IO[List[CircuitResult[Int]]] =
      (1 to 5)
        .map(_ => sampleCircuit.withCircuitBreaker(IO.pure(7 * 7)))
        .toList
        .sequence

    assert(
      results2.unsafeRunSync().flatMap(_.toOption).forall(_ == 49),
      "failed to verify successes with valid results after timeout showing closed after half-open success"
    )
  }

  test(
    "failure 5 times and then default successes even after having valid execution"
  ) {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

    val sampleCircuit: Circuit[Int] =
      new Circuit[Int]("sample-circuit", 5, 5.seconds, IO.pure(1))

    val x = 0

    (1 to 5)
      .map(_ => sampleCircuit.withCircuitBreaker(IO.apply(1 / x)))
      .toList
      .sequence

    assert(
      sampleCircuit
        .withCircuitBreaker(IO.apply(1 / x))
        .unsafeRunSync()
        .toOption
        .contains(-1),
      "failed to validate circuit failure after reaching failure threshold"
    )

    val results: IO[List[CircuitResult[Int]]] =
      ((1 to 5)
        .map(_ => sampleCircuit.withCircuitBreaker(IO.apply(7 * 7))))
        .toList
        .sequence

    assert(results.unsafeRunSync().flatMap(_.toOption).forall(_ == -1))
  }
}
