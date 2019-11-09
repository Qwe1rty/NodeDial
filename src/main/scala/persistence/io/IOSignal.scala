package persistence.io

import akka.stream.IOResult

import scala.util.Try


sealed trait IOSignal {

  def result: Try[IOResult]
}


case class ReadCommittedSignal(result: Try[IOResult]) extends IOSignal
case class WriteAheadCommittedSignal(result: Try[IOResult]) extends IOSignal
case class WriteTransferCommittedSignal(result: Try[IOResult]) extends IOSignal
case class TombstoneCommittedSignal(result: Try[IOResult]) extends IOSignal
