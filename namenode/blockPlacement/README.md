# HDFS NAMENODE: Block Placement

---
__TASKS:__

<!-- toc -->

- [Rack Topology](#Rack-Topology)
- [Disk Types](#Disk-Types)
  * [Configuring Storage Type](#Configuring-Storage-Type)
- [Moving Data between disk types](#Moving-Data-between-disk-types)

<!-- tocstop -->

---


Cluster: HDP 2.6.5.0-292
NameNodes:
- c346-node2.supportlab.cloudera.com (S)
- c346-node1.supportlab.cloudera.com (A)


## Rack Topology
Rack topology is logic used by the NameNode to decide block placement. The goal is to ensure tolerance by spreading around blocks between racks. 

__Creating Racks:__

Via:

`Ambari > Hosts > Selecting Hosts > Actions > Selected Hosts > Set Rack`

Set node1,node2 to new rack: /rack01
Other nodes set to rack: /rack02

## Disk Types
Specifying disk types allows the users to define the type of storage when pushing data to HDFS. This can be helpful if some data is needed quickly (so assigned to SSD disk) vs one that is cold data (possibly assigned to ARCHIVE storage)

### Configuring Storage Type
Configuring different storage types requires a few steps: 

1. Assigning Storage type to local volume on DN. 
2. Setting the storage policy within HDFS
3. (optional) Moving data to new storage type using the `hdfs mover` tool.


__1. Assigning Storage Type:__
Updating the value of `dfs.datanode.data.dir`:

Current Value: `/hadoop/hdfs/data`
New Value: 
```
[DISK]/hadoop/hdfs/data,
[SSD]/hadoop/hdfs/ssd,
[ARCHIVE]/hadoop/hdfs/archive
```
Will need to create new directories on all datanodes and ensure permissions are correct. 

```
for i in c346-node{1..4}; \
do ssh root@$i "mkdir -p /hadoop/hdfs/{ssd,archive}"; \
do ssh root@$i "chown -R hdfs:hadoop /hadoop/hdfs/{ssd,archive}"; done
```
After restart verifying that there are no blocks finalized within the /ssd or /archive directories

Finally, we will create two new HDFS directories: 

```
hdfs dfs -mkdir /archive /ssd
hdfs dfs -chown 777 /archive /ssd
```


__2. Setting Storage Policy:__
We now need to tell HDFS that these new directories have storage policies associated with them. Storage Policies define the preferred location for the blocks to be persisted (i.e. Disk, archival disk, or SSD). The chosen policy may also impact replication as it can require a number of replicas to be persisted to other storage types. We do this using the `hdfs storagepolicies` command:

Currently, we get the folowing output: 
```
$ hdfs storagepolicies -getStoragePolicy -path /archive
The storage policy of /archive is unspecified
```

*For setting the Archive Storage*
```
$ hdfs storagepolicies -setStoragePolicy -path /archive -policy COLD
Set storage policy COLD on /archive
--
$ hdfs storagepolicies -getStoragePolicy -path /archive
The storage policy of /archive:
BlockStoragePolicy{COLD:2, storageTypes=[ARCHIVE], creationFallbacks=[], replicationFallbacks=[]}
```

*For SSD Storage*

```
$ hdfs storagepolicies -getStoragePolicy -path /ssd
The storage policy of /ssd is unspecified
--
$ hdfs storagepolicies -setStoragePolicy -path /ssd -policy All_SSD
Set storage policy All_SSD on /ssd
```
__3. Adding Data to these storage types__
To add data to a specific type, we just need to put data in those directories on HDFS: 

First let's create a file that should get broken down into 5 blocks:
```
# dd if=/dev/urandom of=5Blocks.img bs=1M count=640
```
Now we can `-put` the file into the different directories. 

```
 $ hdfs dfs -put 5Blocks.img /archive
```

__Investigate block names/Locations:__
Blocks now appear within the archive directory:

```
/hadoop/hdfs/archive/current/BP-1559210326-172.25.38.140-1623421882856/current/finalized/subdir0/subdir0/blk_107374185
```

Similarly, done with the /ssd directory and get blocks in the local filesystem: 

```
/hadoop/hdfs/ssd/current/BP-1559210326-172.25.38.140-1623421882856/current/finalized/subdir0/subdir0/blk_10737418
```

## Moving Data between disk types

Unless it is newly being added to HDFS, data will **not** automatically respect storage policies. For existing data we need to use the `hdfs mover` tool. 

We have a file under `/tmp/5Blocks2.img` that we will move to one of the new storage policies: 

```
$ hdfs storagepolicies -getStoragePolicy -path /tmp/5Blocks2.img
The storage policy of /tmp/5Blocks2.img is unspecified

$ hdfs dfs -mkdir /base && hdfs dfs -chmod 777 /base

$ hdfs dfs -cp /tmp/5Blocks2.img /base

$ hdfs storagepolicies -getStoragePolicy -path /base/5Blocks2.img
The storage policy of /base/5Blocks2.img is unspecified

$ hdfs storagepolicies -setStoragePolicy -path /base -policy All_SSD
Set storage policy All_SSD on /base

$ hdfs storagepolicies -getStoragePolicy -path /base/5Blocks2.img
The storage policy of /base/5Blocks2.img:
BlockStoragePolicy{ALL_SSD:12, storageTypes=[SSD], creationFallbacks=[DISK], replicationFallbacks=[DISK]}
```

Despite the storage policy being defined, the blocks would not have shifted to the correct storage until we ran the mover. 

Within DEBUG, we can see the following: 

```
balancer.Dispatcher: Decided to move blk_1073741885_1061 with size=134217728 from 172.25.38.135:50010:DISK to 172.25.38.135:50010:SSD through 172.25.38.135:50010
```
Since both types were defined for that DataNode, HDFS can just perform a move locally. 