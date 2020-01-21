
# Chordial

A distributed, scalable key-value database system! Modeled around existing NoSQL databases such 
as Redis, Cassandra, and Dynamo, it is built with horizontal scalability and cloud deployments in
mind - check out the [build walkthrough](#project-setup-and-walkthrough) and deployment guide to get
started!

**Project development is currently ongoing! Check out the [project plan](#project-development-plan)
for a development overview**


---
## Project Setup and Walkthrough

### Dependency Installation

First, the project build requires that you have some prerequisites installed on your system.

The project itself will require both a Scala and protobuf compiler. The build commands will also
include infrastructure setup, with Docker and Kubernetes providing the 

Here are some reference links that may be helpful for installing dependencies: 

* Install Scala/SBT: <https://www.techrepublic.com/article/how-to-install-sbt-on-ubuntu-for-scala-and-java-projects/>
* Install protobuf: <https://github.com/protocolbuffers/protobuf>
* Install Docker: <https://nickjanetakis.com/blog/setting-up-docker-for-windows-and-wsl-to-work-flawlessly> (Windows/WSL specific link)
* Install Kubernetes: <https://itnext.io/setting-up-the-kubernetes-tooling-on-windows-10-wsl-d852ddc6699c> (Windows/WSL specific link)

### Compilation and Local Server Setup

Afterwards, run `make all` at project root, which will build the fat JARs and create the server
docker image on your local environment. Then, run `make run-server` to start it up

When you `Ctrl-C` the terminal that the server was started on, it detach the terminal from
the docker log output but will not shut down the server. To shut it down, run  the `make kill-server`
command

To reattach the terminal to the server instance, run `make log-server`

### Using the CLI Tool

When building the server instance, you'll also build the CLI tool that allows you to conveniently 
make requests to your server

Once the project is successfully built and the server is running, run the `make install`
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
initialized first. You can check readiness through the `chordial ready` command, and will
reply with this if the server is ready:

```
Readiness response received with status: true
```

Once ready, you can start hitting it with read/write requests. Here's an example of a write followed
by a read:

```
> chordial post -k some_key -v 'Hello World!'
POST request successful: PostResponse()

> chordial get -k some_key
GET request successful: Hello World!
``` 

And if it works, then congrats! Everything is all good and running. Now you're ready to set up a 
cluster (if you'd like to)


---
## Kubernetes Cluster Setup

**Disclaimer: This has currently only been tested using version 1.14.x of the Kubernetes server. Please 
be on the lookout for potential issues when using other versions of Kubernetes**

If everything seems to work okay, you can now set up a Kubernetes cluster! Note that this section may
skip over details about setting up non-Chordial related Kubernetes components (such as the DNS 
service), so some familiarity with Kubernetes would be really helpful

The rest of this section assumes you are using the provided configuration files in the `kube` directory,
and are just running the Kubernetes cluster on your local machine. 

### Single-Node Cluster Setup

Firstly, before you can run the Chordial service, you will need to already have a prerequisite cluster up
and running with some DNS service - this is required especially when scaling up the cluster, as new
nodes will need to resolve the seed node's hostname

(A DNS service is actually not strictly necessary, but it can help to automate and simplify cluster 
scaling. This topic will be further discussed in the scaling subchapter)

When the prerequities are all good, you should first create the chordial namespace using the command:
`kubectl create namespace chordial-ns`. Everything related to Chordial has been configured to run in that
namespace

Since Chordial by its nature requires persistent storage (it's a database, after all), the canonical
Kubernetes object used will be the `StatefulSet`, along with its prerequisite `Headless Service` object

So to create the headless service, run `kubectl create -f kube/chordial-headless.yaml`, followed by the
`StatefulSet` itself: `kubectl create -f kube/chordial-statefulset.yaml`

If all goes well, you'll see three healthy objects running if you check everything in the namespace (it may
take a while for it to reach a ready state):

```
> kubectl get all -n chordial-ns
NAME        READY   STATUS    RESTARTS   AGE
pod/cdb-0   1/1     Running   0          66s

NAME          TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)                         AGE
service/chs   ClusterIP   None         <none>        22200/TCP,22201/TCP,22202/TCP   71s

NAME                   READY   AGE
statefulset.apps/cdb   1/1     66s
```

You can also check out the logs and see how it's interacting with the cluster. Tailing the pod, you'll
get this sort of log output:

```
> kubectl logs cdb-0 -n chordial-ns -f
04:43:19.931 [main] INFO ChordialServer$ - Server config loaded
04:43:19.932 [main] INFO ChordialServer$ - Initializing actor system
04:43:20.350 [Chordial-akka.actor.default-dispatcher-6] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started
04:43:20.356 [Chordial-akka.actor.default-dispatcher-6] DEBUG akka.event.EventStream - logger log1-Slf4jLogger started
04:43:20.359 [Chordial-akka.actor.default-dispatcher-6] DEBUG akka.event.EventStream - Default Loggers started
04:43:20.394 [Chordial-akka.actor.default-dispatcher-7] DEBUG akka.serialization.Serialization(akka://Chordial) - Replacing JavaSerializer with DisabledJavaSerializer, due to `akka.actor.allow-java-serialization = off`.
04:43:20.416 [main] INFO ChordialServer$ - Initializing membership module components
04:43:20.451 [main] INFO membership.MembershipActor$ - Node ID not found - generating new ID
...
04:43:21.196 [Chordial-akka.actor.default-dispatcher-10] DEBUG akka.io.TcpListener - Successfully bound to /0.0.0.0:8080
04:43:21.196 [Chordial-akka.actor.default-dispatcher-10] INFO service.RequestServiceImpl$ - gRPC request service bound to /0.0.0.0:8080
04:44:00.913 [Chordial-akka.actor.default-dispatcher-14] DEBUG akka.io.TcpListener - New connection accepted
04:44:01.097 [Chordial-akka.actor.default-dispatcher-10] DEBUG service.RequestServiceImpl$ - Readiness check received
04:44:01.099 [Chordial-akka.actor.default-dispatcher-10] DEBUG service.RequestServiceImpl$ - Readiness check response with: true
``` 

If it looks something like that, you're all set to start adding new nodes to the cluster!

### Cluster Scaling

To scale the number of replicas in the `StatefulSet`, you will need to run the command:
`kubectl scale statefulset cdb -n chordial-ns --replicas=${REPLICA_COUNT}`. This will add new pods one-by-one 
into the cluster, giving them a chance to synchronize with each other without overwhelming them

Let's try adding one by setting the replica count to 2, which creates a node labelled `cdb-1`. Upon starting
up the second node, it will attempt to contact the first node and synchronize the membership information
with it

However, this is a good time to point out that this fully automatic scaling process can only be achieved
if there is a DNS server present, as the nodes will perform a DNS lookup to retrieve the IP address of the
cluster seed node (the node `cdb-0`)

Without a DNS server, it is still possible to have future nodes be scaled automatically but it will require
you to manually specify the seed node IP address into the Kubernetes `StatefulSet` configuration. _**TODO elaborate on this more**_ 

_**Section under construction! Please come back another time**_


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
  - [x] Local kubernetes cluster setup and integration
    - [x] Service containerization  
    
- [ ] **Milestone 3: Partitioning Layer**
  - [ ] Partitioning via virtual nodes
    - [ ] _Partition ring data structure_
    - [ ] _Dynamic repartition dividing/merges on node join/failure_
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

## Additional Build Setup Notes

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

