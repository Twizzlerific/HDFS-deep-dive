# HDFS FSNamespace Management

__Find out which lock is held?__

From the hdfs-audit.log we can see the client come in and the type of lease: 

```
2021-08-09 18:39:37,748 INFO FSNamesystem.audit: allowed=true   ugi=root (auth:SIMPLE)  ip=/172.25.36.207   cmd=getfileinfosrc=/tmp/fileLease_1628533442    dst=null    perm=null   proto=rpc   callerContext=CLI
2021-08-09 18:39:37,823 INFO FSNamesystem.audit: allowed=true   ugi=root (auth:SIMPLE)  ip=/172.25.36.207   cmd=open    src=/tmp/fileLease_1628533442   dst=null    perm=null   proto=rpc   callerContext=CLI
```

The `cmd=open` tells us this is a read lock as a write lock would show `cmd=create` or `cmd=append`.


__How does namespace be manged with locks?__

Locks manage the namespace by restricting access to an HDFS path ending with a file. The Namespace locks this path and affords the lease to a specific client. During the span of the lease, no other client can write to that path but multiple can read from the path.





