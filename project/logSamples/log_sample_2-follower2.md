This is the log output for node #2 (follower #2) in a three-node cluster, filtered to only show logs from the 
replication layer or Raft. Server logs will show Raft's replication of log entries to its followers
upon their joining, along with their catchup for previous log entries (especially in follower #2).

Annotations have been interspersed within the raw log output to summarize what certain sections code are doing

Akka dispatcher info has been replaced with `[...]` 


```
> kubectl logs cdb-2 -n chordial-ns -f | grep -i replication

|| Much like follower #1, since it is given a seed node (which is the current leader) on startup, it knows that it's
|| joining an existing cluster. Thus, it doesn't try to start an election, and just waits for the leader to approve
|| of this node's join.
19:56:18.621 [...] INFO ChordialServer$ - Initializing raft and replication layer components
19:56:18.640 [...] INFO ChordialServer$ - Replication layer components created
19:56:18.744 [...] DEBUG replication.RaftFSM - Raft role FSM has been initialized
19:56:19.531 [...] INFO replication.Raft$ - Raft API service has been initialized
19:56:19.534 [...] INFO replication.ReplicationComponent - Replication component subscribed to incoming join events from administration module
19:56:19.535 [...] INFO replication.ReplicationComponent - Replication component initialized
19:56:19.596 [...] INFO replication.RaftGRPCService$ - Raft service bound to /0.0.0.0:22203

|| Like the first follower, it gets messaged out of the blue by the leader, saying that it's now part of the cluster
|| and that it needs to catch up its WAL with the rest of the cluster.
||
|| First, it applies log entry #1, which is the entry about follower #1 joining the cluster: 
19:56:21.552 [...] DEBUG replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 0, prevLogIndex = 0)
19:56:21.554 [...] INFO replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:21.598 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appending log entry #1 at offset 0 and byte length 81 to WAL
19:56:21.603 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:56:21.603 [...] DEBUG replication.roles.Follower$ - Append entry request accepted for entry at log index 1
19:56:21.604 [...] INFO replication.roles.Follower$ - Cluster node add received from leader for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:56:21.605 [...] DEBUG replication.roles.Follower$ - Commit index updated to 1
19:56:21.605 [...] INFO replication.roles.Follower$ - Append entry request reply with success status
19:56:21.608 [...] DEBUG replication.RaftFSM - Resetting election timer

|| Then it applies log entry #2, which is the entry about follower #2 (which is this node) joining the cluster:
19:56:22.405 [...] DEBUG replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:56:22.406 [...] INFO replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:22.406 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appending log entry #2 at offset 81 and byte length 81 to WAL
19:56:22.411 [...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:22.411 [...] DEBUG replication.roles.Follower$ - Append entry request accepted for entry at log index 2
19:56:22.411 [...] INFO replication.roles.Follower$ - Cluster node add received from leader for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:22.412 [...] DEBUG replication.roles.Follower$ - Commit index updated to 2
19:56:22.413 [...] INFO replication.roles.Follower$ - Append entry request reply with success status
19:56:22.413 [...] DEBUG replication.RaftFSM - Resetting election timer

|| After it's caught up, it goes into a steady state, receiving a constant stream heartbeat signals from the leader
|| which continually resets its election timer and prevents it from starting a new election.
19:56:23.205 [...] DEBUG replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:23.205 [...] INFO replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:23.205 [...] INFO replication.roles.Follower$ - Append entry request reply with success status
19:56:23.208 [...] DEBUG replication.RaftFSM - Resetting election timer
19:56:23.996 [...] DEBUG replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:23.997 [...] INFO replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:23.997 [...] INFO replication.roles.Follower$ - Append entry request reply with success status
19:56:23.998 [...] DEBUG replication.RaftFSM - Resetting election timer
19:56:24.785 [...] DEBUG replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:24.786 [...] INFO replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:24.786 [...] INFO replication.roles.Follower$ - Append entry request reply with success status
19:56:24.786 [...] DEBUG replication.RaftFSM - Resetting election timer
19:56:25.567 [...] ...
19:56:25.567 [...] ...
19:56:25.568 [...] ...
```