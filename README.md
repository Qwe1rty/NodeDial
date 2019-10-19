# Chordon

**Currently a work in progress!!!**

An attempt at making a distributed system

- [ ] **Part 0**
  - [ ] Repo and build setup
- [ ] **Part 1**
  - [ ] Establish persistence layer, should support atomic write operations
  - [ ] Server setup, should accept client and internal requests
  - [ ] Partitioning schema, consistent hashing (probably chord algo)
  - [ ] Basic test setup, local cluster setup
- [ ] **Part 2**
  - [ ] Replication scheme, quorum handling
  - [ ] Anti-entropy process (anti-entropy or read repair or ideally both)
  - [ ] Concurrent write handling, vector versioning
  - [ ] Better testing, more failure case handling
- [ ] **Part 3**
  - [ ] Distributed transactions (probably using 3rd party Raft implementations)
  - [ ] _TODO_
