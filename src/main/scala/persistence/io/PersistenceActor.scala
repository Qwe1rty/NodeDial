package persistence.io

import java.nio.file.{Path, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import better.files.File
import server.datatypes.OperationPackage


object PersistenceActor {

  final val DIRECTORY_FILE: File = File.currentWorkingDirectory/"chordial"


  def props(executorActor: ActorRef): Props = Props(new PersistenceActor(executorActor))
}


class PersistenceActor(executorActor: ActorRef) extends Actor with ActorLogging {

  private var keyMapping = Map[String, ActorRef]()

  PersistenceActor.DIRECTORY_FILE.createDirectoryIfNotExists()


  override def receive: Receive = {

    case operation: OperationPackage => {
      if (!(keyMapping isDefinedAt operation.requestHash)) {
        val actorProps = KeyStateActor.props(executorActor, operation.requestHash)
        keyMapping += operation.requestHash -> context.actorOf(actorProps)
      }
      keyMapping(operation.requestHash) ! operation.requestBody
    }

    case _ => ??? // TODO log error

  }
}