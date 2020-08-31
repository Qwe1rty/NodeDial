package common.rpc

import membership.Membership


/**
 * An RPC task represents some sort of network task that needs to be completed.
 * For example, this can be replying to an RPC, or broadcasting a message across to
 * all known servers
 *
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
sealed trait RPCTask[+RPCObject] {
  def task: RPCObject
}


/**
 * RPCTaskHandler is an interface for specifying the side-effecting network calls
 * that result from an RPCTask
 *
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
trait RPCTaskHandler[-RPCObject] {

  /**
   * Make the network calls as dictated by the RPC task
   *
   * @param rpcTask the RPC task
   */
  def processRPCTask(rpcTask: RPCTask[RPCObject]): Unit
}


/**
 * Represents a "broadcast" network task
 *
 * @param task the RPC task
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
case class BroadcastTask[RPCObject](task: RPCObject) extends RPCTask[RPCObject]


/**
 * Represents a "request" network task
 *
 * @param task the RPC task
 * @param recipient the address to send the request to
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
case class RequestTask[RPCObject](task: RPCObject, recipient: Membership) extends RPCTask[RPCObject]


/**
 * Represents an asynchronous/future "request" network task
 *
 * @param task the RPC task
 * @param recipient the address to send the request to
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
case class ReplyFutureTask[RPCObject](task: RPCObject, recipient: Membership) extends RPCTask[RPCObject]


/**
 * Represents a "reply" network task
 *
 * @param task the RPC task
 * @tparam RPCObject the RPC object associated with the task (eg. request
 *                   parameters for an RPC)
 */
case class ReplyTask[RPCObject](task: RPCObject) extends RPCTask[RPCObject]


/**
 * Represents a "no task"
 */
case object NoTask extends RPCTask[Nothing] {

  /**
   * Contains no task, and will just throw a NoSuchElementException
   *
   * @return returns nothing, throws NoSuchElementException
   */
  override def task: Nothing =
    throw new NoSuchElementException("NothingTask does not contain a task")
}