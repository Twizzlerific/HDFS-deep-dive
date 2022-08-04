# HDFS Performance

---
__TASKS:__

<!-- toc -->

- [NameNode Performance](#NameNode-Performance)
- [Common issues](#Common-issues)
  * [RPC Latency](#RPC-Latency)
- [Tunings](#Tunings)
  * [JVM Related Tunings:](#JVM-Related-Tunings)
  * [Service Related Tunings:](#Service-Related-Tunings)

<!-- tocstop -->

---


## NameNode Performance
The NameNode is the big orchestator for HDFS wrapped in a JVM. It communicates with clients and datanodes over RPC calls but also maintains the various filesystem objects/metadata/leases, audits all operations, and maintains durability through JNs/SNN as well as persisted files locally. All of these areas can be bottlenecks resulting in performance failures. 

## Common issues

__General Slowness__

When the NameNode is slow, it can result in performance impacts for all components which interact with HDFS. It is best to quantify 'slow' by using simplified commands. 

For example, if a customer is experiencing slowness adding files to HDFS, we can use `time hdfs dfs -put` to get a baseline of the slowness. We can similarly use the `time hdfs dfs -get` command when troubleshooting retrieval from HDFS.

In tandem with the above commands, using: 

`export HADOOP_ROOT_LOGGER=DEBUG,console`

Will allow us to gather more logs from the perspective of the client. This would include connections and requests to the NameNode as well as DNs and slowness or stalling on a specific connection can be a good indicator of a problem.


__Slow Infrastructure__
Slow disks, networks can result in low performance while the processes wait to complete a task. This can surface in slow replication pipelines or persisting files to disk. When either occurs, we should get various error messages in the logs:

__DataNode:__

Writing to disk:
```
org.apache.hadoop.hdfs.server.datanode.DataNode: Slow BlockReceiver write data to disk cost:1295ms (threshold=300ms), volume=file:/data/dfs/dn/, blockId=1477503063
```

Flushing from OS Buffer: 
```
datanode.DataNode (BlockReceiver.java:flushOrSync(440)) - Slow flushOrSync took 2118ms (threshold=300ms), isSync:true, flushTotalNanos=8178ns, volume=file:/data1/hadoop/hdfs/data/, blockId=1107664935
```

Slow replication of a block to another DataNode
```
Slow BlockReceiver write packet to mirror took 12401ms (threshold=300ms), downstream DNs
```
__JournalNode:__
Slow Flush between JNs:

```
WARN  server.Journal (Journal.java:journal(398)) - Sync of transaction range 31062471421-31062471641 took 10766ms
```


__Group Lookup__
When a FileSystem objects (i.e. a file/directory) requires association with a group for the user, NameNode may reach out to an LDAP server. Because everything is kept sequentially, this action can result in a stalled NameNode while it waits for the response. 

We will see an error such as: 

```bash
Potential performance problem: getGroups(user=iron_man) took 22354 milliseconds
```

The best resolution is to resolve the delay on the LDAP side but properties such as static mapping of users to group can be a remedy as it will bypass the group lookup and associate the user based on the mapping: 

```xml
<property>
<name>hadoop.user.group.static.mapping.overrides</name>
<value>hdfs=hdfs,hadoop;kevin=Admin;<\value>
```




### RPC Latency
The NameNode processes requests via the a single RPC queue per port. Handler threads are used to dequeue and process requests. The number of these handlers is controlled by `dfs.namenode.handler.count`.


__RPC Spikes__
If a app or user performs an operation (such as a recursive listing or count) on a large directory, the number of RPC calls will spike on the NameNode. 

This can result in performance impacts as the NameNode rpc queue will be flooded and new requests such as those from a new user/app will stall waiting for a connection. 

We can identify such causes by checking the hdfs-audit logs for batches of operations from a single user within a small amount of time. 

## Tunings


### JVM Related Tunings:

__Heap:__
Garbage collection or JVM pauses will be the main reason for Heap changes. We can use the command: 

`# jmap -heap <PID>` 

to check the usage across the full heap of the process. In addition, the GC logs can also indicate which part of heap is being impacted. 

For example, if we see the NameNode stalling during startup, it may be caused by the number of objects in the FileSystem being ingested into memory. 

Resource: [Configuring NameNode Heap Size - Hortonworks Data Platform](https://docs.cloudera.com/HDPDocuments/HDP2/HDP-2.6.5/bk_command-line-installation/content/configuring-namenode-heap-size.html#:~:text=Hortonworks%20recommends%20a%20maximum%20of,%2DXX%3AMaxPermSize%20to%20256m.)


### Service Related Tunings:

__Service RPC:__
The ServiceRPC port is used by Datanodes and the Zookeeper Failover Controllers to connect with the NameNode. This will create a separate queue for heartbeats, blocks reports, and health checks to avoid them starving or being starved by a queue which also includes client operations.

__Datanode LifeLine RPC:__
Useful on busy clusters where the NameNode is too busy to process the heartbearts of DataNodes resulting in them appears to be dead. This can be problematic as the NameNode will then attempt to return the blocks on that DN to the replication factor resulting overheard.

This feature give the DN a new port for conveying their health to the NameNode.


__FairCallQueue:__
The FairCallQueue is a feature which splits the single RPC queue into multiple queues with prioritye


> Resource: [RPC FairCallQueue](https://community.cloudera.com/t5/Community-Articles/Scaling-the-HDFS-NameNode-part-3-RPC-scalability-features/ta-p/246719)


__IPC BackOff:__
An overloaded RPC queue will result in timeouts. Enabling this feature results in clients request being throttled and with increasing delays to avoid operations that wait and then eventually fail.





