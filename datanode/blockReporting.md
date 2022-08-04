# HDFS DataNoe Block Reporting

## Overview

Block reports are intermittent notices to the NameNode from the datanodes. Block reports will contain a list of all the blocks per volume. NameNode then consume block report while holding a write lock on the FileSystem. This means that a large number of block reports and/or RPC latency can result in NameNode slowness. 

Triggering a block report: 

```
hdfs dfsadmin -triggerBlockReport `hostname -f`:8010
```


When block reports are sent
```
datanode.DataNode (BPServiceActor.java:blockReport(395)) - Successfully sent block report 0x80f9d6244e6b11,  containing 1 storage report(s), of which we sent 1. The reports had 243 total blocks and used 1 RPC(s). This took 5 msec to generate and 16 msecs for RPC and NN processing. Got back one command: FinalizeCommand/5.
2021-07-22 19:49:35,900 INFO  datanode.DataNode (BPOfferService.java:processCommandFromActive(696)) - Got finalize command for block pool BP-1903489469-172.25.34.221-1623421800996
```



