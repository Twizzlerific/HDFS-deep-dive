# HDFS NameNode: File Leases

---
__TASKS:__

<!-- toc -->

  * [File Leases w/ HDFS API](#File-Leases-w-HDFS-API)
    + [Create a File using HDFS API](#Create-a-File-using-HDFS-API)
    + [Find the Lease ID and Client details](#Find-the-Lease-ID-and-Client-details)
- [Questions:](#Questions)

<!-- tocstop -->

---

## File Leases w/ HDFS API

### Create a File using HDFS API

Script can be found here: [fileLease.java](./fileLease.java)

Will initialize a connection to the NameNode and create a file but not close the FileSystem connection. This will leave the final block as 


```
DIR* completeFile: /tmp/fileLease_1625764060 is closed by DFSClient_NONMAPREDUCE_-226958544_1

==> hdfs-audit.log <==
2021-08-09 18:10:48,881 INFO FSNamesystem.audit: allowed=true   ugi=root (auth:SIMPLE)  ip=/172.25.36.207   cmd=create  src=/tmp/fileLease_1628532646   dst=null    perm=root:hdfs:rw-r--r--    proto=rpc
```

### Find the Lease ID and Client details

- Find leaseId and client details 
```
Client Host             Client Name             Open File Path
172.25.36.207           DFSClient_NONMAPREDUCE_2127046050_1 /tmp/fileLease_1628532772
```

- Find Operation granted to lease



# Questions: 
__Which operation(read/write) the lease granted?__
This was a write 

__What happen if client away without closing file?__
If a client does not check in, the lease will be recovered after an interval at which point other clients can then write to the file. Otherwise, if a lease is still held, we may get this error: 

```
appendToFile: Failed to APPEND_FILE /tmp/fileLease_1628533442 for DFSClient_NONMAPREDUCE_1139760686_1 on 172.25.36.207 because this file lease is currently owned by DFSClient_NONMAPREDUCE_-1375153378_1 on 172.25.36.207
```


__Default lease expiry?__
1 minute for soft lease
60 min for hard lease

__How do you find whether lease expired or holding any active lease?__

```
#  hdfs dfsadmin -listOpenFiles
```

__How to close lease manually?__
Using the hdfs debug tool, we can recover the lease

```
// Client
$ hdfs debug recoverLease -path /tmp/fileLease_1628533127

// NN Logs
2021-08-09 18:19:22,919 INFO  namenode.FSNamesystem (FSNamesystem.java:recoverLeaseInternal(2573)) - recoverLease: [Lease.  Holder: DFSClient_NONMAPREDUCE_1347606148_1, pending creates: 1], src=/tmp/fileLease_1628533127 from client DFSClient_NONMAPREDUCE_1347606148_1
2021-08-09 18:19:22,919 INFO  namenode.FSNamesystem (FSNamesystem.java:internalReleaseLease(3315)) - Recovering [Lease.  Holder: DFSClient_NONMAPREDUCE_1347606148_1, pending creates: 1], src=/tmp/fileLease_1628533127
2021-08-09 18:19:22,920 WARN  hdfs.StateChange (FSNamesystem.java:internalReleaseLease(3338)) - BLOCK* internalReleaseLease: All existing blocks are COMPLETE, lease removed, file /tmp/fileLease_1628533127 closed.
```

__What commands used to troubleshoot file recovery operation? on the lease issue.__
We can use the open files command to find the open lease and recover lease to stop the lease so that a client can move forward to recovery


__Can client hold multiple file lease?__
Yes a client can hold a read and write lease simultaneously

__Who is responsible for managing file lease?__
NameNode LeaseManager service manages file leases. If a lease hits it's limit, the LeaseManager will perform recovery to remove the lock.

