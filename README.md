
# Chordon

**Currently a work in progress!!!**

An attempt at making a distributed system

- [ ] **Part 0**
  - [ ] Repo and build setup
- [ ] **Part 1**
  - [ ] Establish persistence layer, should support atomic write operations
  - [ ] Server setup, should accept client and internal requests
  - [ ] Membership and discovery
  - [ ] Partitioning schema, consistent hashing (probably chord algo)
  - [ ] Basic test setup, local cluster setup
- [ ] **Part 2**
  - [ ] Replication scheme, quorum handling
  - [ ] Anti-entropy process (anti-entropy or read repair or ideally both)
  - [ ] Concurrent write handling, vector versioning
  - [ ] Better testing, more failure case handling
- [ ] **Part 3**
  - [ ] Distributed transactions (probably with 3rd party consensus -> Raft)
  - [ ] _TODO_

## Project Setup

Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>