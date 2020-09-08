
# Chordial

A distributed, scalable key-value database system! 
Note that this is mainly being built for educational purposes, and is not production ready 
(so please please never use it on an actual production system)

Modeled around existing leader-follower NoSQL databases such as Redis, it's designed to be similarly horizontally
scalable and deployable on cloud platforms. For more details about setting the project up on your environment, 
check out the [build walkthrough](#project-setup-and-walkthrough) and deployment guide


The main server code is located in the directory `server/src/main/scala/`, and the program currently supports the 
three basic operations: `GET`, `POST`, and `DELETE`

### About the Project

The project started as a way to learn distributed systems concepts, practice writing asynchronous/concurrent 
applications, and gain experience with the pitfalls of writing these distributed applications.
Overall, while the project still has a significant amount of work to do, over the past year of on-and-off work I've
learned a lot and gotten much better at these things. I've found that trying to implement various abstract distributed
systems ideas into an actual program really helps solidify details that I would've missed from just reading about it
(such as the many non-obvious corner cases in the Raft consensus algorithm).

While I'm not sure how long I'll continue working on it past the replication/Raft layer, the project was really fun
and I'm sure that everything I've learned will come in handy for all future projects.


---
## Project Setup and Walkthrough

### Dependency Installation

First, the project build requires that you have some stuff installed on your system:

Compiling the project itself will require both a Scala and protobuf compiler, so you'll have to install those 
if you don't have it already. In addition, running the project itself will also require some infrastructure 
setup, which includes Docker and Kubernetes. 

Finally, ensure that you are running Java 8. This is the only version of Java I've been able to consistently get the
program to run on without absurd amounts of effort messing with the build system, due to netty IO's inclusion in
Java 9+ and resulting library/build incompatibilities.

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
running `chordial --help`, which should print out this menu:
```
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

If everything seems to work okay, you can now set up a Kubernetes cluster! Note that this section may
skip over details about setting up non-Chordial related Kubernetes components (such as the DNS 
service), so some familiarity with Kubernetes would be really helpful

The rest of this section assumes you are using the provided configuration files in the `kube` directory,
and are just running the Kubernetes cluster on your local machine. 

### Single-Node Cluster Setup

Firstly, before you can run the Chordial service, you will need to already have a prerequisite cluster up
and running with some DNS service. (A DNS service is actually not strictly necessary, but you'll otherwise 
have to manually specify the IP address of the seed node)

When the prerequities are ready, you should first create the chordial namespace using the command:
`kubectl create namespace chordial-ns`. Everything related to Chordial has been configured to run in that
namespace

Since Chordial requires persistent storage, the canonical Kubernetes object used will be the `StatefulSet`,
along with its prerequisite `Headless Service` object

To create the headless service, run `kubectl create -f kube/chordial-headless.yaml`, followed by the
`StatefulSet` itself: `kubectl create -f kube/chordial-statefulset.yaml`

If all goes well, you'll see three healthy objects running if you check everything in the namespace (it may
take a while for it to reach a ready state):
```
> kubectl get all -n chordial-ns
NAME        READY   STATUS    RESTARTS   AGE
pod/cdb-0   1/1     Running   0          58s

NAME          TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)                                   AGE
service/chs   ClusterIP   None         <none>        22200/TCP,22201/TCP,22202/TCP,22203/TCP   63s

