# HDFS Trash

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
- [Working with .Trash](#Working-with-Trash)
- [Questions](#Questions)

<!-- tocstop -->

---


Cluster: 

## Overview

Trash in HDFS is a special location within a user's home folder (i.e. /user/kevin/.Trash/Current). Deleted items will remain in this folder for a set interval (defined by `fs.trash.interval`) at which point the trash directory will go through a checkpointing process. 

During this process the 'Current' directory is renamed to a timestamp. It is only after the second iteration of the interval that the timestamped directory is deleted from HDFS.

When an item is deleted from the namespace, the NameNode will send instructions on the next heartbeat for the Datanodes to delete the blocks that construct the file. 


## Working with .Trash

__Deleting a file:__
```
# hdfs dfs -rm /tmp/shakespeare
21/07/21 16:52:41 INFO fs.TrashPolicyDefault: Moved: 'hdfs://c246-node2.supportlab.cloudera.com:8020/tmp/shakespeare' to trash at: hdfs://c246-node2.supportlab.cloudera.com:8020/user/hdfs/.Trash/Current/tmp/shakespeare
```

__Retrieving a deleted file:__
```
hdfs dfs -mv /user/hdfs/.Trash/Current/tmp/shakespeare /user/hdfs
```


__Skipping Trash:__
```
hdfs dfs -rmr -skipTrash shakespeare
rmr: DEPRECATED: Please use 'rm -r' instead.
Deleted shakespeare
```


## Questions

__Can we recover the data once the trash is cleaned?__
There is a very small window of time after the trash has been cleared where we may be able to recover files. 

In this scenario, we know that a file has been deleted and the interval has passed but the DNs have removed the blocks. To try and recover the blocks we would stop __ALL__ of HDFS. This will prevent new operations and avoid checkpointing. 

In this state, we may be able to use the FSImage and EditsLogs to find and remove the operation which performed the delete from the EditsLogs. When the FSImage is rebuilt, it will not run the operation that removed the file. If atleast one replica of the blocks still exist on a DN, HDFS should work to re-replicate that block.


__Can you keep millions of files in the trash? Will there is be any issues if we keep large files in the trash?__

Trash requires two intervals before the blocks are removed and the space made available on HDFS, larger files will still hold on to that space until that second interval. This may alert admins who are expecting the space to be made available. 

Large numbers of files may also cause issues as the .Trash location is still an HDFS directory and limited by the max number of items in a directory `dfs.namenode.fs-limits.max-directory-items` which has a default value of ~1M but an upper limit of ~6M.




