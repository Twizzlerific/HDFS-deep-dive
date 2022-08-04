# HDFS NameNode: User/Group Management

---
__TASKS:__

<!-- toc -->

- [Linux Users and Groups](#Linux-Users-and-Groups)
- [File Ownership](#File-Ownership)
- [Group Ownership](#Group-Ownership)
- [HDFS SuperUser](#HDFS-SuperUser)
- [Questions](#Questions)

<!-- tocstop -->

---


## Linux Users and Groups

Creating a local user and group on linux for NameNode to resove. 

Active NameNode: nn1

Create User and Groups (only on nn1):

```
// Verifying no groups
# hdfs groups sme1
sme1 :

// Adding Group
# groupadd hdfs-sme
hdfs-sme:x:1002:-g

// Adding User
# useradd -g hdfs-sme sme1
# id sme1
uid=1005(sme1) gid=1002(hdfs-sme) groups=1002(hdfs-sme)


// Verifying new resolution
# hdfs groups sme1
sme1 : hdfs-sme
```

## File Ownership 
Because the Active NameNode can resolve the group memberships locally, this will be used when creating files: 

As `sme1` user:
```
# hdfs dfs -put /tmp/file.out /tmp
# hdfs dfs -ls /tmp/file.out
-rw-r--r--   3 sme1 hdfs  268435456 2021-07-05 18:33 /tmp/file.out
```

Unless they are a superuser, `sme1` can only edit permissions on files they are the owner of or else they will get an error: 
```
$ hdfs dfs -chown sme1: /tmp/7
chown: changing ownership of '/tmp/7': Permission denied. user=sme1 is not the owner of inode=7
```



## Group Ownership
As long as the group is locally available, the file permissions can be set for that group.

If the group doesn't exists or is not resolvable, we may get an error when setting permissions because that user is not connected to the group: 

```
# hdfs dfs -chown sme1:hdfs-sme-fake /tmp/file.out
chown: changing ownership of '/tmp/file.out': User null does not belong to hdfs-sme-fake
```
> An HDFS SuperUser can override this requirement:
> ```
> hdfs dfs -chown sme1:hdfs-sme-fake /tmp/file.out
> 
> hdfs dfs -ls /tmp/file.out
> -rw-r--r--   3 sme1 hdfs-sme-fake  268435456 2021-07-05 18:33 /tmp/file.out
>```


## HDFS SuperUser
Creating an HDFS SuperUser involves two properties depending on the expected operations: 

`dfs.permissions.superusergroup`
The name of the Group who's members will be HDFS superusers. This is set to `hdfs` by default so adding a user to this group should make them a super user: 

```
// As sme1 user:
$ hdfs dfs -mkdir /sme
mkdir: Permission denied: user=sme1, access=WRITE, inode="/sme":hdfs:hdfs:drwxr-xr-x

// Add sme1 to hdfs group
# usermod -G hdfs sme1
# id sme1
uid=1005(sme1) gid=1002(hdfs-sme) groups=1002(hdfs-sme),1000(hdfs)

// Check group resolution in HDFS: 
# hdfs groups sme1
sme1 : hdfs-sme

// As hdfs user: 
$ hdfs dfsadmin -refreshUserToGroupsMappings

// Verify as sme1 user: 
$ hdfs groups
sme1 : hdfs-sme hdfs

// No longer blocked 
$ hdfs dfs -mkdir /sme
```

> If we remove sme1 from the `hdfs` group and refresh the mappings, the user will go back to being denied: 
> ```
> # usermod -G hdfs-sme sme1
> ```

&nbsp;


`dfs.cluster.administrators`
This property can be used to control which users can access the admin servlets on the NameNode such as the /stack, /conf and /jmx endpoints of the UI whens ecurity is enabled. 

Via Ambari set the following to make the hdfs-sme group administrators:
`dfs.cluster.administrators=hdfs,hdfs-sme`



## Questions

__Find out the mechanism used for group resolution? I.e LDAP?__

We can find out what is being used for group resolution from the command line: 
```
# hdfs getconf -confKey hadoop.security.group.mapping
org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback
```

This implementation will use the OS's group name resolution similar to the output of: 
```
# id `whoami`
```



