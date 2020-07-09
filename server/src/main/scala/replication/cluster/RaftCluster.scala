package replication.cluster

import common.persistence.{JavaSerializer, PersistentVal}
import membership.MembershipTable
import replication.RaftState._


trait RaftCluster {

  val raftMembership: PersistentVal[MembershipTable] =
    new PersistentVal[MembershipTable](RAFT_DIR/"cluster"/RAFT_STATE_EXTENSION) with JavaSerializer[MembershipTable]

  // TODO quorum reached checking
}
