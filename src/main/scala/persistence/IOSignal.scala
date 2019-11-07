package persistence

sealed trait IOSignal


case object ReadCommittedSignal extends IOSignal
case object WriteAheadCommittedSignal extends IOSignal
case object WriteTransferCommittedSignal extends IOSignal
case object TombstoneCommittedSignal extends IOSignal
