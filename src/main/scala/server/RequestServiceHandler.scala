package server

import akka.stream.Materializer

import scala.concurrent.Future

import chordial.server.{
  DeleteRequest,
  DeleteResponse,
  GetRequest,
  GetResponse,
  PostRequest,
  PostResponse,
  RequestService
}

class RequestServiceHandler(implicit mat: Materializer) extends RequestService {

  override def get(in: GetRequest): Future[GetResponse] = ???
  override def post(in: PostRequest): Future[PostResponse] = ???
  override def delete(in: DeleteRequest): Future[DeleteResponse] = ???
}
