
# Chordial

## **Currently a work in progress!!!**

An attempt at making a distributed key-value database system, modeled around existing NoSQL
databases such as Redis, Cassandra, and Dynamo


---
## Project Plan

This is a loose outline of all the core features that should be included, and the order
of implementation. _Italics indicate that this component is in progress_

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
- [ ] **Milestone 2: Cluster Membership**
  - [ ] Membership list of other nodes' IPs
    - [ ] _Node state tracking and broadcasting, following the SWIM protocol_
      - [ ] _Cluster joins/rejoins/leaves_
      - [x] Suspicion/death refutation
    - [ ] _Gossip component_
      - [x] Push mechanism for join/leave broadcasting
      - [ ] Pull mechanism for anti-entropy
    - [x] Failure detection through direct + indirect check mechanism
  - [ ] Local kubernetes cluster setup and integration
    - [x] Service containerization
- [ ] **Milestone 3: Partitioning Layer**
  - [ ] Partitioning via virtual nodes
    - [ ] Partition ring structure
    - [ ] Dynamic repartition dividing/merges on node join/failure
    - [ ] Data shuffling on node membership changes
  - [ ] Better testing, should be able to do some failure case handling
- [ ] **Milestone 4: Replication Layer**
  - [ ] Replication scheme, quorum handling
  - [ ] Anti-entropy process (anti-entropy or read repair or ideally both)
  - [ ] Cluster-level concurrent write handling, vector versioning
  - [ ] Consistency/node failure testing
- [ ] **Milestone 5: Transaction Layer**
  - [ ] Distributed transactions (2PC?)
  - [ ] _TODO_


---
## Project Setup

First, the project build requires that you have some prerequisites installed on your system: 
* Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>
* Install protobuf: <https://github.com/protocolbuffers/protobuf>
* (Windows/WSL only) Reconfigure Docker: <https://nickjanetakis.com/blog/setting-up-docker-for-windows-and-wsl-to-work-flawlessly>

Afterwards, run `make all` at project root, which will build the fat JARs and locally create the server
docker image. Then, run `make run-server` to start it up

When you `Ctrl-C` the terminal that the server was started on, it detach the terminal from
the docker log output but will not shut down the server. To shut it down, run `make kill-server`

To reattach the terminal to the server instance, run `make log-server`
  

#### Build Setup Notes

Various links that help explain various aspects of the build setup used in this project:

* gRPC and ScalaPB
  * Importing Google common protobuf files: <https://github.com/googleapis/common-protos-java>
  * Additional fix regarding above link for SBT build: <https://discuss.lightbend.com/t/use-googles-annotations-proto/3302>
* SBT Multi-project
  * Example `build.sbt`: <https://github.com/pbassiner/sbt-multi-project-example/blob/master/build.sbt>
  * SBT assembly + Docker: <https://hackernoon.com/akka-io-sbt-assembly-and-docker-a88b649f63cf>
* Dockerizing Scala apps
  * Walkthrough: https://blog.elegantmonkeys.com/dockerizing-your-scala-application-6590385fd501
* Java downgrading: <https://askubuntu.com/questions/1133216/downgrading-java-11-to-java-8>