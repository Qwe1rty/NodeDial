package replication.cluster

import membership.MembershipTable


trait RaftCluster {

  val raftMembership: MembershipTable = MembershipTable()

  // TODO quorum reached checking
}
