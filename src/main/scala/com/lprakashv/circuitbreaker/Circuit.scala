package com.lprakashv.circuitbreaker

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import com.lprakashv.circuitbreaker.CircuitResult.{
  CircuitFailure,
  CircuitSuccess
}
import com.lprakashv.circuitbreaker.SwitchState.{Closed, HalfOpen, Open}
import io.odin._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class Circuit[R](name: String,
                 private final val maxFailureRatePerSec: Long,
                 private final val openTimeout: FiniteDuration,
                 defaultAction: IO[R],
                 ignoreException: Throwable => Boolean = { _: Throwable =>
                   false
                 })(implicit T: Timer[IO], CS: ContextShift[IO]) {
  val logger: Logger[IO] = consoleLogger()

  val switchState: IO[Ref[IO, SwitchState]] = Ref.of[IO, SwitchState](Closed)
  val totalCumulativeFailures: IO[Ref[IO, Long]] = Ref.of[IO, Long](0L)

  val halfOpeningTimer: IO[Unit] =
    T.sleep(openTimeout).flatMap(_ => changeSwitchState(HalfOpen))

  val failureRateCheckerTimer: Unit = {
    def repeat: IO[Unit] =
      for {
        startingFailuresRef <- totalCumulativeFailures
        startingFailures <- startingFailuresRef.get
        timerFiber <- T.sleep(1.seconds).start
        _ <- timerFiber.join
        currentFailuresRef <- totalCumulativeFailures
        currentFailures <- currentFailuresRef.get
        _ <- if (currentFailures - startingFailures > maxFailureRatePerSec) {
          changeSwitchState(Open)
        } else IO.unit
      } yield ()

    repeat.start
  }.unsafeRunAsync {
    case Left(throwable) =>
      logger.error(s"[$name] ", throwable).unsafeRunAsyncAndForget()
    case Right(_) => ()
  }

  private def changeSwitchState(newState: SwitchState): IO[Unit] =
    for {
      currentStateRef <- switchState
      currentState <- currentStateRef.get
      _ <- logger.debug(
        s"[$name] Updating switch state from $currentState to $newState"
      )
      _ <- currentStateRef.modify(s => (newState, s))
      _ <- logger.debug(s"[$name] switch state changed to $newState")
      _ <- newState match {
        case Open     => halfOpeningTimer.runAsync(_ => IO.unit).toIO
        case Closed   => totalCumulativeFailures.map(l => (0L, l))
        case HalfOpen => totalCumulativeFailures.map(l => (0L, l))
      }
    } yield ()

  def withCircuitBreaker(block: IO[R]): IO[CircuitResult[R]] =
    for {
      currentStateRef <- switchState
      currentState <- currentStateRef.get

      result <- currentState match {
        case Open => defaultAction.map(Success.apply)
        case _ =>
          block.map(Success.apply).handleErrorWith(th => IO.pure(Failure(th)))
      }

      _ <- (currentState, result) match {
        case (HalfOpen, Success(_)) => changeSwitchState(Closed)
        case (HalfOpen, Failure(_)) => changeSwitchState(Open)
        case (_, Failure(_)) =>
          totalCumulativeFailures.map(_.modify(l => (l + 1, l)))
        case _ => IO.unit
      }

      circuitResult <- result match {
        case Success(value) => IO.pure(CircuitSuccess(value))
        case Failure(exception) if ignoreException(exception) =>
          defaultAction.map(CircuitSuccess.apply)
        case Failure(exception) => IO.pure(CircuitFailure[R](exception))
      }
    } yield circuitResult
}
