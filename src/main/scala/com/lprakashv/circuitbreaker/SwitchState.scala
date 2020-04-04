package com.lprakashv.circuitbreaker

sealed trait SwitchState

object SwitchState {
  case object Open extends SwitchState
  case object Closed extends SwitchState
  case object HalfOpen extends SwitchState

}
