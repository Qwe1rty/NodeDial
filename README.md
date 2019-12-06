
# Chordial

## **Currently a work in progress!!!**

An attempt at making a distributed system


---
## Project Plan

- [x] **Milestone 0: Setup**
  - [x] Repo and build setup
- [ ] **Milestone 1: Persistence Layer**
  - [x] External service setup via gRPC
  - [ ] Establish persistence layer, should support locally atomic/isolated operations
    - [x] Key isolation
      - [x] Serial execution for single keys 
      - [x] Thread partitioning
    - [ ] Key atomicity/durability via write-ahead strategy
    - [x] ~~Non-blocking async disk I/O~~ Thread-pool backed I/O
  - [x] Logging that should work in Akka actor contexts and non-actor contexts
  - [x] Multi-subproject setup for common components
  - [x] Basic testing of core functionality
  - [ ] Service containerization
- [ ] **Milestone 2: Cluster Membership**
  - [ ] Membership list of other nodes' IPs  
  - [ ] Joining/Failure detection
    - [ ] Initial direct check
    - [ ] Followup indirect checks after first missing ACK
  - [ ] Gossip component
    - [ ] Push mechanism
    - [ ] Pull mechanism
    - [ ] Support/API for arbitrary gRPC services
  - [ ] Local kubernetes cluster setup and integration
- [ ] **Milestone 3: Partitioning Layer**
  - [ ] Partitioning via consistent hashing (following chord algo)
    - [ ] Finger table initialization
    - [ ] Dynamic partition dividing/merges on node join/failure
  - [ ] Better testing, should be able to do some failure case handling
- [ ] **Milestone 4: Replication Layer**
  - [ ] Replication scheme, quorum handling
  - [ ] Anti-entropy process (anti-entropy or read repair or ideally both)
  - [ ] Cluster-wide concurrent write handling, vector versioning
  - [ ] Consistency/node failure testing
- [ ] **Milestone 5: Transaction Layer**
  - [ ] Distributed transactions (2PC)
  - [ ] _TODO_

---
## Project Setup

Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>

Install protobuf: <https://github.com/protocolbuffers/protobuf>

Run `sbt run` at project root

#### Build Setup Notes

Links regarding SBT setup:
* gRPC and ScalaPB
  * Importing Google common protobuf files: <https://github.com/googleapis/common-protos-java>
  * Additional fix regarding above link for SBT build: <https://discuss.lightbend.com/t/use-googles-annotations-proto/3302>
* SBT Multi-project
  * Example `build.sbt`: <https://github.com/pbassiner/sbt-multi-project-example/blob/master/build.sbt>
  * SBT assembly + Docker: <https://hackernoon.com/akka-io-sbt-assembly-and-docker-a88b649f63cf>
* Java downgrading: <https://askubuntu.com/questions/1133216/downgrading-java-11-to-java-8>