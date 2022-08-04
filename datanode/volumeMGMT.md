# HDFS Datanode Volume Management

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
- [Volume Scanner in Action](#Volume-Scanner-in-Action)
- [Questions](#Questions)

<!-- tocstop -->

---


Cluster: 

> Resource: [HDFS DataNode Scanners and Disk Checker Explained - Cloudera Blog](https://blog.cloudera.com/hdfs-datanode-scanners-and-disk-checker-explained/)

## Overview
Volume scanners are a single unit apart of a collection that makes up a DataNode's block scanner. Volume scanners run in their own thread and are assigned a single volume where it will read each block on the volume. The position of the scan is maintained by the `scanner.cursor` file in case the DN is restarted.

The VolumeScanner may flag a file if it has been the subject of non-network i/o exceptions and keeps a list of these blocks. Despite the

__Setup:__
Under `hdfs-site.xml` we set the property to have volume scanner run every hour. 

```
dfs.datanode.scan.period.hours=1
```

On startup we may see: 
```
datanode.VolumeScanner (VolumeScanner.java:findNextUsableBlockIter(392)) - Now rescanning bpid BP-1903489469-172.25.34.221-1623421800996 on volume /hadoop/hdfs/data, after more than 1 hour(s)
```

## Volume Scanner in Action

__1. Corrupt a block:__

We will start by corrupting a block and the .meta file with random data.
```
# readlink -f blk_1073742037
/hadoop/hdfs/data/current/BP-1903489469-172.25.34.221-1623421800996/current/finalized/subdir0/subdir0/blk_1073742037
--
# head -c 1M </dev/urandom > blk_1073742037.*
--
# md5sum blk_1073742037*
1f1391eab89512fc480912c96ad07047  blk_1073742037
ae18bfb12779b314e62787d0c5f9a8a6  blk_1073742037_1226.meta
```

We can verify that the corruption is found via the `hdfs dfsadmin -metasave` command which will offer up this information: 

```
Metasave: Blocks waiting for replication: 1
/cache/1/3GB.tar.gz.2: blk_1073742037_1226 (replicas: l: 2 d: 0 c: 1 e: 0)  172.25.39.214:1019 :  172.25.34.204:1019 :  172.25.35.5:1019(corrupt) :
...
Corrupt Blocks:
Block=1073742037        Node=172.25.35.5:1019   StorageID=DS-24127ce0-18e0-4876-8d3b-78a4a499a7a6       StorageState=NORMAL     TotalReplicas=3 Reason=SIZE_MISMATCH
```

## Questions

__How do you isolate if Volume Scanner is causing disk slowness:__
Taking jstacks or within the log files there will be a record of how long it took the VolumeScanner to scan a volume.


__How do optimize volume scanner?__
dfs.block.scanner.volume.bytes.per.second
dfs.datanode.scan.period.hours