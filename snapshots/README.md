# HDFS Snapshots

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
- [HDFS Snapshots](#HDFS-Snapshots)
  * [Enabling Snapshots](#Enabling-Snapshots)
  * [Creating Snapshots](#Creating-Snapshots)
  * [Deleting Snapshots](#Deleting-Snapshots)
  * [Renaming Snapshots](#Renaming-Snapshots)
- [Known Issues](#Known-Issues)
  * [TSB-2020-432](#TSB-2020-432)
- [Troubleshooting](#Troubleshooting)
  * [Recreating TSB-2020-432](#Recreating-TSB-2020-432)

<!-- tocstop -->

---

## Overview 
Snapshots are point in time copies of the files. They do not result in additional blocks or copying. Modifications to Snapshots are recorded in reverse chronological order so snapshots data is created by rolling back modifications from current data

Snapshottable directories will encompass all child directories. This also prevents a directory from being snapshottable if a parent is already snapshotted

## HDFS Snapshots 

### Enabling Snapshots
To enable snapshots on a directory, an administrator needs to first make it snapshottable: 

```
// Let's first make a few directories
# hdfs dfs -mkdir /ss/s1 /ss/s2 /ss/s3 

// Now we will make some sub-folder content
# hdfs dfs -put shakespeare.txt /ss/s1

// Now, let's allow snapshots on the dir
# hdfs dfsadmin -allowSnapshot /ss/s1
Allowing snaphot on /ss/s1 succeeded
```

If we need to look for all snapshottable dirs we can use the `hdfs lsSnapshottableDir` command: 

```
# hdfs lsSnapshottableDir
drwxr-xr-x 0 hdfs hdfs 0 2021-07-19 15:22 1 65536 /ss/s1
```
What we have listed is the 

### Creating Snapshots

With content in the directory, we can then create a snapshot of the directory using the `-createSnapshot` flag followed by the `<PATH>` and snapshot name:

```
# hdfs dfs -createSnapshot /ss/s1 s1_snap_$(date +%s)
Created snapshot /ss/s1/.snapshot/s1_snap_1626708156
```

We know that a snapshot has been created because the files will be kept in a hidden folder within the snapshotted dir: 

```
# hdfs dfs -ls /ss/s1/.snapshot
Found 1 items
drwxr-xr-x   - hdfs hdfs          0 2021-07-19 15:22 /ss/s1/.snapshot/s1_snap_1626708156
```


### Deleting Snapshots
We can remove the point in time snapshots by runnign using the `-deleteSnapshot` command:

```
# hdfs dfs -deleteSnapshot /ss/s1 s1_snap_1626708156
# hdfs dfs -ls /ss/s1/.snapshot
```
> Note: There is no return because .snapshot dir is only present when there are contents. 

### Renaming Snapshots
We can rename a snapshot using the `-renameSnapshot` flag followed by the `<PATH>`, old name, and new name


1. Creating a new Snapshot
```
 hdfs dfs -createSnapshot /ss/s1 s1_snap_$(date +%s)
Created snapshot /ss/s1/.snapshot/s1_snap_1626709495
```

2. Renaming snapshot
```
# hdfs dfs -renameSnapshot /ss/s1 s1_snap_1626709495 s1_snap_rename1
--
# hdfs dfs -ls /ss/s1/.snapshot
Found 1 items
... /ss/s1/.snapshot/s1_snap_rename1
```


__Comparing Snapshots:__

Multiple snapshots can be compared for their differences:


1. Let's make a change to a file in a snapped dir:
```
# hdfs dfs -appendToFile test /ss/s1/shakespeare.txt
```

2. Let's verify the change: 
```
# hdfs dfs -cat /ss/s1/shakespeare.txt | less
```

3. With something changed we can create a new snapshot:
```
# hdfs dfs -createSnapshot /ss/s1 s1_snap_$(date +%s)
Created snapshot /ss/s1/.snapshot/s1_snap_1626710045
```

4. Now we can compare the difference 
```
# hdfs snapshotDiff /ss/s1 s1_snap_rename1 s1_snap_1626710045

Difference between snapshot s1_snap_rename1 and snapshot s1_snap_1626710045 under directory /ss/s1:
M   ./shakespeare.txt
```

The line:

`M ./shakespeare.txt` 

indicates that the file 'shakespeare.txt' was Modified.

In addition to listing modified files, the snapshot diff toll can print any number of results: 

| Identified | Meaning           |
| ---------- | ----------------- |
| +          | File/dir created  |
| -          | File/dir deleted  |
| M          | file/dir modified |
| R          | File/dir renamed  | 



## Known Issues


### TSB-2020-432

TSB-2020-432 Identified an area for potential data loss when using snapshot. This occurs when a series of operations are performed culimating in a removal of a snapshot. Deleting a snapshot results in files being deleted unexpectedly. 

__JIRAS:__
TSB-2020-432 is considered if a customer is running a build which includes HDFS-13101 but not HDFS-15313. This can be avoided by not applying HDFS-13101 but can be resolved by ensuring all of the following JIRAs are applied: 

`HDFS-13101` was created to resolve an issue with FSImages becoming corrupt due to snapshots

`HDFS-15313` was aimed at preventing inodes from being deleted when snapshots are deleted.

`HDFS-15012` resolves NameNode failing to parse EditLogs after applying HDFS-13101 due to snapshots failure. This produces a log with a similar error: 

```
ERROR org.apache.hadoop.hdfs.server.namenode.FSEditLogLoader: Encountered exception on operation DeleteSnapshotOp [snapshotRoot=/path/to/hdfs/file, snapshotName=distcp-3479-31-old, RpcClientId=b16a6cb5-bdbb-45ae-9f9a-f7dc57931f37, Rpc
CallId=1]
java.lang.AssertionError: Element already exists: element=partition_isactive=true, DELETED=[partition_isactive=true]
```

`HDFS-15276` updated code to resolve a failure relating to inode state changes. This relates to snapshots as inodes are the identifiers for files and linked within the FSImage by those inode identifiers. 

> Resource: [TSB-2020-432](https://my.cloudera.com/knowledge/TSB-2020-432--Potential-HDFS-data-loss-due-to-snapshot-usage?id=299898) 




## Troubleshooting


### Recreating TSB-2020-432

1. Creating Directories:

```
# hdfs dfs -mkdir /dl
# hdfs dfs -mkdir /dl/d1 /dl/d2
```

2. Make `/ss/s2` snapshottable: 

```
# hdfs dfsadmin -allowSnapshot /dl
Allowing snaphot on /dl succeeded
```

3. Create snapshot s0
```
# hdfs dfs -createSnapshot /dl s0
Created snapshot /dl/.snapshot/s0
```

4. Create a new sub-directory

```
# hdfs dfs -mkdir /dl/d2/da
```

5. Create a new snapshot `s1`:
```
hdfs dfs -createSnapshot /dl s1
Created snapshot /dl/.snapshot/s1
```
---
> Note: 
> 
At this point, the dir strucutre is the following: 
```
/dl
--> /dl1
--> /dl2
-----> /da
```
Snapshot `s0` was captured with only the `/d1` and `/d2` directories created. 

Snapshot `s1` was created after `/dl/d2/da` directory was created

We can see this within the `snapshotDiff`:

```
hdfs snapshotDiff /dl s0 s1
Difference between snapshot s0 and snapshot s1 under directory /dl:
M   ./d2
+   ./d2/da

```
---

6. Rename `/da` moving it to `/dl/d1/da`

```
# hdfs dfs -mv /dl/d2/da /dl/d1/da
```

7. Create a new snapshot `s2`

```
# hdfs dfs -createSnapshot /dl s2
Created snapshot /dl/.snapshot/s2
```

8. Add a file to `/dl/d1/da`:


```
# hdfs dfs -put shakespeare.txt /dl/d1/da
# hdfs dfs -ls /dl/d1/da
... /dl/d1/da/shakespeare.txt
```

9. Create snapshot `s3`:

```
# hdfs dfs -createSnapshot /dl s3
```

10. Delete snapshot `s1`:

```
# hdfs dfs -deleteSnapshot /dl s1
```

11. Delete snapshot `s3`

```
# hdfs dfs -deleteSnapshot /dl s3
```

File `/dl/d1/da/shakespeare.txt` will be deleted. 





