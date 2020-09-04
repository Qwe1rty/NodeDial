## Project Development Plan

This is a loose outline of all the core features that should be included, and the general order
of implementation. _Italics indicate that this component is in progress!_

There also may be some elements that are left unticked, which means that the functionality is not 
strictly essential and is skipped for now to allow the establishment the high-level architecture first

- [x] **Milestone 0: Repo and Build Setup**
  
- [x] **Milestone 1: Persistence Layer**
  - [x] External service setup via gRPC
  - [x] Establish persistence layer
    - [x] Key isolation
      - [x] Serial execution for single keys 
      - [x] Thread partitioning
    - [ ] Key durability via write-ahead strategy
    - [x] ~~Non-blocking async disk I/O~~ Thread-pool backed I/O
  - [x] Logging that should work in Akka actor contexts and non-actor contexts
  - [x] Multi-subproject setup for common components
  - [x] Basic testing of core functionality
  
- [x] **Milestone 2: Cluster Administration**
  - [x] Membership table of other nodes' IPs and liveness states
  - [x] Node state tracking and broadcasting, following the SWIM protocol
    - [x] Cluster joins/leaves
    - [x] Suspicion/death refutation
    - [ ] _Cluster rejoins and recovery, including dynamic IP recognition_
  - [x] Gossip component
    - [x] Push mechanism for join/leave broadcasting
    - [ ] Pull mechanism for anti-entropy
  - [x] Failure detection through direct + indirect check mechanism
  - [x] Local kubernetes cluster setup and integration
    - [x] Service containerization  
    
- [ ] **Milestone 3: Replication Layer**
  - [ ] *Raft implementation*
    - [x] Leader election
      - [x] Follower/candidate/leader state persistence handling
      - [x] Voting and election mechanics: RPCs and logic
    - [ ] *Log replication*
      - [ ] *Majority commit: includes `AppendEntry` handling and disk persistence* 
      - [ ] *Log recovery and replica log backtracking*
    - [ ] Raft membership/configuration changes
    - [ ] Log compaction (if there's time) 
  - [ ] Consistency/node failure testing
  - [ ] Code debt cleanup
    
- [ ] **Milestone 4: Partitioning Layer**
  - [ ] Partitioning via virtual nodes
    - [ ] _Partition ring data structure_
    - [ ] Dynamic repartition dividing/merges on node join/failure
    - [ ] Data shuffling on node membership changes
  - [ ] Better testing, should be able to do some failure case handling
  
- [ ] **Milestone 5: Transaction Layer**
  - [ ] Distributed transactions (2PC?)
  - [ ] _TODO_