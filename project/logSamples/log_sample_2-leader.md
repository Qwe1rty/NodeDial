This is the log output for node #0 (the leader) in a three-node cluster, filtered to only show logs from the 
replication layer or Raft. Server logs will show Raft's replication of log entries to its followers
upon their joining, along with their catchup for previous log entries (especially in follower #2).

Annotations have been interspersed within the raw log output to summarize what certain sections code are doing

Akka dispatcher info, and `DEBUG/INFO` log level information has been omitted for brevity


```
> kubectl logs ndb-0 -n nodedial-ns -f | grep -i replication

| Server is started, and no other nodes are in the cluster. It elects itself as leader, and can start taking
| client commands
|
19:54:58.350 NodeDialServer$ - Initializing raft and replication layer components
19:54:58.367 NodeDialServer$ - Replication layer components created
19:54:58.482 replication.RaftFSM - Raft role FSM has been initialized
19:54:58.498 replication.RaftFSM - Resetting election timer
19:54:58.500 replication.RaftFSM - Randomized Raft election timeout started as no external seed node was detected
19:54:59.319 replication.Raft$ - Raft API service has been initialized
19:54:59.324 replication.ReplicationComponent - Replication component subscribed to incoming join events from administration module
19:54:59.324 replication.ReplicationComponent - Replication component initialized
19:54:59.357 replication.RaftGRPCService$ - Raft service bound to /0.0.0.0:22203
19:55:00.427 replication.RaftFSM - Starting leader election for new term: 1
19:55:00.429 replication.RaftFSM - Resetting election timer
19:55:02.651 replication.RaftFSM - Election won, becoming leader of term 1

| Suppose now that follower #1 announces it wants to join the cluster.
|
| Upon receiving this join command, Raft will immediately write it to the WAL. Since no other node is currently
| in the cluster, it immediately succeeds. The first follower is then officially invited to the cluster, and will
| start receiving Raft messages from future leaders 
|
| Note that the administration module receives join messages via gossip, and reports it to Raft.
| Since the gossip protocol may receive multiple of the same message, Raft will reject any messages if it detects it's 
| a duplicate.
|
19:55:35.023 replication.ReplicationComponent - Join replica group request received for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.027 replication.roles.Leader$ - Client operation received to add node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 to the cluster
19:55:35.097 replication.eventlog.SimpleReplicatedLog$ - Appending log entry #1 at offset 0 and byte length 81 to WAL
19:55:35.103 replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.107 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:55:35.112 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.119 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.119 replication.RaftFSM - Committing node add entry, node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 officially invited to cluster

| Once the follower joins the cluster, it begins receiving log append entry commands from the leader. At this point,
| the leader has one log entry - the cluster join event. The leader will then proceed to catch up the new node through
| the normal log entry replication mechanism.
| 
19:55:35.911 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.912 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:55:35.920 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.962 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:35.967 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:36.213 replication.ReplicationComponent - Join replica group request received for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:36.216 replication.roles.Leader$ - Rejecting new server add: node is already a member
19:55:36.492 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:36.494 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.261 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.263 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:37.264 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.291 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:37.291 replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date

| Once the log entry is replicated on the follower, it detects that the follower is fully up-to-date and doesn't
| replicate anything further. The cluster becomes stable, with the leader sending heartbeats to the follower to prevent
| the follower from starting an election.
|
19:55:37.292 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.060 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.064 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:38.064 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.090 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:38.090 replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:55:38.090 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.860 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.862 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:38.863 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.897 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:38.898 replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:55:38.901 ...
19:55:39.660 ...
19:55:39.663 ...


| Now comes follower #2, wanting to join the cluster as well.
| 
| Much like the first follower, it essentially follows the example same process except...
|
19:56:20.670 replication.ReplicationComponent - Join replica group request received for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:20.671 replication.roles.Leader$ - Client operation received to add node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f to the cluster
19:56:20.672 replication.eventlog.SimpleReplicatedLog$ - Appending log entry #2 at offset 81 and byte length 81 to WAL
19:56:20.675 replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000

| ...it now has to get approval from follower #1 to form a majority . Here, we see that the follower replicates the 
| entry and approves of the new node joining the cluster, and the leader can proceed.
| 
19:56:20.677 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:20.680 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:20.696 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true

| The leader determines that a majority of the cluster has replicated the entry, and now commits it. Follower #2 is 
| officially invitied to the cluster.
|
19:56:20.698 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:20.699 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #2 at offset 81 and byte length 81 from WAL
19:56:20.706 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:20.707 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:20.707 replication.RaftFSM - Committing node add entry, node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f officially invited to cluster

| Immediately, the leader has to catch up the new follower, so it sends over both of its log entries in successive
| commands. Here, it's replicating log entry #1:
|
| (Messages about follower #1 have been omitted in this section for brevity since it's in a stable state)
|
19:56:21.469 replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:21.470 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:56:21.474 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:56:21.476 replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:21.478 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:21.612 replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true

| ...and replicating the log entry #2:
|
19:56:21.613 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.380 replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.380 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #2 at offset 81 and byte length 81 from WAL
19:56:22.383 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:22.385 replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:22.385 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.419 replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true
19:56:22.420 replication.roles.Leader$ - Follower df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f is fully up to date


| From here onwards, the cluster reaches a stable state again and will continuously perform heartbeat checks on the
| two followers for all eternity
|
19:56:23.212 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:23.810 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.812 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:23.813 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.825 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:56:23.826 replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:56:23.826 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.981 replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:23.983 replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:23.984 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.002 replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true
19:56:24.003 replication.roles.Leader$ - Follower df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f is fully up to date
19:56:24.003 replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.590 replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.592 replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:24.592 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.604 replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:56:24.604 replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:56:24.604 replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.770 replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.772 ...
19:56:24.772 ...
19:56:24.789 ...
```  