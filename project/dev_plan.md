## Project Development Plan

This is a loose outline of all the features that may be included, and the general order
of implementation. Some non-critical elements might be skipped over and returned to later if
there isn't enough time to do them at first.

_Italics indicate that this component is in progress!_

- [x] **Milestone 0: Repo and Build Setup**
  
- [x] **Milestone 1: Persistence Layer**
  - [x] External service setup via gRPC
  - [x] Establish persistence layer
    - [x] Key isolation
      - [x] Serial execution for single keys 
      - [x] Thread partitioning
    - [x] ~~Non-blocking async disk I/O~~ Thread-pool backed I/O
  - [x] Logging that should work in Akka actor contexts and non-actor contexts
  - [x] Multi-subproject setup for common components
  - [x] Basic testing of core functionality
  
- [x] **Milestone 2: Cluster Administration**
  - [x] Membership table of other nodes' IPs and liveness states
  - [x] Node state tracking and broadcasting, following the SWIM protocol
    - [x] Cluster joins/leaves
    - [x] Suspicion/death refutation
    - [ ] Cluster rejoins and recovery, including dynamic IP recognition
  - [x] Gossip component
    - [x] Push mechanism for join/leave broadcasting
    - [ ] Pull mechanism for anti-entropy
  - [x] Local kubernetes cluster setup and integration
    - [x] Service containerization  
  - [x] Failure detection through direct + indirect check mechanism
    
- [x] **Milestone 3: Replication Layer**
  - [x] *Raft implementation*
    - [x] Leader election
      - [x] Follower/candidate/leader state persistence handling
      - [x] Voting and election mechanics: RPCs and logic
    - [x] Log replication
      - [x] Majority commit: includes `AppendEntry` handling and disk persistence 
      - [x] Log recovery and replica log backtracking
    - [x] Raft membership/configuration changes
    - [ ] Log compaction (if there's time) 
  - [x] Consistency/node failure testing
  - [x] A good amount of code debt cleanup and library updates (particularly Akka 2.6.x)

I'm not sure I want to continue the project past this point, so there's no guarantee that
these will ever be completed
    
- [ ] **Milestone 4: Partitioning Layer**
  - [ ] Partitioning via virtual nodes
    - [ ] _Partition ring data structure_
    - [ ] Dynamic repartition dividing/merges on node join/failure
    - [ ] Data shuffling on node membership changes
  - [ ] Better testing, should be able to do more advanced failure case handling
  
- [ ] **Milestone 5: Transaction Layer**
  - [ ] Distributed transactions (2PC?)
  - [ ] _TODO_