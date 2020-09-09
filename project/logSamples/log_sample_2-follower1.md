This is the log output for node #1 (follower #1) in a three-node cluster, filtered to only show logs from the 
replication layer or Raft. Server logs will show Raft's replication of log entries to its followers
upon their joining, along with their catchup for previous log entries (especially in follower #2).

Annotations have been interspersed within the raw log output to summarize what certain sections code are doing

Akka dispatcher info, and `DEBUG/INFO` log level information has been omitted for brevity


```
> kubectl logs ndb-1 -n nodedial-ns -f | grep -i replication

| Server is started, but since it is given a seed node (which is the current leader) on startup, it knows that it's
| joining an existing cluster. Thus, it doesn't try to start an election, and just waits for the leader to approve
| of this node's join.
|
19:55:33.328 NodeDialServer$ - Initializing raft and replication layer components
19:55:33.332 NodeDialServer$ - Replication layer components created
19:55:33.466 replication.RaftFSM - Raft role FSM has been initialized
19:55:34.279 replication.Raft$ - Raft API service has been initialized
19:55:34.284 replication.ReplicationComponent - Replication component subscribed to incoming join events from administration module
19:55:34.284 replication.ReplicationComponent - Replication component initialized
19:55:34.316 replication.RaftGRPCService$ - Raft service bound to /0.0.0.0:22203

| Behind the scenes, the leader is busy trying to get approval for the new node join. However, from the joining node's
| perspective, nothing seems to happen until the server swoops in and tells it that it's now in the cluster and
| immediately needs to start catching up its WAL to the other nodes.
|
| The first message the follower receives is a command to replicate the first log entry, which is the entry about this
| node joining the cluster.
|
| The write succeeds, and the follower sends back confirmation to the leader.
|
19:55:36.315 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 0, prevLogIndex = 0)
19:55:36.324 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:55:36.452 replication.eventlog.SimpleReplicatedLog$ - Appending log entry #1 at offset 0 and byte length 81 to WAL
19:55:36.461 replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:36.463 replication.roles.Follower$ - Append entry request accepted for entry at log index 1
19:55:36.464 replication.roles.Follower$ - Cluster node add received from leader for node a88b80dc226df57b0d8df8f272b22b0a4302f766d651fb9c9a4e8413f1ae9e23
19:55:36.466 replication.roles.Follower$ - Commit index updated to 1
19:55:36.467 replication.roles.Follower$ - Append entry request reply with success status

| Afterwards, the follower reaches a stable state, where it receives a steady stream of heartbeats from the leader.
| This confirms the leader's liveness, resetting its election timer on message receive, meaning it doesn't need to go
| and start a new election.
|
19:55:36.477 replication.RaftFSM - Resetting election timer
19:55:36.477 replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 81 from WAL
19:55:36.493 replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 124F0800124B0A406138386238306463323236646635376230643864663866323732623232623061343330326637363664363531666239633961346538343133663161653965323311C900010A00000000
19:55:37.278 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:55:37.279 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:55:37.279 replication.roles.Follower$ - Append entry request reply with success status
19:55:37.279 replication.RaftFSM - Resetting election timer
19:55:38.080 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:55:38.081 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:55:38.081 replication.roles.Follower$ - Append entry request reply with success status
19:55:38.083 replication.RaftFSM - Resetting election timer
19:55:38.878 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:55:38.878 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:55:38.879 replication.roles.Follower$ - Append entry request reply with success status
19:55:38.879 replication.RaftFSM - Resetting election timer
19:55:39.684 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:55:39.684 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:55:39.685 replication.roles.Follower$ - Append entry request reply with success status
19:55:39.687 replication.RaftFSM - Resetting election timer
19:55:40.484 ...
19:55:40.484 ...
19:55:40.484 ...


| Later on, follower #2 joins the party. From this node's perspective, it's just like any other log entry. It appends
| the entry to its WAL, sends approval to the leader...
|
19:56:20.688 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 1)
19:56:20.688 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:20.689 replication.eventlog.SimpleReplicatedLog$ - Appending log entry #2 at offset 81 and byte length 81 to WAL
19:56:20.692 replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 124F0800124B0A406466356139386365343235333063366562666662623532643030363361316437373632613566383530323461653738663630376666653262396466373831376611CA00010A00000000
19:56:20.692 replication.roles.Follower$ - Append entry request accepted for entry at log index 2
19:56:20.692 replication.roles.Follower$ - Cluster node add received from leader for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:20.693 replication.roles.Follower$ - Append entry request reply with success status
19:56:20.696 replication.RaftFSM - Resetting election timer
19:56:21.196 replication.ReplicationComponent - Join replica group request received for node df5a98ce42530c6ebffbb52d0063a1d7762a5f85024ae78f607ffe2b9df7817f
19:56:21.479 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:21.479 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:21.479 replication.roles.Follower$ - Commit index updated to 2
19:56:21.479 replication.roles.Follower$ - Append entry request reply with success status
19:56:21.481 replication.RaftFSM - Resetting election timer

| ...and returns to a stable state.
|
19:56:22.264 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:22.265 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:22.265 replication.roles.Follower$ - Append entry request reply with success status
19:56:22.265 replication.RaftFSM - Resetting election timer
19:56:23.044 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:23.044 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:23.044 replication.roles.Follower$ - Append entry request reply with success status
19:56:23.045 replication.RaftFSM - Resetting election timer
19:56:23.821 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:23.821 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:23.821 replication.roles.Follower$ - Append entry request reply with success status
19:56:23.822 replication.RaftFSM - Resetting election timer
19:56:24.601 replication.RaftGRPCService$ - Append entries request from leader bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with latest log entry: (prevLogTerm = 1, prevLogIndex = 2)
19:56:24.601 replication.roles.Follower$ - Append entry request received from node bccc22417f4719d3bec769d12abda315bdae7b4fbe929740207e830253e84fde with term 1
19:56:24.601 replication.roles.Follower$ - Append entry request reply with success status
19:56:24.602 ...
19:56:25.380 ...
19:56:25.381 ...
```  