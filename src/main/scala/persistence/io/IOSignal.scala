package persistence.io

import akka.stream.IOResult

import scala.util.Try


sealed trait IOSignal {

  def result: Try[IOResult]
}


case class ReadCommitSignal(result: Try[IOResult]) extends IOSignal
case class WriteAheadCommitSignal(result: Try[IOResult]) extends IOSignal
case class WriteTransferCommitSignal(result: Try[IOResult]) extends IOSignal
case class TombstoneCommitSignal(result: Try[IOResult]) extends IOSignal
