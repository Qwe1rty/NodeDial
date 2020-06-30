package replication.roles

import akka.actor.FSM
import replication.{RaftRequest, RaftState}


/**
 * The raft roles (Leader, Candidate, Follow) essentially follow a finite state
 * machine pattern, so this trait encapsulates that logic, on top of
 */
trait RaftRoleFSM extends FSM[RaftRole, RaftState] {

  type RoleState = RaftRole

  startWith(Leader, RaftState())

  when(Leader) {
    case Event(event: RaftRequest, state: RaftState) =>
      Leader.processRaftEvent(event, state)
      stay.using(state)
  }
}
