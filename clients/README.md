# HDFS CLIENTS

---
__TASKS:__

<!-- toc -->

- [WebHDFS](#WebHDFS)
  * [`GET` Request](#GET-Request)
  * [`PUT` Requests:](#PUT-Requests)
  * [`POST` Requests](#POST-Requests)
  * [`DELETE` Requests](#DELETE-Requests)
- [NFS](#NFS)
  * [Setting up NFS](#Setting-up-NFS)
  * [Configuring NFS](#Configuring-NFS)
- [Questions](#Questions)

<!-- tocstop -->

---

Cluster: c246

NameNode: c246-node2.supportlab.cloudera.com
HDFS Policy: HTTP_ONLY



## WebHDFS
Web UI for accessing underlying HDFS files. Uses REST calls so data is transferred via HTTP protocol. 

The WebDHFS call is broken down as: 

Options: `-X GET`
Protocol: `http` or `https`
HostName of Active NameNode
Port: Based on `dfs.namenode.[http/https]-address`
Endpoing: `webhdfs/v1`
Directory: `/tmp`
Operation: `?op=LISTSTATUS`

> Resource: [WebHDFS REST API Operations](https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/WebHDFS.html#Operations)


### `GET` Request

Get Requests are used to retrive a file, list the status of a file or directory or return various attribtues
```
# curl -ikv --negotiate -u: -X GET "http://c246-node2.supportlab.cloudera.com:50070/webhdfs/v1/tmp?op=LISTSTATUS"
```

### `PUT` Requests:

__creating a file:__
The PUT requests can be used to create a file but has two steps: 

1. Create the placeholder
2. Use redirect location to push data to pipeline


Creating a file: 

```
# curl -ikv --negotiate -u: -X PUT "http://c246-node2.supportlab.cloudera.com:50070/webhdfs/v1/tmp/shakespeare?op=CREATE"

// The return willl be a redirect to a datanode (first in line for pipeline)
Location: http://c246-node3.supportlab.cloudera.com:1022/webhdfs/v1/tmp/shakespeare?op=CREATE&delegation=HgAFa2V2aW4Fa2V2aW4AigF6l3lZ8ooBeruF3fIHEhQcs6gJvJzymJfkc1hhR1UO_Y_MZhJXRUJIREZTIGRlbGVnYXRpb24SMTcyLjI1LjM0LjE5Njo4MDIw&namenoderpcaddress=c246-node2.supportlab.cloudera.com:8020&createflag=&createparent=true&overwrite=false
```

The returned information will include a `Location` field with a url pointing to a datanode. This will be the url we use to write the actual file to HDFS: 

```
curl -ikv -X PUT -T shakespeare.txt "http://c246-node3.supportlab.cloudera.com:1022/webhdfs/v1/tmp/shakespeare?op=CREATE&delegation=HgAFa2V2aW4Fa2V2aW4AigF6l3lZ8ooBeruF3fIHEhQcs6gJvJzymJfkc1hhR1UO_Y_MZhJXRUJIREZTIGRlbGVnYXRpb24SMTcyLjI1LjM0LjE5Njo4MDIw&namenoderpcaddress=c246-node2.supportlab.cloudera.com:8020&createflag=&createparent=true&overwrite=false"
```

We do not have to use the `--negotiate` or `-u:` flags within the curl as the NameNode has returned a token within the body which will allow the curl client to access the blocks.


### `POST` Requests

POST requests are used to append to a file. 


```
curl -ikv --negotiate -u: -X POST "http://c246-node2.supportlab.cloudera.com:50070/webhdfs/v1/tmp/shakespeare?op=APPEND"

// We get similar output: 
Location: http://c246-node2.supportlab.cloudera.com:1022/webhdfs/v1/tmp/shakespeare?op=APPEND&delegation=HgAFa2V2aW4Fa2V2aW4AigF6l352xYoBeruK-sUIEhQOPGb3kgMKfJ3cffK0wrY9e8rL9BJXRUJIREZTIGRlbGVnYXRpb24SMTcyLjI1LjM0LjE5Njo4MDIw&namenoderpcaddress=c246-node2.supportlab.cloudera.com:8020
```

We can now use the local file and append to the one within HDFS: 

```
curl -ikv -X POST -T test "http://c246-node2.supportlab.cloudera.com:1022/webhdfs/v1/tmp/shakespeare?op=APPEND&delegation=HgAFa2V2aW4Fa2V2aW4AigF6l352xYoBeruK-sUIEhQOPGb3kgMKfJ3cffK0wrY9e8rL9BJXRUJIREZTIGRlbGVnYXRpb24SMTcyLjI1LjM0LjE5Njo4MDIw&namenoderpcaddress=c246-node2.supportlab.cloudera.com:8020"
```

### `DELETE` Requests
Delete requests allow us to delete a file or a snapshot via the endpoint: 

```
# hdfs dfs -ls /tmp/shakespeare.txt
-rwxr-xr-x   3 kevin hdfs    5458199 2021-07-12 17:40 /tmp/shakespeare.txt

curl -ikv --negotiate -u: -X DELETE "http://c246-node2.supportlab.cloudera.com:50070/webhdfs/v1/tmp/shakespeare.txt?op=DELETE"

// Returns 
{"boolean":true}

# hdfs dfs -ls /tmp/shakespeare.txt
ls: `/tmp/shakespeare.txt': No such file or directory
```


## NFS

NFS allows HDFS to be mounted and accessible as if it were a volume on the local FS.

For secure environments, the keytab file and principal must be defined. Otherwise, a proxyuser config must be setup for the user running the gateway: 

> Resource: [Configure the NFS Gateway](https://docs.cloudera.com/HDPDocuments/HDP3/HDP-3.1.5/data-storage/content/configure_hdfsnfs_gateway.html)


### Setting up NFS
__Installing Packages:__
```
yum install nfs-utils portmap rpcbind -y
```

__Starting Service:__
We first need to start portmap for hadoop. We use the hadoop-daemon script so the process starts in the background: 

```
/usr/hdp/2.6.5.0-292/hadoop/sbin/hadoop-daemon.sh start portmap
```

Now we start nfs3

```
/usr/hdp/2.6.5.0-292/hadoop/sbin/hadoop-daemon.sh start nfs3
```

Lastly, we mount HDFS via NFS: 

```
mount -t nfs -o vers=3,proto=tcp,nolock,sync,rsize=1048576,wsize=1048576 `hostname -f`:/ /hdfs
```

### Configuring NFS


__Mount Options:__

Mountin options control how the local OS views and interacts with the mounted drive. We specify options to control the interaction when clients perform some operation on the mounted FileSystem.

> Resource: [Linux Mount Options](https://man7.org/linux/man-pages/man8/mount.8.html#FILESYSTEM-INDEPENDENT_MOUNT_OPTIONS)

When mounting from a client, we should ensure the `nfs-utils` package is installed and run the following command:

```
# mount -t nfs -o vers=3,proto=tcp,nolock,sync c246-node1.supportlab.cloudera.com:/ /hdfs
```

The above command uses some but not all of the common mount options. 

| Option              | Description                                                  | Example                       |
| ------------------- | ------------------------------------------------------------ | ----------------------------- |
| `vers`              | Version of NFS                                               | `3`                           |
| `proto`             | Connection Protocol                                          | `tcp` or `udp`                |
| `nolock`            | No Lock; disables file locking                               | `nolock`                      |
| `sync`              | All I/O is done synchronously                                | `sync`                        |
| `rsize` and `wsize` | Controls the max number of bytes in a single r/w operation   | `rsize=1048576,wsize=1048576` |
| `soft`              | Soft mount of NFS file system where operations can timeout   | `,soft`                       |
| `hard`              | Hard mount for NFS where a client will wait for an operation | `,hard,`                      |



__Required Services:__
HDFS NFS requires a few linux pages to be available including `nfs-utils`, `portmap`, and `rpcbind`. These as well as others can be used to verify the setup. 

`rpcinfo -p $nfs_server_ip` can be used to validate that the nfs services are all up and running
`showmount -e $nfs_server_ip` will show that the HDFS was properly mounted




__Enabling DEBUG logging:__

JVM variables (such as heap) are controlled within the hadoop-env.sh using the `HADOOP_NFS3_OPTS` directive. 

We can enable DEBUG logging by editing the hdfs-log4j configuration to include the following: 

```
log4j.logger.org.apache.hadoop.hdfs.nfs=DEBUG
// Verbose RPC logging can be enabled by adding the following: 
log4j.logger.org.apache.hadoop.oncrpc=DEBUG
```

With DEBUG enabled, the log file will include multiple references for simple operations. For example, when running a `ls` on the root dir we get the following DBEUG: 

```
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:getattr(333)) - GETATTR for fileHandle: fileId: 16385 namenodeId: -1407630824 client: /172.25.35.2:726
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:access(594)) - NFS ACCESS fileHandle: fileId: 16385 namenodeId: -1407630824 client: /172.25.35.2:726
```
The `GETATTR` is repeated for each item (i.e. directory) as well as any files. 

When reading the contents of a file via cat, we see the stream within the logs: 

```
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:getattr(333)) - GETATTR for fileHandle: fileId: 16470 namenodeId: -1407630824 client: /172.25.35.2:726
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:access(594)) - NFS ACCESS fileHandle: fileId: 16470 namenodeId: -1407630824 client: /172.25.35.2:72
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:handleInternal(2264)) - READ_RPC_CALL_START____-159075570
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:read(743)) - NFS READ fileHandle: fileId: 16470 namenodeId: -1407630824 offset: 0 count: 179 client: /172.25.35.2:7
DEBUG nfs3.RpcProgramNfs3 (RpcProgramNfs3.java:handleInternal(2268)) - READ_RPC_CALL_END______-1590755704
```

This shows us not only the initial retrieval of attributes but also the rpc stream as data is transferred. 

For an added level, the following occurs when a user tries to retrieve a file within an encrypted zone: 

```
DEBUG nfs3.WriteManager (WriteManager.java:commitBeforeRead(226)) - No opened stream for fileId: fileId: 16467 namenodeId: -1407630824 commitOffset=1755. Return success in this case.
WARN  nfs3.DFSClientCache (DFSClientCache.java:getDfsInputStream(352)) - Failed to create DFSInputStream for user:root Cause:java.util.concurrent.ExecutionException: org.apache.hadoop.security.authorize.AuthorizationException: User:hdfs not allowed to do 'DECRYPT_EEK' on 'encr1'
```

One important note here is that the NFS client does not return any failure: 

```
# cat /hdfs/encrypted/hosts
[root@c246-node4 /]#
```



## Questions


__Limitations in NFS gateway?__
User ID number for locally available users must match across clients. 
Depending on hard vs soft mount we may see performance impacts if the NFS gateway becomes unresponsive
Does not audit 

__Does NFS gateway honor the ranger?__
No, NFS only recognizes the posix permissions which were translated: 

```
// Our test file: 
$ ls -l /hdfs/auth/rejected/core-site.xml
-rwx------ 1 hdfs hdfs 5410 Jul 12 19:06 /hdfs/auth/rejected/core-site.xml

// As local thor users: 
$ cat /hdfs/auth/rejected/core-site.xml
cat: /hdfs/auth/rejected/core-site.xml: Permission denied

// With thor kerberos principal
$ hdfs dfs -cat /auth/rejected/core-site.xml
<FILE CONTENT>; success
```