NAME                   READY   AGE
statefulset.apps/cdb   1/1     58s
```

You can also check out the logs and see how it's interacting with the cluster. Tailing the pod, you'll
get this sort of log output:
```
> kubectl logs cdb-0 -n chordial-ns -f
[main] INFO ChordialServer$ - Server config loaded
[main] INFO ChordialServer$ - Initializing actor system
...
[ChordialServer-akka.actor.default-dispatcher-8] INFO ChordialServer$ - Initializing administration components
[ChordialServer-akka.actor.default-dispatcher-8] INFO administration.Administration$ - Administration has determined node ID: e74020db48ba67212baa73a0cc28798a5f3b407821d0ddab9383cc47d06795be, with rejoin flag: false
[ChordialServer-akka.actor.default-dispatcher-8] INFO ChordialServer$ -  components initialized
``` 

If it looks something like that, you're all set to start adding new nodes to the cluster

### Cluster Scaling

To scale the number of replicas in the `StatefulSet`, you will need to run the command:
`kubectl scale statefulset cdb -n chordial-ns --replicas=${REPLICA_COUNT}`. This will add new pods one-by-one 
into the cluster, giving them a chance to synchronize with each other without overwhelming them

Let's try adding one by setting the replica count to 2, which creates a node labelled `cdb-1`. Upon starting
up the second node, it will attempt to contact the first node and synchronize the membership information
with it. 

To better illustrate the joining process, a sample log output of the new node would look like this:
```
[...] INFO administration.Administration$ - Retrieved seed node environment variable with value: 'cdb-0.chs.chordial-ns.svc.cluster.local'
[...] INFO administration.Administration$ - Seed node IP address resolved to: 10.1.0.171
[...] INFO administration.Administration - Contacting seed node for membership listing
[...] INFO administration.Administration - Successful full sync response received from seed node
```

What's occurring here is that the new node will try to first resolve the seed node's hostname, and then
contact it to request a complete synchronization of the membership table

Once complete, the node has full status knowledge of the all other nodes in the cluster and is ready to
start broadcasting its new alive status to the rest of the cluster:
```
[...] INFO administration.Administration - Successful full sync response received from seed node
[...] INFO administration.Administration - Broadcasting join event to other nodes
[...] DEBUG administration.gossip.Gossip - Gossip request received with key GossipKey(Event(022fd1be6f6b4fc3a857266cbac07f01cf295d0f688fabcaa83b42443f81fafd,Join(Join(167837872,PartitionHashes(List())))))
[...] DEBUG common.gossip.GossipActor - Cluster size detected as 2, setting gossip round buffer to 5
``` 

The join event is send to the `GossipActor` instance, which is the component responsible for broadcasting the 
event through gossip dissemination. You can see that 5 is the number of rounds of gossip that will be 
spread by this particular node before it goes into cooldown  

On the seed node side, it'll first receive the full sync request, and then receive the join gossip
message shortly after:
```
[...] INFO administration.AdministrationGRPCService$ - Full sync requested from node 2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27 with IP 10.1.0.92
[...] DEBUG administration.AdministrationGRPCService$ - Event received from 2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27, forwarding to membership actor
[...] DEBUG administration.Administration - Join event - 2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27 - Join(167837788,PartitionHashes(Vector()))
[...] INFO administration.Administration - New node 2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27 added to membership table with IP address 167837788
[...] DEBUG administration.gossip.Gossip - Gossip request received with key GossipKey(Event(022fd1be6f6b4fc3a857266cbac07f01cf295d0f688fabcaa83b42443f81fafd,Join(Join(167837872,PartitionHashes(Vector())))))
[...] DEBUG administration.gossip.Gossip - Cluster size detected as 2, setting gossip round buffer to 5
```

Note that from the perspective of the seed node, the new node won't be officially added by the full
sync request, and instead waits until the join event gossip arrives. This will mean that the joining node 
assumes full responsibility for broadcasting the join notification instead of the seed node.

Afterwards, both nodes will stabilize and start to periodically perform failure checks on each other, and 
reply liveness confirmations to incoming checks:
```
[...] DEBUG administration.failureDetection.FailureDetector - Target [2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27, 10.1.0.92] successfully passed initial direct failure check
[...] DEBUG administration.failureDetection.FailureDetector - Target [2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27, 10.1.0.92] successfully passed initial direct failure check
[...] INFO administration.failureDetection.FailureDetectorGRPCService$ - Health check request has been received, sending confirmation
[...] INFO administration.failureDetection.FailureDetectorGRPCService$ - Health check request has been received, sending confirmation
[...] DEBUG administration.failureDetection.FailureDetector - Target [2551c17d92b95acfaa5a1528c45eee54829572df33dfbd01b383d722e48e0e27, 10.1.0.92] successfully passed initial direct failure check
```

Now you can scale your cluster to any size you want!

However, this is a good time to point out that this fully automatic scaling process can only be achieved
if there is a DNS server present, as the nodes will perform a DNS lookup to retrieve the IP address of the
cluster seed node (the hostname `cdb-0.chs.chordial-ns.svc.cluster.local`)

Without a DNS server, it is still possible to have future nodes scaled automatically, but it will require
you to manually specify the seed node IP address into the Kubernetes `StatefulSet` configuration. The
program will attempt to read this IP address from the environment variable `SEED_IP` if it fails to
read the variable `SEED_NODE`. 


---
## Raft on a Single Node

When starting up a single node, you'll notice that it immediately begins the election process. Since there is 
nobody else to provide votes, it will win and become leader for Term 1. This is the log output from winning the election:
```
[...] INFO replication.RaftFSM - Starting leader election for new term: 1
[...] INFO replication.RaftFSM - Election won, becoming leader of term 1
```

Once it has become leader, it can start processing `POST` and `DELETE` client requests (note that `GET` 
requests do not go through Raft and read directly from disk). Upon a `POST` request, the leader will write the
entry into its write-ahead log (WAL) and return an acknowledgement once it finishes.

At the same time, Raft will notice that a new entry has been appended to the WAL - combined with the fact
that it's the only node in the cluster, it will determine that it is safe to hand the `POST` request off to
the persistence layer to apply on disk. Only when the persistence layer writes the entry to disk will the entry
be visible through a client `GET`.

Here is an example of what a `post -k "hello" -v "world"` request looks like going through Raft; notice the two
distinct phases of writing to the WAL before officially committing the change: 
```
[...] DEBUG replication.ReplicationComponent - Post request received with UUID d66f67e0-9692-4ca5-9105-13a914781888 and hex value: 776F726C64
[...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appending log entry #1 at offset 0 and byte length 63 to WAL
[...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Appended log entry: 0A0568656C6C6F123612340A0568656C6C6F12191F8B08000000000000002BCF2FCA4901004311773A050000001A10D66F67E096924CA5910513A914781888
...
[...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieving log entry #1 at offset 0 and byte length 63 from WAL
[...] DEBUG replication.eventlog.SimpleReplicatedLog$ - Retrieved log entry: 0A0568656C6C6F123612340A0568656C6C6F12191F8B08000000000000002BCF2FCA4901004311773A050000001A10D66F67E096924CA5910513A914781888
[...] INFO replication.RaftFSM - Write entry with key 'hello' and UUID d66f67e0-9692-4ca5-9105-13a914781888 will now attempt to be committed
```

## Raft Cluster Operations

The first thing to note is that the nodes the administration module considers to be a part of the cluster is 
not necessarily what Raft considers to be a part of the cluster. There are basically two independent membership
protocols within the program. 

Raft needs to ensure that there is consensus on what the cluster actually is - so while it gets notified about 
joining nodes by the administration module's gossip, it will apply backpressure to ensure that each joining node
is applied one at a time.

When the leader is delivered a gossip join message, it will attempt to replicate the join command to the existing
nodes in the Raft cluster to get majority agreement on the new server. Once a majority agrees, the leader
officially adds the new server to the cluster and it can start receiving client messages:

```
[...] INFO replication.RaftFSM - Committing node add entry, node c6518456f35b64e33b4302c14f33af4a41a13ca517e176ab50aeefe2b8fc98ac officially invited to cluster
```

Overall, the typical workflow for when there's multiple nodes are the same as when there's just one, except that 
the leader has to reach out to the cluster and confirm with a majority of nodes every time it wants to write something.

The log walkthrough for the log entry replication process is quite long, so I've moved it over to the
[log samples](project/logSamples) subfolder, where you'll find annotated explanations about cluster operations
in a 3-node cluster titled `log_sample_2-leader.md`, `log_sample_2-follower1.md`, and `log_sample_2-follower2.md`

This example goes over Raft cluster joining, replicating log entries from both the leader and follower side, and
the subsequent stable cluster state where all followers receive a steady stream of heartbeat messages and no
new elections occur.

---
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

