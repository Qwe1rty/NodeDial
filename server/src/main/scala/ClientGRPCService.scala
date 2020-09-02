import administration.Administration.{AdministrationAPI, GetReadiness}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.Materializer
import com.google.protobuf.ByteString
import io.jvm.uuid.UUID
import org.slf4j.LoggerFactory
import persistence.PersistenceComponent.PersistenceData
import replication.ReplicationComponent.{ClientOperation, DeleteOperation, ReadOperation, ReplicatedConfirmation, WriteOperation}
import schema.ImplicitGrpcConversions._
import schema.PortConfiguration.EXTERNAL_REQUEST_PORT
import schema.service.Request.{DeleteRequest, GetRequest, PostRequest}
import schema.service.Response.{DeleteResponse, GetResponse, PostResponse}
import schema.service._

import scala.concurrent.{ExecutionContext, Future, Promise}


class ClientGRPCService(
    replicationComponent: ActorRef[ClientOperation],
    administration: ActorRef[AdministrationAPI],
  )
  (implicit actorSystem: ActorSystem[_])
  extends RequestService {

  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  final private val log = LoggerFactory.getLogger(ClientGRPCService.getClass)
  final private val service: HttpRequest => Future[HttpResponse] = RequestServiceHandler(this)(
    Materializer.matFromSystem(actorSystem),
    actorSystem.classicSystem
  )

  Http()(actorSystem.classicSystem)
    .bindAndHandleAsync(service, interface = "0.0.0.0", port = EXTERNAL_REQUEST_PORT, HttpConnectionContext())
    .foreach(binding => log.info(s"gRPC request service bound to ${binding.localAddress}"))

  import common.ServerDefaults.EXTERNAL_REQUEST_TIMEOUT


  override def get(in: GetRequest): Future[GetResponse] = {
    val uuid = UUID.random
    log.debug(s"Get request received with key '${in.key}' and UUID $uuid")

    if (in.key.isBlank) {
      Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
    }
    else {
      val readPromise = Promise[PersistenceData]()
      replicationComponent ! ReadOperation(readPromise, in.key, uuid)
      readPromise.future.map(data => GetResponse(data.map(byteArrayToByteString)))
    }
  }

  override def post(in: PostRequest): Future[PostResponse] = {
    val uuid = UUID.random
    log.debug(s"Post request received with key '${in.key}' and UUID $uuid")

    if (in.key.isBlank) {
      Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
    }
    else {
      val writePromise = Promise[ReplicatedConfirmation]()
      replicationComponent ! WriteOperation(writePromise, in.key, in.value, uuid)
      writePromise.future.map(_ => PostResponse())
    }
  }

  override def delete(in: DeleteRequest): Future[DeleteResponse] = {
    val uuid = UUID.random
    log.debug(s"Delete request received with key '${in.key}' and UUID $uuid")

    if (in.key.isBlank) {
      Future.failed(new IllegalArgumentException("Key value cannot be empty or undefined"))
    }
    else {
      val deletePromise = Promise[ReplicatedConfirmation]()
      replicationComponent ! DeleteOperation(deletePromise, in.key, uuid)
      deletePromise.future.map(_ => DeleteResponse())
    }
  }

  override def readiness(in: ReadinessCheck): Future[ReadinessConfirmation] = {
    log.debug(s"Readiness check received")

    administration
      .ask(GetReadiness(_: ActorRef[Boolean]))
      .map { readiness =>
        log.debug(s"Readiness check response with: $readiness")
        ReadinessConfirmation(readiness)
      }
  }
}

object ClientGRPCService {

  def apply(
      replicationComponent: ActorRef[ClientOperation],
      membershipActor: ActorRef[AdministrationAPI]
    )
    (implicit actorSystem: ActorSystem[_]): RequestService =
    new ClientGRPCService(replicationComponent, membershipActor)
}