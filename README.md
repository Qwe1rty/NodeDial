
# Chordial

A distributed, scalable key-value database system! Modeled around existing NoSQL databases such 
as Redis, Cassandra, and Dynamo, it is built with horizontal scalability and cloud deployments in
mind - check out the [build walkthrough](#project-setup-and-walkthrough) and deployment guide to get
started!

**Project development is currently ongoing! Check out the [project plan](#project-development-plan)
for a development overview**


---
## Project Setup and Walkthrough

#### Dependency Installation

First, the project build requires that you have some prerequisites installed on your system.

The project itself will require both a Scala and protobuf compiler. The build commands will also
include infrastructure setup, with Docker and Kubernetes providing the 

Here are some reference links that may be helpful for installing dependencies: 

* Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>
* Install protobuf: <https://github.com/protocolbuffers/protobuf>
* Install Docker: <https://nickjanetakis.com/blog/setting-up-docker-for-windows-and-wsl-to-work-flawlessly> (Windows/WSL specific link)
* Install Kubernetes: <https://itnext.io/setting-up-the-kubernetes-tooling-on-windows-10-wsl-d852ddc6699c> (Windows/WSL specific link)

#### Compilation and Local Server Setup

Afterwards, run `make all` at project root, which will build the fat JARs and create the server
docker image on your local environment. Then, run `make run-server` to start it up

When you `Ctrl-C` the terminal that the server was started on, it detach the terminal from
the docker log output but will not shut down the server. To shut it down, run  the `make kill-server`
command

To reattach the terminal to the server instance, run `make log-server`

#### Using the CLI Tool

When building the server instance, you'll also build the CLI tool that allows you to conveniently 
make requests to your server

Once the project is successfully built and the server is running, run the `make install-client`
command to install the client `JAR` and wrapper script into to your `$PATH` space. 
**Note that this will require `sudo` privileges, as it is copying them to `/var/lib/` and `/usr/local/bin`
 respectively**  
 
Now, you should be able to just run the `chordial` command from anywhere. Test your installation by 
running `chordial --help`, which should print out this lovely menu:

```
This Chordial client program is a CLI tool to interact with the database node instances
For more information, check out: https://github.com/Qwe1rty/Chordial

Usage: chordial [get|post|delete|ready] [options]

  -k, --key <value>      key for an entry in the database
  -v, --value <value>    value associated with a key
  -t, --timeout <value>  timeout for the resulting gRPC call made to the server. If omitted, it will be set to 10 seconds
  -h, --host <value>     hostname to target. If omitted, the address 0.0.0.0 will be used
  --help                 prints this usage text


Command: get
Get a value from the database

Command: post
Insert a value into the database. If present, will overwrite existing value for the specified key

Command: delete
Delete a value from the database

Command: ready
Perform a readiness check - readiness indicates the node is ready to receive requests
```

Before sending read or write requests, you will need to wait until the database has fully
initialized first. You can check readiness through the `chordial ready` command

Once the server is ready, you can start hitting it with read/write requests 

**SECTION IN PROGRESS**

#### Build Setup Notes


For further information about various aspects of how the project build system works, here are some various
resources that help elaborate on certain build topics used in this project:

* gRPC and ScalaPB
  * Importing Google common protobuf files: <https://github.com/googleapis/common-protos-java>
  * Additional fix regarding above link for SBT build: <https://discuss.lightbend.com/t/use-googles-annotations-proto/3302>
* SBT Multi-project
  * Example `build.sbt`: <https://github.com/pbassiner/sbt-multi-project-example/blob/master/build.sbt>
  * SBT assembly + Docker: <https://hackernoon.com/akka-io-sbt-assembly-and-docker-a88b649f63cf>
* Dockerizing Scala apps
  * Walkthrough: https://blog.elegantmonkeys.com/dockerizing-your-scala-application-6590385fd501
* Java downgrading: <https://askubuntu.com/questions/1133216/downgrading-java-11-to-java-8>
* Kubernetes Cluster Setup:
  * Example StatefulSet deployment (ZooKeeper): <https://kubernetes.io/docs/tasks/run-application/scale-stateful-set/>
  * Example StatefulSet deployment (Cassandra): <https://kubernetes.io/docs/tutorials/stateful-application/cassandra/>
  * Cassandra deployment walkthrough/breakdown: <https://convit.de/blog/blog-cassandra-kubernetes-it-good-fit-convit.html>


---

## Kubernetes Cluster Setup

**Disclaimer: This has currently only been tested using version 1.14.x of the Kubernetes server. Please 
be on the lookout for potential issues if using other versions of Kubernetes**

If everything seems to work okay, you can now set up a Kubernetes cluster! Note that this section may
skip over details about setting up non-Chordial related Kubernetes components (such as the DNS 
service), so some familiarity with Kubernetes would be really helpful

The rest of this section assumes you are using the provided configuration files in the `kube` directory,
and are just running the Kubernetes cluster on your local machine. 

#### Single-Node Cluster Setup

Firstly, before you can run the Chordial service, you will need to already have a prerequisite cluster up
and running with some DNS service - this is required especially when scaling up the cluster, as new
nodes will need to resolve the seed node's hostname

(A DNS service is actually not strictly necessary, but it can help to automate and simplify cluster 
scaling. This topic will be further discussed in the scaling subchapter)



#### Cluster Scaling

#### Cloud Environment Provisioning

**SECTION IN CONSTRUCTION. PLEASE COME BACK LATER!**


---
## Project Development Plan

This is a loose outline of all the core features that should be included, and the general order
of implementation. _Italics indicate that this component is in progress!_

- [x] **Milestone 0: Repo and Build Setup**
  
- [x] **Milestone 1: Persistence Layer**
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
  - [x] _Membership table of other nodes' IPs and liveness states_
  - [ ] _Node state tracking and broadcasting, following the SWIM protocol_
    - [x] Cluster joins/leaves
    - [x] Suspicion/death refutation
    - [ ] _Cluster rejoins and recovery, including dynamic IP recognition_
  - [ ] _Gossip component_
    - [x] Push mechanism for join/leave broadcasting
    - [ ] Pull mechanism for anti-entropy
  - [x] Failure detection through direct + indirect check mechanism
  - [ ] _Local kubernetes cluster setup and integration_
    - [x] Service containerization  
    
- [ ] **Milestone 3: Partitioning Layer**
  - [ ] Partitioning via virtual nodes
    - [ ] Partition ring data structure
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

