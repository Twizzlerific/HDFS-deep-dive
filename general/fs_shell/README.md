# HDFS FS Shell

---


## `hdfs dfsadmin` Commands


| Command | Description | Example |
| ------- | ----------- | ------- |
|-report|Generate a basic report of HDFS filesystem info|`hdfs dfsadmin -report`|
|-safemode|Locks FS preventing changes|`hdfs dfsadmin -safemode enter` & `hdfs dfsadmin -safemode leave`|
|-saveNamespace|Force a Checkpoint|` hdfs dfsadmin -saveNamespace`|
|-rollEdits|Force EditLogs to roll on activeNN|` hdfs dfsadmin -rollEdits`|
|-restoreFailedStorage|Toggle for automatic restore on storage availability|`hdfs dfsadmin -restoreFailedStorage`|
|-refreshNodes|Instructs NameNode to check include/exclude file again|`hdfs dfsadmin -refreshNodes`|
|-setQuota|Sets the limit on the number of named items within a directory (includes .Trash)|`hdfs dfsadmin -setQuota 2 /limit`|
|-clrQuota|Remve the name limit on a dir|`hdfs dfsadmin -clrQuota /limit`|
|-setSpaceQuota|hard limit on number of bytes a dir can contain|`hdfs dfsadmin -setSpaceQuota 10 /limit`|
|-clrSpaceQuota|Removed hard limit on bytes|`hdfs dfsadmin -clrSpaceQuota /limit`|
|-finalizeUpgrade|Finalize an upgrade forcing DN to delete previous version dirs|`hdfs dfsadmin -finalizeUpgrade`|
|-rollingUpgrade|XXX|`hdfs dfsadmin -rollingUpgrade`|
|-refreshServiceAcl|Reload Serice ACL policy files|`hdfs dfsadmin -refreshServiceAcl`|
|-refreshUserToGroupsMappings|Refresh HDFS user/group mapping|`hdfs dfsadmin -refreshUserToGroupsMappings`|
|-refreshSuperUserGroupsConfiguration|Refresh mapping of superuser and proxy mappings|`hdfs dfsadmin -refreshSuperUserGroupsConfiguration`|
|-refreshCallQueue|XXX|`hdfs dfsadmin -refreshCallQueue`|
|-refresh|Triggers runtime refresh of a property|`hdfs dfsadmin -refresh c246-node4.supportlab.cloudera.com:8010 dfs.datanode.data.dir`|
|-reconfig|Reconfigure a property|`hdfs dfsadmin -reconfig datanode c246-node4.supportlab.cloudera.com:8010 properties`|
|-printTopology|Prints rack topology of datanodes|`hdfs dfsadmin -printTopology`|
|-refreshNamenodes|Forces a datanode to reload cnfig files and stop serving a removed BP|`hdfs dfsadmin -refreshNamenodes c246-node4.supportlab.cloudera.com:8010`|
|-deleteBlockPool|Deletes a BP dir not being served by dn|`hdfs dfsadmin -deleteBlockPool c246-node4.supportlab.cloudera.com:8010 239823342`|
|-setBalancerBandwidth|Changes property for balancer bandwidth|`hdfs dfsadmin -setBalancerBandwidth 100000`|
|-fetchImage|Writes FSImage to file on localFS|`hdfs dfsadmin -fetchImage /opt/imageBAK`|
|-allowSnapshot|Allow a dirctory to be snapshottable|`hdfs dfsadmin -allowSnapshot /ss`|
|-disallowSnapshot|prevent or remove a directories snapshot ability|`hdfs dfsadmin -disallowSnapshot /ss`|
|-shutdownDatanode|Send a command to shutdown a datanode|`hdfs dfsadmin -shutdownDatanode c146-node4.supportlab.cloudera.com:8010`|
|-getDatanodeInfo|Retrieve version information from DN|`hdfs dfsadmin -getDatanodeInfo c246-node4.supportlab.cloudera.com:8010`|
|-metasave|Generate metadata information. File palced in NN Log dir|`hdfs dfsadmin -metasave meta_$(date +%s)`|
|-triggerBlockReport|Force a DN to send in a block report|`hdfs dfsadmin -triggerBlockReport c146-node4.supportlab.cloudera.com:8010`|




### Failures:



__Quotas:__

Name Quota exceeded:
```
hdfs dfs -mkdir /limit/1 /limit/2
mkdir: The NameSpace quota (directories and files) of directory /limit is exceeded: quota=2 file count=3
```

Space Quota exceeded:
```
hdfs dfs -cp /tmp/hosts /limit
21/07/21 17:34:42 WARN hdfs.DFSClient: DataStreamer Exception
org.apache.hadoop.hdfs.protocol.DSQuotaExceededException: The DiskSpace quota of /limit is exceeded: quota = 10 B = 10 B but diskspace consumed = 402653184 B = 384 MB
```

_