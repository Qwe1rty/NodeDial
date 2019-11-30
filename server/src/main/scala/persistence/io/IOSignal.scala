package persistence.io

import scala.util.Try


sealed trait IOSignal


case class ReadCompleteSignal(result: Try[Array[Byte]]) extends IOSignal
case class WriteCompleteSignal(result: Try[Unit]) extends IOSignal

case class WriteAheadCommitSignal() extends IOSignal
case class WriteAheadFailureSignal(e: Exception) extends IOSignal
