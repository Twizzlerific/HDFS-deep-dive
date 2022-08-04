# HDFS Distcp

Setup two clusters and show the data copy betweens:

---
__TASKS:__

<!-- toc -->

- [Environment](#Environment)
- [Overview](#Overview)
- [Distcp in Action](#Distcp-in-Action)
  * [Distcp in a secure cluster](#Distcp-in-a-secure-cluster)
  * [Distcp using webhdfs](#Distcp-using-webhdfs)
  * [Distcp with -update and -overwrite](#Distcp-with--update-and--overwrite)
  * [Distcp sync option](#Distcp-sync-option)
  * [Distcp non-encryption zone to encyption zone.](#Distcp-non-encryption-zone-to-encyption-zone)
  * [Distcp encryption zone to encryption zone.](#Distcp-encryption-zone-to-encryption-zone)
  * [Distcp between hadoop 1.x to hadoop 2.x.](#Distcp-between-hadoop-1x-to-hadoop-2x)
  * [Distcp between un-secure to secure cluster.](#Distcp-between-un-secure-to-secure-cluster)

<!-- tocstop -->

---

# Environment
Clusters: 

__c246:__
HDP 2.6.5.0-292 (Hadoop 1.x)
SSSD Group Mapping
Ranger KMS
dfs.http.policy = HTTP_ONLY
dfs.internal.nameservices = ks1

__c346:__
HDP 3.1.0.0-78 (Hadoop 2.x)
HadoopGroupMapping 
dfs.http.policy = HTTPS_AND_HTTP
dfs.internal.nameservices = ks2


__Files:__ 
[c246 hdfs-site.xml](./c246_hdfs-site.xml)
[c346 hdfs-site.xml](./c346_hdfs-site.xml)
[Distcp Config hdfs-site.xml](./distcp_hdfs-site.xml)


# Overview 

# Distcp in Action

__Setup:__
For this activity, we will set up some directories and files within HDFS on both clusters: 

```
# hdfs dfs -mkdir /distcp/snap /distcp/update /distcp/encr /distcp/webhdfs
# echo $(date) > update
# cat update
Thu Aug 5 15:16:18 UTC 2021
```

## Distcp in a secure cluster



__Auth-to-local-rules:__
When running distcp in a secure environment where Kerberos is enabled requires ensuring that the principal resolve to the same names. This can be refined by adjusting the `auth_to_local` rules : 

```
// Ambari > HDFS > Advanced core-site > hadoop.security.auth_to_local
RULE:[1:$1@$0](ambari-qa-c246@KS.COM)s/.*/ambari-qa/
RULE:[1:$1@$0](hdfs-c246@KS.COM)s/.*/hdfs/
RULE:[1:$1@$0](ambari-qa-c346@SUPPORTLAB.CLOUDERA.COM)s/.*/ambari-qa/
RULE:[1:$1@$0](hdfs-c346@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[1:$1@$0](yarn-ats-c346@SUPPORTLAB.CLOUDERA.COM)s/.*/yarn-ats/
RULE:[1:$1@$0](.*@KS.COM)s/@.*//
RULE:[1:$1@$0](.*@SUPPORTLAB.CLOUDERA.COM)s/@.*//
RULE:[2:$1@$0](beacon@SUPPORTLAB.CLOUDERA.COM)s/.*/beacon/
RULE:[2:$1@$0](dn@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](jhs@SUPPORTLAB.CLOUDERA.COM)s/.*/mapred/
RULE:[2:$1@$0](jn@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](knox@SUPPORTLAB.CLOUDERA.COM)s/.*/knox/
RULE:[2:$1@$0](nfs@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](nm@SUPPORTLAB.CLOUDERA.COM)s/.*/yarn/
RULE:[2:$1@$0](nn@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](rm@SUPPORTLAB.CLOUDERA.COM)s/.*/yarn/
RULE:[2:$1@$0](yarn@SUPPORTLAB.CLOUDERA.COM)s/.*/yarn/
RULE:[2:$1@$0](yarn-ats-hbase@SUPPORTLAB.CLOUDERA.COM)s/.*/yarn-ats/
RULE:[2:$1@$0](beacon@KS.COM)s/.*/beacon/
RULE:[2:$1@$0](dn@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](jhs@KS.COM)s/.*/mapred/
RULE:[2:$1@$0](jn@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](knox@KS.COM)s/.*/knox/
RULE:[2:$1@$0](nfs@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](nm@KS.COM)s/.*/yarn/
RULE:[2:$1@$0](nn@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](rangeradmin@KS.COM)s/.*/ranger/
RULE:[2:$1@$0](rangerkms@KS.COM)s/.*/keyadmin/
RULE:[2:$1@$0](rangertagsync@KS.COM)s/.*/rangertagsync/
RULE:[2:$1@$0](rangerusersync@KS.COM)s/.*/rangerusersync/
RULE:[2:$1@$0](rm@KS.COM)s/.*/yarn/
RULE:[2:$1@$0](yarn@KS.COM)s/.*/yarn/
RULE:[2:$1@$0](nn/.*@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](dn/.*@SUPPORTLAB.CLOUDERA.COM)s/.*/hdfs/
RULE:[2:$1@$0](nn/.*@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](dn/.*@KS.COM)s/.*/hdfs/
RULE:[2:$1@$0](.*@SUPPORTLAB.CLOUDERA.COM)s/@.*//
RULE:[2:$1@$0](.*@KS.COM)s/@.*//
DEFAULT
```
> Resource: [Auth to Local Rules Syntax](https://community.cloudera.com/t5/Community-Articles/Auth-to-local-Rules-Syntax/ta-p/245316)

Rules can be verified locally on a node by updating the core-site.xml and then running the `HadoopKerberosName` command against the principal: 

```
// Running from Node where primary Realm is KS.COM
# hadoop org.apache.hadoop.security.HadoopKerberosName nn/c346-node2.supportlab.cloudera.com@SUPPORTLAB.CLOUDERA.COM
Name: nn/c346-node2.supportlab.cloudera.com@SUPPORTLAB.CLOUDERA.COM to hdfs
```

> __Note:__ Auth_to_local Rules are run from top to bottom until a rule applies. If we have specific designations (i.e. nn/ --> hdfs) make sure they are listed before the wildcard (*@REALM.COM) rules

## Distcp using webhdfs

Distcp can be run against webhdfs by changing the protocol from `hdfs://` to `webhdfs://`. This eliminates the need to add `/webhdfs/v1` to the url. 

```
// Cluster c246 
# hadoop --config /opt/distcpConf distcp hdfs://ks1/distcp/update/update webhdfs://c346-node2.supportlab.cloudera.com:50070/distcp/webhdfs/update


// Cluster c346
# hdfs dfs -ls /distcp/webhdfs
Found 1 items
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:27 /distcp/webhdfs/update
# hdfs dfs -cat /distcp/webhdfs/update
Thu Aug 5 15:16:18 UTC 2021
```


## Distcp with -update and -overwrite

Let's create a file with a timestamp and add it to hdfs multiple times: 

```
# for i in {1..10}; do hdfs dfs -put update /distcp/update/update_$i; done
# hdfs dfs -ls /distcp/update
Found 11 items
/distcp/update/update
/distcp/update/update_1
/distcp/update/update_10
/distcp/update/update_2
/distcp/update/update_3
/distcp/update/update_4
/distcp/update/update_5
/distcp/update/update_6
/distcp/update/update_7
/distcp/update/update_8
/distcp/update/update_9

// Destination Cluster: c346
# hdfs dfs -ls /distcp/update
// No Items 
# hdfs dfs -cp /distcp/webhdfs/update /distcp/update
Found 1 items
-rw-r--r--   3 hdfs hdfs         28 2021-08-05 15:35 /distcp/update/update
```

With the files in place we can run distcp with the `-update` flag to copy over files which do not exist at the destination. 

```
# hadoop --config /opt/distcpConf distcp -update hdfs://ks1/distcp/update/ hdfs://ks2/distcp/update
# hdfs dfs -ls /distcp/update
Found 11 items
-rw-r--r--   3 hdfs hdfs         28 2021-08-05 15:35 /distcp/update/update
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_1
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_10
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_2
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_3
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_4
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_5
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_6
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_7
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_8
-rw-r--r--   2 hdfs hdfs         28 2021-08-05 15:36 /distcp/update/update_9
```
From the timestamps, we can see that the `update` file was not changed by the command because it already exists. 

Now, we can make a few changes to the update file and then append to the file on HDFS: 

```
# echo $(date) > update
[root@c246-node1 opt]# cat update
Thu Aug 5 15:38:30 UTC 2021
hdfs dfs -appendToFile update /distcp/update/update
# hdfs dfs -cat /distcp/update/update
Thu Aug 5 15:16:18 UTC 2021
Thu Aug 5 15:38:30 UTC 2021
```

Let's run the command again now that the update file has changed: 

```
# hadoop --config /opt/distcpConf distcp -update hdfs://ks1/distcp/update/ hdfs://ks2/distcp/update

// Files show up and we can check update
# hdfs dfs -cat /distcp/update/update
Thu Aug 5 15:16:18 UTC 2021
Thu Aug 5 15:38:30 UTC 2021
```

The `-overwrite` can be used to forcefully replace a file. 

## Distcp sync option

Distcp offers the ability to sync HDFS Snapshots between clusters. We use the `-update` and `-diff` to denote that we are comparing the snapshots and updating with the differences




## Distcp non-encryption zone to encyption zone.

Since encryption zones use special keys with access defind in Ranger, users who do not have access to the key will not be able to decrypt the data resulting in an error:

```
Caused by: org.apache.hadoop.security.authorize.AuthorizationException: User:iron_man not allowed to do 'DECRYPT_EEK' on 'encr1'
```

Running command with `kevin` principal which has access to key.

```
Caused by: java.io.IOException: Check-sum mismatch between hdfs://ks1/encrypted/hosts and hdfs://ks1/notencrypted/.distcp.tmp.attempt_1628092424571_0003_m_000000_0.
```
Distcp notices that the checksum for the data has changed. We run using the additional flags `-skipcrccheck -update` which will skip running the checksum check. The `-update` flag is a requirement to skip the check but overwrites a file if it exists. 

```
hadoop distcp -skipcrccheck -update /encrypted/hosts /notencrypted
```


## Distcp encryption zone to encryption zone.

We have two paths with different encryption keys: 

```
$ hdfs crypto -listZones
/encrypted   encr1
/encrypted2  encr2
```

To move between encryption Zones we would need to ensure that the user has accecss to the keys of both paths. We can run the following command but it will fail: 

```
# hadoop distcp /encrypted/hosts /encrypted2/hosts
...
Caused by: java.io.IOException: Couldn't run retriable-command: Copying hdfs://ks1/encrypted/hosts to hdfs://ks1/encrypted2/hosts
    at org.apache.hadoop.tools.util.RetriableCommand.execute(RetriableCommand.java:101)
    at org.apache.hadoop.tools.mapred.CopyMapper.copyFileWithRetry(CopyMapper.java:296)
    ... 10 more
Caused by: java.io.IOException: Check-sum mismatch between hdfs://ks1/encrypted/hosts and hdfs://ks1/encrypted2/.distcp.tmp.attempt_1628103125527_0001_m_000000_1.
```

Similarly, we have to use the crc flag to skip checksums: 

```
# hadoop distcp -skipcrccheck -update /encrypted/hosts /encrypted2/hosts
// Success

# hdfs dfs -ls /encrypted2
-rw-r--r--   2 encr hdfs       1755 2021-08-05 16:43 /encrypted2/hosts
```

## Distcp between hadoop 1.x to hadoop 2.x.

```
# hadoop --config /opt/distcpConf distcp -Dmapreduce.job.hdfs-servers.token-renewal.exclude=ks2 -Dipc.client.fallback-to-simple-auth-allowed=true -Ddfs.namenode.kerberos.principal.pattern=* hdfs://ks1/tmp/shakespeare1 hdfs://ks2/tmp

# hadoop --config /opt/distcpConf fs -ls hdfs://ks2/tmp/shakespeare1
-rw-r--r--   2 iron_man  hdfs                 153 2021-08-04 17:02 hdfs://ks2/tmp/shakespeare1
```


## Distcp between un-secure to secure cluster.

_With Kerberos disabled on Cluster c346_

When performing distcp between a secure and non-secure cluster we can use `webhdfs`:

```
// Secure to non-secure
# hadoop --config /opt/distcpConf distcp -D ipc.client.fallback-to-simple-auth-allowed=true /tmp/hosts webhdfs://ks2/tmp/hosts2353
```

```
// On secure cluster within custom core-site.xml
ipc.client.fallback-to-simple-auth-allowed
```
