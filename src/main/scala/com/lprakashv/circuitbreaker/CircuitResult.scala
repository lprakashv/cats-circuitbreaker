package com.lprakashv.circuitbreaker

import com.lprakashv.circuitbreaker.CircuitResult.CircuitSuccess

trait CircuitResult[T] extends Product {
  def isFailed: Boolean = this.toOption.isEmpty

  def isSuccess: Boolean = this.toOption.isDefined

  def toOption: Option[T] = this match {
    case CircuitSuccess(value) => Some(value)
    case _                     => None
  }
}

object CircuitResult {
  case class CircuitSuccess[T](value: T) extends CircuitResult[T]

  case class CircuitFailure[T](exception: Throwable) extends CircuitResult[T]
}
