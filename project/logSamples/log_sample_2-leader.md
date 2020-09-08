This is the log output for node #0 (the leader) in a three-node cluster, filtered to only show logs from the 
replication layer or Raft. Server logs will show Raft's replication of log entries to its followers
upon their joining, along with their catchup for previous log entries (especially in follower #2).

Annotations have been interspersed within the raw log output to summarize what certain sections code are doing

Akka dispatcher info has been replaced with `[...]` 

```
> kubectl logs cdb-0 -n chordial-ns -f | grep -i replication

|| Server is started, and no other nodes are in the cluster. It elects itself as leader, and can start taking
|| client commands
19:54:58.350 [...] INFO ChordialServer$ - Initializing raft and replication layer components
19:54:58.367 [...] INFO ChordialServer$ - Replication layer components created
19:54:58.482 [...] DEBUG replication.RaftFSM - Raft role FSM has been initialized
19:54:58.498 [...] DEBUG replication.RaftFSM - Resetting election timer
19:54:58.500 [...] INFO replication.RaftFSM - Randomized Raft election timeout started as no external seed node was detected
19:54:59.319 [...] INFO replication.Raft$ - Raft API service has been initialized
19:54:59.324 [...] INFO replication.ReplicationComponent - Replication component subscribed to incoming join events from administration module
19:54:59.324 [...] INFO replication.ReplicationComponent - Replication component initialized
19:54:59.357 [...] INFO replication.RaftGRPCService$ - Raft service bound to /0.0.0.0:22203
19:55:00.427 [...] INFO replication.RaftFSM - Starting leader election for new term: 1
19:55:00.429 [...] DEBUG replication.RaftFSM - Resetting election timer
19:55:02.651 [...] INFO replication.RaftFSM - Election won, becoming leader of term 1

|| Suppose now that follower #1 announces it wants to join the cluster.
||
|| Upon receiving this join command, Raft will immediately write it to the WAL. Since no other node is currently
|| in the cluster, it immediately succeeds. The first follower is then officially invited to the cluster, and will
|| start receiving Raft messages from future leaders 
||
|| Note that the administration module receives join messages via gossip, and reports it to Raft.
|| Since the gossip protocol may receive multiple of the same message, Raft will reject any messages if it detects it's 
|| a duplicate.
19:55:35.023 [...] DEBUG replication.ReplicationComponent - Join replica group request received for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.027 [...] INFO replication.roles.Leader$ - Client operation received to add node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 to the cluster
19:55:35.097 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appending log entry #1 at offset 0 and byte length 81 to WAL
19:55:35.103 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.107 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:55:35.112 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.119 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.119 [...] INFO replication.RaftFSM - Committing node add entry, node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 officially invited to cluster

|| Once the follower joins the cluster, it begins receiving log append entry commands from the leader. At this point,
|| the leader has one log entry - the cluster join event. The leader will then proceed to catch up the new node through
|| the normal log entry replication mechanism. 
19:55:35.911 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:35.912 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:55:35.920 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:35.962 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:35.967 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:36.213 [...] DEBUG replication.ReplicationComponent - Join replica group request received for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:36.216 [...] INFO replication.roles.Leader$ - Rejecting new server add: node is already a member
19:55:36.492 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:36.494 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.261 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.263 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:37.264 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:37.291 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:37.291 [...] DEBUG replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date

|| Once the log entry is replicated on the follower, it detects that the follower is fully up-to-date and doesn't
|| replicate anything further. The cluster becomes stable, with the leader sending heartbeats to the follower to prevent
|| the follower from starting an election.
19:55:37.292 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.060 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.064 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:38.064 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.090 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:38.090 [...] DEBUG replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:55:38.090 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.860 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.862 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:55:38.863 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:38.897 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:55:38.898 [...] DEBUG replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:55:38.901 [...] ...
19:55:39.660 [...] ...
19:55:39.663 [...] ...


|| Now comes follower #2, wanting to join the cluster as well.
|| 
|| Much like the first follower, it essentially follows the example same process except...
19:56:20.670 [...] DEBUG replication.ReplicationComponent - Join replica group request received for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:20.671 [...] INFO replication.roles.Leader$ - Client operation received to add node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f to the cluster
19:56:20.672 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appending log entry #2 at offset 81 and byte length 81 to WAL
19:56:20.675 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000

|| ...it now has to get approval from follower #1. Here, we see that the follower replicates the entry and approves of 
|| the new node joining the cluster, and the leader can proceed. 
19:56:20.677 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:20.680 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:20.696 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true

|| The leader determines that a majority of the cluster has replicated the entry, and now commits it. Follower #2 is 
|| officially invitied to the cluster.
19:56:20.698 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:20.699 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #2 at offset 81 and byte length 81 from WAL
19:56:20.706 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:20.707 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:20.707 [...] INFO replication.RaftFSM - Committing node add entry, node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f officially invited to cluster

|| Immediately, the leader has to catch up the new follower, so it sends over both of its log entries in successive
|| commands. Here, it's replicating log entry #1:
||
|| (Messages about follower #1 have been omitted in this section for brevity since it's in a stable state)
19:56:21.469 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:21.470 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:56:21.474 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:56:21.476 [...] DEBUG replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:21.478 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:21.612 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true

|| ...and replicating the log entry #2:
19:56:21.613 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.380 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.380 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #2 at offset 81 and byte length 81 from WAL
19:56:22.383 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:22.385 [...] DEBUG replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:22.385 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.419 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true
19:56:22.420 [...] DEBUG replication.roles.Leader$ - Follower df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f is fully up to date


|| From here onwards, the cluster reaches a stable state again and will continuously perform heartbeat checks on the
|| two followers for all eternity
19:56:23.212 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:23.810 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.812 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:23.813 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.825 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:56:23.826 [...] DEBUG replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:56:23.826 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:23.981 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:23.983 [...] DEBUG replication.RaftFSM - Message sent to member df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with IP address 10.1.0.202
19:56:23.984 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.002 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f with status: true
19:56:24.003 [...] DEBUG replication.roles.Leader$ - Follower df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f is fully up to date
19:56:24.003 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.590 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.592 [...] DEBUG replication.RaftFSM - Message sent to member a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with IP address 10.1.0.201
19:56:24.592 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.604 [...] DEBUG replication.roles.Leader$ - Append entry reply received from node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 with status: true
19:56:24.604 [...] DEBUG replication.roles.Leader$ - Follower a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23 is fully up to date
19:56:24.604 [...] DEBUG replication.RaftFSM - Resetting individual node heartbeat timer: a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:24.770 [...] INFO replication.roles.Leader$ - Individual leader timeout reached for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:24.772 [...] ...
19:56:24.772 [...] ...
19:56:24.789 [...] ...
```  