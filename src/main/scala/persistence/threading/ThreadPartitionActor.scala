package persistence.threading

import akka.actor.Actor
import persistence.io.IOTask


object ThreadPartitionActor {


  def props
}


class ThreadPartitionActor extends Actor {


  override def receive: Receive = {

    case ioTask: IOTask => {

    }
  }
}
