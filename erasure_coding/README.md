# HDFS Erasure Coding

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
- [Setting up EC](#Setting-up-EC)
- [Using EC](#Using-EC)
- [Testing EC Recovery](#Testing-EC-Recovery)

<!-- tocstop -->

---


Cluster: c446 
CDP 7.1.6

> Resources: [Erasure Coding](https://blog.cloudera.com/introduction-to-hdfs-erasure-coding-in-apache-hadoop/)


## Overview 

Erasure coding is a framework for preventing data loss commonly seen implemented in RAID setups. It works by breaking files into smaller units and storing the differnet blocks on different disks. 

This is similar to what we accomplish within HDFS via replication of blocks but deviates in the use of parity data. Parity data files are created when a file is encoded resulting in parity cells. These cells are then distributed so that if one is lost of becomes corrupt, the rest of the cells can be used to reconstruct that file. 


__EC Policies:__

`XOR`: Exclusive-or 1 parity bit is generated for an arbitrary number of bits. The down side is that is can only tolerate a single failure. 


`Reed-Solomon (RS)`: Generates multiple parity cells so that it can tolerate multiple failures. 

The default policy is `RS-6-3-1024k` which maintains that each stripe of data will have `6` blocks. Each stripe will also have `3` parity blocks (even if it doesn't need it). Lastly, the size of a single cell should be `1024k`. 

__When to use what?:__
Within EC there are two main ideologies for the policies: Contiguous layout and striped layout: 

Contiguous layouts favor deployments with lots of large files. 

Striped layouts favor deployments with lots of small sizes. 


## Setting up EC

Erasure coding is applied at the directory level so we need to first create directories: 

1. Creating EC directories
```
# hdfs dfs -mkdir /ec
# hdfs dfs -chmod 777 /ec
```

2. Next we verify the available policies and codecs: 

```
# hdfs ec -listCodecs
Erasure Coding Codecs: Codec [Coder List]
    RS [RS_NATIVE, RS_JAVA]
    RS-LEGACY [RS-LEGACY_JAVA]
    XOR [XOR_NATIVE, XOR_JAVA]
  ---
  
# hdfs ec -listPolicies
Erasure Coding Policies:
ErasureCodingPolicy=[Name=RS-10-4-1024k, Schema=[ECSchema=[Codec=rs, numDataUnits=10, numParityUnits=4]], CellSize=1048576, Id=5], State=DISABLED
ErasureCodingPolicy=[Name=RS-3-2-1024k, Schema=[ECSchema=[Codec=rs, numDataUnits=3, numParityUnits=2]], CellSize=1048576, Id=2], State=DISABLED
ErasureCodingPolicy=[Name=RS-6-3-1024k, Schema=[ECSchema=[Codec=rs, numDataUnits=6, numParityUnits=3]], CellSize=1048576, Id=1], State=DISABLED
ErasureCodingPolicy=[Name=RS-LEGACY-6-3-1024k, Schema=[ECSchema=[Codec=rs-legacy, numDataUnits=6, numParityUnits=3]], CellSize=1048576, Id=3], State=DISABLED
ErasureCodingPolicy=[Name=XOR-2-1-1024k, Schema=[ECSchema=[Codec=xor, numDataUnits=2, numParityUnits=1]], CellSize=1048576, Id=4], State=DISABLED
```

> Note: `XOR` is not supported on CDP


3. With a list of policies we first need to enable them: 

```
# hdfs ec -enablePolicy -policy RS-3-2-1024k
Erasure coding policy RS-3-2-1024k is enabled
```
> Note: in CDP, enabling a policy checks the cluster setup and provides warnings if they would conflict but does not fail: 
> ```
> # hdfs ec -enablePolicy -policy RS-3-2-1024k
> Erasure coding policy RS-3-2-1024k is enabled
>Warning: The cluster setup does not support EC policy RS-3-2-1024k. Reason: 3 racks are required for the erasure coding policies: RS-3-2-1024k. The number of racks is only 1.
>```
>


4. Now we can apply a policy to our directory: 

```
# hdfs ec -setPolicy -path /ec -policy RS-6-3-1024k
Set RS-6-3-1024k erasure coding policy on /ec
```

5. Let's verify that the policy is applied: 

```
# hdfs ec -getPolicy -path /ec
RS-6-3-1024k
```

6. What happens when we try to add a file to the directory: 

```
# hdfs dfs -put /etc/hosts /ec
21/07/21 12:41:15 WARN erasurecode.ErasureCodeNative: Loading ISA-L failed: Failed to load libisal.so.2 (libisal.so.2: cannot open shared object file: No such file or directory)
21/07/21 12:41:15 WARN erasurecode.ErasureCodeNative: ISA-L support is not available in your platform... using builtin-java codec where applicable
put: File /ec/hosts._COPYING_ could only be written to 4 of the 6 required nodes for RS-6-3-1024k. There are 4 datanode(s) running and no node(s) are excluded in this operation.
```

This is because `RS-6-3-1024k` was chosen which requires `6` data blocks and the same number of datanodes. 

7. We can update the policy on a directory: 

```
# hdfs ec -setPolicy -path /ec -policy RS-3-2-1024k
# hdfs ec -getPolicy -path /ec
RS-3-2-1024k

# hdfs dfs -put /etc/hosts /ec
# hdfs ec -getPolicy -path /ec/hosts
RS-3-2-1024k
```

> Note it is important to ensure there are enough data volumes available for the set policy.


## Using EC 

__Transition from Replication to EC:__
Applying a policy to a directory with existing data does not force those blocks to use the policy. Data must be copied into the dir using distcp so that the blocks are rewritten. 

1. Let's first verify the original file has not EC Policy: 

```
# hdfs ec -getPolicy -path /tmp/hosts
The erasure coding policy of /tmp/hosts is unspecified
```


2. Now we can move the file using distcp:

```
// Verifying Checksum of file
# hdfs dfs -get /tmp/hosts | sha512sum
cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e  -

// Transitioning file to EC
# hadoop distcp -pacx /tmp/hosts /ec

// Verifying new policy
# hdfs ec -getPolicy -path /ec/hosts
RS-3-2-1024k

# hdfs dfs -get /ec/hosts | sha512sum
cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e  -
```
> Note: The `-pacx` flags on ensure Permissions, ACLs, Checksum, and Extended attributes are preserved.


## Testing EC Recovery

If a block file becomes corrupt, EC should automatically initiate recovery. Let's test this out.

__Corrupting Blocks:__

1. Let's find block locations: 

```
# hdfs fsck /ec/hosts -blocks -files -locations

// Output includes 3 replicas
blk_-9223372036854775744:DatanodeInfoWithStorage[172.25.38.6:1019,DS-57be7508-4056-42a1-bb08-735f63d9acce,DISK], 
blk_-9223372036854775740:DatanodeInfoWithStorage[172.25.32.142:1019,DS-e5ddd22e-bfd1-415d-bdab-bae980b30629,DISK], 
blk_-9223372036854775741:DatanodeInfoWithStorage[172.25.36.22:1019,DS-69746234-dfcd-4775-ab5f-71a13a3d3a66,DISK]
```
They exist on node2, node6, and node3

2. Then we can find the actual block files and remove them: 

```
// 172.25.32.142 - node6 - blk_-9223372036854775740
# ls -R | grep blk_-9223372036854775740
blk_-9223372036854775740
blk_-9223372036854775740_287974.meta
# mv blk_-9223372036854775740* /tmp


// 172.25.38.6 - node2 - blk_-9223372036854775744
# ls -R | grep -i blk_-9223372036854775744
blk_-9223372036854775744
blk_-9223372036854775744_287974.meta

# mv blk_-9223372036854775744* /tmp
```

__Monitoring Recovery:__
With two out of the 3 blocks removed, the data will get rebuild


From Client: 
```
# hdfs dfs -cat /ec/hosts
```

DataNode Logs:
```
java.io.IOException: Got error, status=ERROR, status message opReadBlock BP-412483913-172.25.38.14-1620673804275:blk_-9223372036854775740_287974 received exception java.io.FileNotFoundException: BlockId -9223372036854775740 is not valid., for OP_READ_BLOCK, self=/172.25.38.6:47762, remote=/172.25.32.142:1019, for file dummy, for pool BP-412483913-172.25.38.14-1620673804275 block -9223372036854775740_287974
    at org.apache.hadoop.hdfs.protocol.datatransfer.DataTransferProtoUtil.checkBlockOpStatus(DataTransferProtoUtil.java:134)
    at org.apache.hadoop.hdfs.protocol.datatransfer.DataTransferProtoUtil.checkBlockOpStatus(DataTransferProtoUtil.java:110)
    at org.apache.hadoop.hdfs.client.impl.BlockReaderRemote.checkSuccess(BlockReaderRemote.java:440)
    at org.apache.hadoop.hdfs.client.impl.BlockReaderRemote.newBlockReader(BlockReaderRemote.java:408)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedBlockReader.createBlockReader(StripedBlockReader.java:128)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedBlockReader.<init>(StripedBlockReader.java:83)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedReader.createReader(StripedReader.java:169)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedReader.initReaders(StripedReader.java:150)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedReader.init(StripedReader.java:133)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedBlockReconstructor.run(StripedBlockReconstructor.java:56)
    at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
    at java.util.concurrent.FutureTask.run(FutureTask.java:266)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
    at java.lang.Thread.run(Thread.java:745)
2021-07-21 14:40:57,634 WARN  datanode.DataNode (StripedBlockReconstructor.java:run(67)) - Failed to reconstruct striped block: BP-412483913-172.25.38.14-1620673804275:blk_-9223372036854775744_287974
java.io.IOException: Can't find minimum sources required by reconstruction, block id: -9223372036854775744
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedReader.initReaders(StripedReader.java:162)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedReader.init(StripedReader.java:133)
    at org.apache.hadoop.hdfs.server.datanode.erasurecode.StripedBlockReconstructor.run(StripedBlockReconstructor.java:56)
    at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
    at java.util.concurrent.FutureTask.run(FutureTask.java:266)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
    at java.lang.Thread.run(Thread.java:745)
```

On client the contents of the file is returned and another fsck shows:

```
0. BP-412483913-172.25.38.14-1620673804275:blk_-9223372036854775744_287974 len=1757 Live_repl=3  [
1. blk_-9223372036854775744:DatanodeInfoWithStorage[172.25.38.6:1019,DS-57be7508-4056-42a1-bb08-735f63d9acce,DISK], 
2. blk_-9223372036854775740:DatanodeInfoWithStorage[172.25.37.145:1019,DS-371acb03-1f2d-4145-8dcf-d327cb249934,DISK], 
3. blk_-9223372036854775741:DatanodeInfoWithStorage[172.25.36.22:1019,DS-69746234-dfcd-4775-ab5f-71a13a3d3a66,DISK]]

```

When we check the datanode Directories we see that the files have returned: 


```
// 172.25.32.142 - node6 - blk_-9223372036854775740
# ls -R | grep blk_-9223372036854775740
blk_-9223372036854775740
blk_-9223372036854775740_287974.meta
# mv blk_-9223372036854775740* /tmp


// 172.25.38.6 - node2 - blk_-9223372036854775744
# ls -R | grep -i blk_-9223372036854775744
blk_-9223372036854775744
blk_-9223372036854775744_287974.meta

# mv blk_-9223372036854775744* /tmp
```

