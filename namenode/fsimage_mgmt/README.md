# HDFS NAMENODE: FSImage Management 

---
__TASKS:__

<!-- toc -->

- [HDFS Metadata](#HDFS-Metadata)
  * [Directories and Locations](#Directories-and-Locations)
- [Replicating the FSImage](#Replicating-the-FSImage)
- [Parse FSImage](#Parse-FSImage)
- [Parse Edit Log](#Parse-Edit-Log)
- [Questions](#Questions)

<!-- tocstop -->


---


## HDFS Metadata

To maintain the distributed file system, the NameNodes use two objects; The _EditLog_ and the _FSImage_.

The FsImage is a mapping of the blocks to files, properties, and such. The EditLog is a running transaction log of changes to the FileSystem. 

__During Startup__, the NameNode takes the FSImage and applies the transcations in the EditLog to create a new FSImage. The EditsLog is then truncated. This is known as __checkpointing__.

### Directories and Locations

The locations for the FSImage and EditLogs vary depending on the setup and role but can be customized. 

__Default Setup w/ NN and Secondary NN__
The NameNode will store EditLog and FSImage files locally in the location(s) defined by: 

```
dfs.namenode.name.dir
```
> This can be a comma-seperated list if the Image should be replicated in multiple places. 
> 

The Secondary NameNode will create checkpoints as well and store them in the location defined by the comma-deliminted property: 

```
dfs.namenode.checkpoint.dir
```

__High-Availability Setups:__
With HA Setups, we have Two NameNodes (One 'Active' and on 'Standby') and a quorum of JournalNodes. JournalNodes receive namespace modifications from the NameNode as client operations occur. 

The Active NameNode will have a lock on the namespace and will stream edits to the JNs.

The Standy NameNode watches the changes to the edit logs and applies it to it's own namespace for failover. 

JournalNodes are defined by the property: 

```
dfs.namenode.shared.edits.dir
```

The value of this property starts with the protocol `qjournal://` and lists the hostnames:ports of nodes. It then defines the namespace as a directory (i.e. /ks2)

The JournalNode daemon reads the edits from local files stored at location(s) defined by:

```
dfs.journalnode.edits.dir
```
Under this directory there will be a sub-folder for the namespace: 

```
dfs.journalnode.edits.dir = /hadoop/hdfs/journal
dfs.namenode.shared.edits.dir = qjournal://node1:8485;node2:8485;node3:8485/ks2

====
JournalNode files located at: 
/hadoop/hdfs/journal/ks2/current
```


__Zookeeper and ZKFC:__
Zookeeper has the job of identifying the Active NameNode in HA Setups. This is held within the znode `/hadoop-ha` 

We can retrieve the metadata from Zookeeper to identify the Active and Standby:

```
get /hadoop-ha/<dfs.nameservices>/ActiveBreadCrumb
> ks2nn1"c346-node2.supportlab.cloudera.com
```
> Identifies c346-node2 as the Active NameNode identified as nn1 belonging to the ks2 nameservice


## Replicating the FSImage

We are going to tell the NameNodes to store their FSImage in multiple places. This can be used for redudency and fault-tolerance by specifying volumes on different disks or mounts. 

__Creating the new directories:__
On both NameNodes: 
```
# mkdir /hadoop/hdfs/namenode2
# chown hdfs:hadoop /hadoop/hdfs/namenode
```

Within Ambari/CM we then update `dfs.namenode.name.dir`:

```
Original Value: 
/hadoop/hdfs/namenode

Updated Value: 
/hadoop/hdfs/namenode,/hadoop/hdfs/namenode2
```
After restart, newly added dirs will receive new edits. 


## Parse FSImage
How are files and directories mapped.

The FSImage file can be consumed and parsed using the Offline Image Viewer (oiv) operation: 

__Using oiv:__
1. First let's make some changes to the namespace: 

```
// Creating Directories w/ permissions
# su hdfs -c "hdfs dfs -mkdir /eng /support /qa /finance /data"
# su hdfs -c "hdfs dfs -chmod 777 /eng /support /qa /finance /data"

// Creating dummy file: 
# head -c 256M </dev/urandom >/tmp/dummy.out


for i in eng support qa finance data; do su hdfs -c "hdfs dfs -put dummy.out /$i/`date +%s`.out"; done
```

2. Next we will checkpoint the namespace: 
```
su hdfs -l -c 'hdfs dfsadmin -safemode enter' && \
su hdfs -l -c 'hdfs dfsadmin -saveNamespace' && \
su hdfs -l -c 'hdfs dfsadmin -safemode leave'
```

3. On the Active NameNode we will capture the FSImage and place it into a separate directories: 

```
cp /hadoop/hdfs/namenode2/current/fsimage_0000000000000004760* /opt/fsimage/
```

4. Use the oiv command to create the XML:
```
# hdfs oiv --processor XML -i fsimage_0000000000000004760 --outputFile fsOut.xml
```

5. (Optional) Use `xmllint` package to clean up output:
```
# xmllint -format fsOut.xml > fsOut-Formatted.xml
```

__Understanding FSImage Output:__
The FSImage XML output features several sections wrapped around an fsimage tag: 

- NameSection
- INodeSection
- INodeReferenceSection
- SnapshotSection
- INodeDirectorySection
- FileUnderConstructionSection
- Snapshot
- Snapshot_diff
- SecretManagerSection
- CacheManagerSection

>Some Section may not appear if certain features are not in use (i.e. Snapshots)

__NameSection:__
Identifies the genstamp, last block ID given, and last transactionId (txid) 

__INodeSection:__
For each file and directory we will have an entry containing: 

| Field              | Use                                                           | Example                 |
| ------------------ | ------------------------------------------------------------- | ----------------------- |
| inode id           | Inode id used for mapping FS Object                           | 16400                   |
| type               | Defines object Type (i.e. FILE,DIRECTORY)                     | FILE                    |
| name               | Name of File                                                  | mapreduce.tar.gz        |
| replication        | Replication Factor of File                                    | 3                       |
| mtime              | Modification time of file                                     | 1623421895824           |
| atime              | Access time of the file                                       | 1623421894237           |
| perferredBlockSize | Value (in bytes) for block size                               | 134217728 bytes (128MB) |
| permission         | HDFS Permissions for the inode                                | `yarn:hadoop:rwx------` |
| blocks             | Nested section identifying inode id of blocks comprising file | See Below               |


```
<blocks>
        <block>
          <id>1073741830</id>
          <genstamp>1006</genstamp>
          <numBytes>134217728</numBytes>
        </block>
        <block>
          <id>1073741831</id>
          <genstamp>1007</genstamp>
          <numBytes>134217728</numBytes>
        </block>
      </blocks>
```

__INodeReferenceSection:__
Used to map files in snapshots that are modified

__SnapshotSection:__
Snapshottable directories
Snapshot count

__INodeDirectorySection:__
Maps the inode ids of directory items to their parents to create a heirarchy

__FileUnderConstructionSection:__
Shows files still being replicated

__Snapshot:__

COMING SOON 

__Snapshot_Diff:__

COMING SOON 


__SecretManagerSection:__
Contains Current ID and tokensequence number

__CacheManagerSection:__
Identifies files set up within caching


## Parse Edit Log
View Sequence of Operations

The Edits log can be parsed using the Offline Edits Viewer (oev) tool. 

1. Let's grab the edits files and put them in a new dir:
```
# cp edits_000000000000000* /opt/edits/
```
2. Use the oev command to format and output the contents
```
for i in `ls -tr edits_000000000000000*`; do hdfs oev -p xml -i $i -o $i.xml; done
```

From here we can open the XML files to see the operations. The EditsLog is broken down by records: 

| Field             | Use                                                              | Example                |
| ----------------- | ---------------------------------------------------------------- | ---------------------- |
| OPCODE            | Operational Code                                                 | OP_MKDIR, OP_SET_OWNER |
| DATA              | Subsection containnig information about the fs object            | See Example below      |
| TXID              | Transaction ID for the operation                                 | 8                      |
| LENGTH            |                                                                  |                        |
| INODEID           | ID for inode                                                     | 16393                  |
| PATH              | Path for opertation                                              | /mr-history            |
| TIMESTAMP         | Epoch time for operation                                         | 1623421892062          |
| PERMISSION_STATUS | Subsection containing permissions such as owner, group, and mode | See Example below      |



EditLog Record Example:: 
```
  <RECORD>
    <OPCODE>OP_MKDIR</OPCODE>
    <DATA>
      <TXID>19</TXID>
      <LENGTH>0</LENGTH>
      <INODEID>16394</INODEID>
      <PATH>/mr-history</PATH>
      <TIMESTAMP>1623421892062</TIMESTAMP>
      <PERMISSION_STATUS>
        <USERNAME>hdfs</USERNAME>
        <GROUPNAME>hdfs</GROUPNAME>
        <MODE>493</MODE>
      </PERMISSION_STATUS>
    </DATA>
  </RECORD>
```

## Questions

When does the edit log roll?
The edit logs has two triggers and will roll whenever a trigger is hit: 

1. When the interval set by `dfs.namenode.checkpoint.period` has passed since last checkpoint
2. When the number of transactions since last checkpoint reaches or exceeds the value within `dfs.namenode.checkpoint.txns`

What is the default Frequency?
The default for the checkpoint interval is 3600s or 1 hour. 
The default number of transactions is after 1,000,000 transactions.