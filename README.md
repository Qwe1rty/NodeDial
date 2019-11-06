
# Chordial

## **Currently a work in progress!!!**

An attempt at making a distributed system


---
## Project Plan

- [x] **Part 0**
  - [x] Repo and build setup
- [ ] **Part 1**
  - [x] External service setup via gRPC
  - [ ] Establish persistence layer, should support locally atomic/isolated operations
    - [ ] Key isolation
      - [ ] Serial execution for single keys 
      - [ ] Thread partitioning
    - [ ] Key atomicity
      - [ ] Log recovery via write-ahead strategy
      - [ ] Rollback to previously commited value
    - [ ] Non-blocking async disk I/O
  - [ ] Logging that should work in Akka actor contexts and non-actor contexts
  - [ ] Basic test setup and containerization
- [ ] **Part 2**
  - [ ] Membership and discovery
    - [ ] Internal actor remote communication
    - [ ] Gossip-based discovery
    - [ ] Finger table initialization
    - [ ] Local kubernetes cluster setup
  - [ ] Partitioning via consistent hashing (following chord algo)
  - [ ] Better testing, should be able to do some failure case handling
- [ ] **Part 3**
  - [ ] Replication scheme, quorum handling
  - [ ] Anti-entropy process (anti-entropy or read repair or ideally both)
  - [ ] Cluster-wide concurrent write handling, vector versioning
  - [ ] Consistency/node failure testing
- [ ] **Part 4**
  - [ ] Distributed transactions (2PC)
  - [ ] _TODO_

---
## Project Setup

Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>

Install protobuf: <https://github.com/protocolbuffers/protobuf>

Run `sbt run` at project root

#### Build Setup Notes

Links regarding Akka-gRPC and SBT setup:
* Importing Google common protobuf files: <https://github.com/googleapis/common-protos-java>
* Additional fix regarding above link for SBT build: <https://discuss.lightbend.com/t/use-googles-annotations-proto/3302>
* Java downgrading: <https://askubuntu.com/questions/1133216/downgrading-java-11-to-java-8>