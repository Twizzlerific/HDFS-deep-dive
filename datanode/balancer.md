# HDFS Balancer

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
  * [DEMO: Balancer](#DEMO-Balancer)
- [Questions](#Questions)

<!-- tocstop -->

---


Cluster: 

## Overview 


### DEMO: Balancer 


__Making a DataNode over-utilized__
Shutdown 2/4 of the DataNodes; Decommission 3rd; add 5GB file to hdfs with replication factor set to 1

```
hadoop --config /opt/distcpConf fs -D ipc.client.fallback-to-simple-auth-allowed=true -D dfs.replication=1 -put 5Blocks2.img webhdfs://ks2/tmp/5Blocks1225634
```

After this, 1 Datanode was 3.79% used and the lowest was at 0.85%

We will also tune the balancer bandwidth: 
```
$ hdfs dfsadmin -setBalancerBandwidth 31457280
Balancer bandwidth is set to 31457280
```

Now running Balancer with threshold: 

```
hdfs balancer -threshold 2
// Started at 21/08/05 18:29:22

...
//Logs
21/08/05 18:31:42 INFO balancer.Dispatcher: Successfully moved blk_1073742754_1952 with size=134217728 from 172.25.38.131:50010:DISK to 172.25.33.209:50010:DISK through 172.25.38.131:50010
Aug 5, 2021 6:31:43 PM            1              1.13 GB           661.84 MB            2.13 GB

// Completed at 21/08/05 18:33:07
// Ran for ~4 mins
```

## Questions


__How does HDFS balancer differ from disk balancer?__

HDFS Balancer can focus on balancing block density across the cluster or Block pool (if multiple BPs exist) whereas disk balancer will focus on a DataNode and the block distribution within that DataNode's disks. 

__Does balancer cause performance issues? If so, what to do__

Yes, Balancer can cause network and disk overhead as blocks are being moved. We can tune aronud this by defining the number of: 

Concurrent Move :: `dfs.datanode.balance.max.concurrent.moves`
Bandwidth of transer :: `dfs.datanode.balance.bandwidthPerSec`
Number of Threads :: `dfs.balancer.moverThreads`
Max size of within iteration :: `dfs.balancer.max-size-to-move`

We can also define a larger threshold which will define the percentage of disk utilization that a DN can be +/- the average to require balancing.

__How do we tune balancer? Please demo a result, the performance before the change and after the change.__

Added to hdfs-site.xml on DNs
```
 <property>
      <name>dfs.datanode.balance.max.concurrent.moves</name>
      <value>10</value>
    </property>
```
Reconfigure DataNodes: 

```
for i in c346-node{1..4}.supportlab.cloudera.com; do hdfs dfsadmin -reconfig datanode $i:8010 start; done
```
When running balancer we can see update: 
```
INFO balancer.Balancer: dfs.datanode.balance.max.concurrent.moves = 10 (default=50)
```

Reducing the number of concurrent moves significantly increased the time it took to reach a balanced state. 

```
# time hdfs balancer -threshold 2
real    10m11.481s
user    0m9.361s
sys 0m1.466s
```

__How do they differ? Balancer vs Mover?__

While balancer considers DataNode disk usage, mover considers a blocks defined storage policy. 

