package replication.roles


sealed trait RPCTask[+Task] {
  def task: Task
}


case class BroadcastTask[Task](task: Task) extends RPCTask[Task]

case class RequestTask[Task](task: Task) extends RPCTask[Task]

case class ReplyTask[Task](task: Task) extends RPCTask[Task]

