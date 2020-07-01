package replication.roles


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
   * @param RPCTask the RPC task
   */
  def handleRPCTask(RPCTask: RPCTask[RPCObject]): Unit
}


case class BroadcastTask[RPCObject](task: RPCObject) extends RPCTask[RPCObject]

case class RequestTask[RPCObject](task: RPCObject) extends RPCTask[RPCObject]

case class ReplyTask[RPCObject](task: RPCObject) extends RPCTask[RPCObject]

