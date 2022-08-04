# NameNode High Availability

All clients acceses data on HDFS through the NameNode. If the NameNode goes down, the entire distributed file system become inaccessible. A High Availability setup allows for multiple NameNodes in case of failure. High Availability works in an `active-passive` state which means only one is 'Active' and manages the FS. The other is in 'Standby' state and will reject client access to avoid corrupting the FS.



---
__Tasks:__
<!-- toc -->

- [HA Setup](#HA-Setup)
- [Moving NameNode w/o Downtime](#Moving-NameNode-wo-Downtime)
- [Checkpoint Standby NN](#Checkpoint-Standby-NN)
- [Manual Failover](#Manual-Failover)
- [Questions](#Questions)

<!-- tocstop -->

---


## HA Setup
Cluster: HDP 2.6.5.0-292
Current NameNode: c346-node2.supportlab.cloudera.com
New NameNode: c346-node1.supportlab.cloudera.com

- Using Ambari Wizard
- Select NameSpaceId: `ks2`
- Complete Manual Steps:

__On c346-node2.supportlab.cloudera.com:__
```
# su hdfs -l -c 'hdfs dfsadmin -safemode enter'
Safe mode is ON

# su hdfs -l -c 'hdfs dfsadmin -saveNamespace'
Save namespace successful
```

- Wizard will set up and install additional NameNode, JournalNodes, and Zookeeper Failover Controllers (ZKFC)


```
# su hdfs -l -c 'hdfs namenode -initializeSharedEdits'
```
- This command will format a new shared edits dir and copy in enough edit log segments so that the standby NameNode can start up. 

```
# su hdfs -l -c 'hdfs zkfc -formatZK'
```
- Formats Zookeeper znode for HDFS HA

> New NameNode: c346-node1.supportlab.cloudera.com.

__On c346-node1.supportlab.cloudera.com:__

```
su hdfs -l -c 'hdfs namenode -bootstrapStandby'
```
- Standby NameNode will copy latest snapshot from active NN


## Moving NameNode w/o Downtime
Original nn2: c346-node1.supportlab.cloudera.com
New nn2: c346-node3.supportlab.cloudera.com

>Script for repeated writes: 
>`for i in {1..50}; do hdfs dfs -touchz /tmp/$i; sleep 2s; done`


- Put Standby NameNode into safeMode
- Tar up namenode dir: `/hadoop/hdfs/namenode`
- Move tar to new node
- create directory if needed 
- create `xconfig` dir and cp configs from `/etc/hadoop/conf`
- Edit hdfs-site.xml in xconfig to make nn2 node3
- Start NameNode
`hdfs --config xconfig namenode`


Alternatively we can just start the NameNode and have it retrieve the most recent checkpoint from the active NN:
`hdfs --config xconfig namenode -bootstrapStandby`

- Verifying with Client:
```
# hdfs --config xconfig/ dfs -fs hdfs://c346-node3.supportlab.cloudera.com:8020 -ls /tmp
ls: Operation category READ is not supported in state standby
```



## Checkpoint Standby NN

```
[hdfs@c346-node1 root]$ hdfs dfsadmin -fs hdfs://`hostname -f`:8020 -safemode enter
Safe mode is ON
[hdfs@c346-node1 root]$ hdfs dfsadmin -fs hdfs://`hostname -f`:8020 -saveNamespace
Save namespace successful
[hdfs@c346-node1 root]$ hdfs dfsadmin -fs hdfs://`hostname -f`:8020 -safemode leave
Safe mode is OFF
```

## Manual Failover

```
# hdfs haadmin -failover nn1 nn2
Failover to NameNode at c346-node1.supportlab.cloudera.com/172.25.36.210:8020 successful
```

## Questions

__Does restarting the zookeeper will result in any namenode failover? What is the use of zookeepers in HA?__
No, Zookeeper restarts will not cause Failovers. HDFS will reconnect when ZK is back up. ZK is used for automatic failovers so if ZK is not healthy automatic failover may not occur


__What is the disk type recommended for namenode, journal node, and zookeeper?__

NameNode, JN, and Zookeeper nodes require highly reliable storage because they can be i/o intensive. In addition, each should have a dedicated volume not in use by any of the other master components.


__If a slow disk is causing NN crash Or failover then workaround?__

If there is a single slow disk on the node out of multiple, we can edit the dfs.namenode.name.dir to exclude that disk.


