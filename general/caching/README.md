# HDFS Centralized Cache Management

---
__TASKS:__

<!-- toc -->

- [Overview](#Overview)
- [Using Caching](#Using-Caching)
  * [Managing Cache Pools](#Managing-Cache-Pools)
  * [Managing Directives](#Managing-Directives)
  * [Troubleshooting](#Troubleshooting)
- [Questions:](#Questions)

<!-- tocstop -->

---

## Overview 

Caching in HDFS means identifying paths and having the blocks that make up the contents of that path stored on off-heap caches.

__Why?:__
Cached data can be used for frequently used data which is use frequently. This is ever important when the size of the data is larger than main memory.


__How:__
DataNode caches are managed by the NameNode so that when clients make requests it can direct to those block locations. During heartbeats, DataNodes will send back a report of cached blocks. NameNode will keep this information in fsimage under the CacheManagerSection.

Admins can add or remove paths (also known as _directives_) to the cache pool. Limits can also be placed on the amount of data stored within the cache as well as _time-to-live_ (TTL) limits which give a time limit on a directive in a cache pool.

Performance improvements for reads increases if the client is co-located with the cached replicas. There is also the option to define the number of replicas pinned in memory.

__Where Might we use caching?:__
A prime use case if when a small 'fact' Hive table is needed for joins such as identifying an issue id. 


## Using Caching

__Set up:__
Centralized Caching requires setting the following within the hdfs-site to define the max memory that can be used for caching blocks in memory.:

`dfs.datanode.max.locked.memory`

> Note: This memory is off-heap so additional things such as `ulimit` and OS memory need to be considered. 


__CLI:__
We can interact with the Centralized Cache Management feature using the `cacheadmin` tool from the CLI: 

### Managing Cache Pools

__Adding Pools:__
Creating Cache pools:

```
# hdfs cacheadmin -addPool pool1 -owner kevin -group hdfs -mode 0755 -maxTtl 3d
Successfully added cache pool pool1.
```

The command takes several arguments: 

| arg                   | Description                   |
| --------------------- | ----------------------------- |
| \<name>               | name of the cache pool        |
| `-owner` \<ownerName> | owner of the cache pool       |
| `-group` \<groupName> | group of the cache pool       |
| `-mode` \<MODE>       | Numeric value for permissions |
| `-limit` \<LIMIT>     | Max number of bytes for pool  |
| `-maxTtl` \<TTL>      | Time-to-live for directives added                              |


__Listing Pools:__
List out all available cache pools: 

```
# hdfs cacheadmin -listPools
Found 1 result.
NAME   OWNER  GROUP  MODE            LIMIT            MAXTTL
pool1  kevin  hdfs   rwxr-xr-x   unlimited  003:00:00:00.000
```


__Editing Pools:__
Modify a pool's configuration 

```
# hdfs cacheadmin -modifyPool pool1 -mode 0770
Successfully modified cache pool pool1 to have mode rwxrwx---
```

We can then use the `listPools` command to verify the change: 

```
hdfs cacheadmin -listPools
Found 1 result.
NAME   OWNER  GROUP  MODE            LIMIT            MAXTTL
pool1  kevin  hdfs   rwxrwx---   unlimited  003:00:00:00.000
```

__Removing Pools:__

```
# hdfs cacheadmin -addPool deleteMe
Successfully added cache pool deleteMe.

# hdfs cacheadmin -removePool deleteMe
Successfully removed cache pool deleteMe.
```

### Managing Directives

Directives are the actual paths that we combine to create pools. Simiarly we can manage them using the `cacheadmin` command from the CLI.  


__Listing Directive:__
Listing all of the directives:

```
hdfs cacheadmin -listDirectives
Found 0 entries
```

This can also be filtered down using some additional flags:

| flag                | definition                         |
| ------------------- | ---------------------------------- |
| `-path` \<PATH>     | Filter down list based on path     |
| `-pool` \<poolName> | List only directives for that pool |
| `-stats`            | Show path-based cache stats        |                    |                                    |


Let's create some directories and populate them with files
```
# for i in {1..10}; do hdfs dfs -mkdir /cache/$i; done
# --
# for i in {1..10}; do hdfs dfs -put 5Blocks2.img /cache/$i/5Blocks2.img.$i; done
```


__Adding Directive:__
Adding a path to a directory of file that will be cached.

```
# hdfs cacheadmin -addDirective -path /cache/1 -pool pool1 -replication 1 -ttl 3d
Added cache directive 1
```

The flags are similar to the ones listed above but include a new flag for replication which controls the number of block replicas which will be cached across the cluster: 

| flag                          | definition |
| ----------------------------- | ---------- |
| `-replication` \<replication> | The cache replication factor           |


We can now view the directive and relevant info: 

```
# hdfs cacheadmin -listDirectives
Found 1 entry
ID POOL    REPL EXPIRY                    PATH
1 pool1      1 2021-07-22T19:53:12+0000  /cache/1
```

__Removing Directive:__
Let's add a few more and then remove them: 

```
# hdfs cacheadmin -addDirective -path /cache/2 -pool pool1 -replication 2 -ttl 1d
Added cache directive 2
--
# hdfs cacheadmin -listDirectives
Found 5 entries
 ID POOL    REPL EXPIRY                    PATH
  1 pool1      1 2021-07-22T19:53:12+0000  /cache/1
  2 pool1      2 2021-07-20T19:56:49+0000  /cache/2
  3 pool1      1 2021-07-22T19:57:18+0000  /cache/3
  4 pool1      1 2021-07-22T19:57:24+0000  /cache/5
  5 pool1      1 2021-07-22T19:58:15+0000  /cache/6/5Blocks2.img
```


Now we remove the directives using the `-removeDirective` flag and the ID from the CLI: 

```
# hdfs cacheadmin -removeDirective 4
Removed cached directive 4
---
hdfs cacheadmin -listDirectives
Found 4 entries
 ID POOL    REPL EXPIRY                    PATH
  1 pool1      1 2021-07-22T19:53:12+0000  /cache/1
  2 pool1      2 2021-07-20T19:56:49+0000  /cache/2
  3 pool1      1 2021-07-22T19:57:18+0000  /cache/3
  5 pool1      1 2021-07-22T19:58:15+0000  /cache/6/5Blocks2.img
```

### Troubleshooting

__Cache Memory otu of limit:__
Cached blocks are kept in an off heap memory buffer. We want to stress this by adding more than the allowed amount.

is set to `2684354560` bytes or ~2.5G so we will need something greater than that to cause a failure: 

```
// My dn block dir is ~6.6 GB
// Ran following command until file was over 3gb
# tar cvzf 3GB.tar.gz /hadoop/hdfs/data/current
```

When trying to add a directive for a file that would exceed the capacity, we get the following error: 

```
# hdfs cacheadmin -addDirective -path /cache/4/3GB.tar.gz -pool pool1 -replication 3
InvalidRequestException: Caching path /cache/4/3GB.tar.gz of size 3303784448 bytes at replication 3 would exceed pool pool1's remaining capacity of -12595707804 bytes.
```


## Questions: 

__Is the NameNode Aware of cached blocks and locations?:__
Yes, the NN persists this information within the FSImage

<details>
<summary>CacheManager</summary>
&nbsp;
```xml
<CacheManagerSection>
    <nextDirectiveId>6</nextDirectiveId>
    <pool>
      <poolName>pool1</poolName>
      <ownerName>kevin</ownerName>
      <groupName>hdfs</groupName>
      <mode>504</mode>
      <limit>9223372036854775807</limit>
      <maxRelativeExpiry>0</maxRelativeExpiry>
    </pool>
    <directive>
      <id>1</id>
      <path>/cache/1</path>
      <replication>1</replication>
      <pool>pool1</pool>
      <expiration>
        <millis>1626983592199</millis>
        <relatilve>false</relatilve>
      </expiration>
    </directive>
```
&nbsp;
</details>

__Does it cache data on JVM heap or OS direct buffer?:__
Cached blocks are kept within an off-heap buffer controlled by the amount of lock memory.

__What if cache memory is out of limit?__
If cache memory if above limits, it will prevent new additions to the pool

__What is the TTL on cache data?__
This is the time before the directive is removed from a pool and no longer cached.


__Does it cache all copies of data?__
Not by default but when defining a directive, we can set the `-replication` flag to define the number of replicas to cache.


__Any limits on the cache directory?__
Yes, cached directories will only cache data at the next level. Sub-directories will not have their content cached. 

For example if we have /1/a/b and used the directive /1/ than files in /a or /a/b will not be cached.