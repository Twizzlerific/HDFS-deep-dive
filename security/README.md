# HDFS SECURITY

---
__TASKS:__

<!-- toc -->

- [Kerberos](#Kerberos)
  * [Overview:](#Overview)
  * [Cross-Realm Trust:](#Cross-Realm-Trust)
- [Tokens](#Tokens)
  * [Delegation Tokens](#Delegation-Tokens)
  * [Block Access Tokens](#Block-Access-Tokens)
- [UserGroupInformation (UGI)](#UserGroupInformation-UGI)
  * [LDAP Provider](#LDAP-Provider)
- [ACLs](#ACLs)
- [Encryption](#Encryption)
  * [Wire Encryption](#Wire-Encryption)
  * [Encryption at Rest (KMS)](#Encryption-at-Rest-KMS)

<!-- tocstop -->

---

__Cluster c246:__


Primary Realm: KS.COM

Primary KDC: ldap.ks.com (support lab)

__Cluster c346:__


Primary Realm: SUPPORTLAB.CLOUDERA.COM

Primary KDC: c346-node1.supportlab.cloudera.com



## Kerberos

### Overview:
Kerberos is a protocol used for authenticating clients to server using a mutually trusted third party (i.e. the KDC). It works using tickets granted to a client after performing some authentication such as providing a username/password that matches credentials in the KDC database. 

In HDFS it can be used to authenticate real users, proxy users, services, applications, etc. It is also used to secure WebUIs via SPNEGO. 

Kerberos can also be used as the initiator for Hadoop Delegation Tokens which are a form of renewable, extenable, and forwardable objects used for authentication. 


### Cross-Realm Trust:
When trust is establish between two or more separate Kerberos Realms. Useful in production where the Hadoop cluster needs to have a separate KDC and REALM from the overall org. 

Setting up cross realm trust between two kerberos realms starts by making the clients aware of both realms. This is accomplished by updating the kerberos config file `krb5.conf`. 

__krb5.conf:__
Within a basic krb5.conf we have several sections: 

- `[libdefaults]`
- `[logging]`
- `[realms]`

&nbsp;

`[libdefaults]`:
Settings for the kerberos client.
```
[libdefaults]
  renew_lifetime = 7d
  forwardable = true
  default_realm = KS.COM
  ticket_lifetime = 24h
  dns_lookup_realm = false
  dns_lookup_kdc = false
  #default_ccache_name = /tmp/krb5cc_%{uid}
```
&nbsp;

`[logging]`
Mostly relates to logging for the KDC server

```
[logging]
  default = FILE:/var/log/krb5kdc.log
  admin_server = FILE:/var/log/kadmind.log
  kdc = FILE:/var/log/krb5kdc.log
```
> Note: use `export KRB5_TRACE=<LOGFILE>` to get client level DEBUG logging. 

&nbsp;

`[realms]`
Tells the client which REALMS exist and where to connect. 
```
[realms]
  KS.COM = {
    master_kdc = ldap.ks.com
    admin_server = ldap.ks.com
    kdc = ldap.ks.com
  }
  SUPPORTLAB.CLOUDERA.COM = {
    admin_server = c346-node1.supportlab.cloudera.com
    kdc = c346-node1.supportlab.cloudera.com
  }
```

__krbtgt:__
To establish a one-way trust, we need to provide a principal by which the realms can retrive ticket-granting tickets. For two-way trust this will need to be done on all KDCs and the passwords should be kept the same. 

```
kadmin.local -q "addprinc krbtgt/KS.COM@SUPPORTLAB.CLOUDERA.COM"
kadmin.local -q "addprinc krbtgt/SUPPORTLAB.CLOUDERA.COM@KS.COM"
```

__Testing Cross-Realm:__
The quickest way to test cross-realm is to `kinit` with a principal from the other Realm. 

On Cluster c246 where the realm is KS.COM: 
```
# kinit admin/admin@SUPPORTLAB.CLOUDERA.COM
Password for admin/admin@SUPPORTLAB.CLOUDERA.COM:
```
The prompt for the password is enough to verify that the KDC is reachable and that the princiapl `admin/admin` exists. This alone will still fail if we attempt to perform any action on hadoop: 

```
INFO util.KerberosName: No auth_to_local rules applied to admin/admin@SUPPORTLAB.CLOUDERA.COM
...
Caused by: GSSException: No valid credentials provided (Mechanism level: Fail to create credential. (63) - No service creds
```

__auth_to_local:__
How hadoop components will translate a kerberos principals like into users. 

`admin/admin@SUPPORTLAB.CLOUDERA.COM --> admin`

Without rules defined for SUPPORTLAB.CLOUDERA.COM, we can locally verify the translation via the kerbname class: 

```
# hadoop org.apache.hadoop.security.HadoopKerberosName admin/admin@SUPPORTLAB.CLOUDERA.COM
INFO util.KerberosName: No auth_to_local rules applied to admin/admin@SUPPORTLAB.CLOUDERA.COM
Name: admin/admin@SUPPORTLAB.CLOUDERA.COM to admin/admin@SUPPORTLAB.CLOUDERA.COM
```

We can setup trust by updating the auth_to_local rules with the following rules: 

```
RULE:[1:$1@$0](.*@SUPPORTLAB.CLOUDERA.COM)s/@.*//
RULE:[2:$1@$0](.*@SUPPORTLAB.CLOUDERA.COM)s/@.*//
```

> Note: To avoid multiple restarts when testing, we can update the local /etc/hadoop/conf/core-site.xml and run the kerbname command. 

After update, we should see the principals get properly translated: 

```
# hadoop org.apache.hadoop.security.HadoopKerberosName admin/admin@SUPPORTLAB.CLOUDERA.COM
Name: admin/admin@SUPPORTLAB.CLOUDERA.COM to admin
```

## Tokens 

### Delegation Tokens

__overview:__
Delegation tokens are a mehanism within Hadoop for internal auth that can be passed between services. A prime example is a YARN application which reads/writes to HDFS. The Delegation token for a directory on HDFS can be passed to the applcation allowing it the same access as the authenticated principal. 


__example:__
We can see an example of the delegation token being passed via a teragen 

```
/usr/hdp/current/hadoop-client/bin/hadoop jar \
/usr/hdp/current/hadoop-mapreduce-client/hadoop-mapreduce-examples-*.jar \
teragen 1000 /tmp/`whoami`/teragen_$(date +%s).out
```

Several token should be noted including HDFS, YARN, and the Timeline Service: 

*HDFS*
```
DEBUG mapreduce.JobSubmitter: adding the following namenodes' delegation tokens:[hdfs://c246-node2.supportlab.cloudera.com:8020]
```
*YARN*
```
impl.YarnClientImpl: Add timline delegation token into credentials: Kind: TIMELINE_DELEGATION_TOKEN, Service: 172.25.34.215:8188, Ident: (owner=kevin, renewer=yarn, realUser=, issueDate=1625777204580, maxDate=1626382004580, sequenceNumber=3, masterKeyId=14)
```
Delegation tokens can also be fetched from HDFS and stored within a file for use by client: 

*With principal: kevin*
```
# hdfs fetchdt --webservice http://c246-node2.supportlab.cloudera.com:50070 --renewer hdfs dt.out
```

This token can then be passed along to another service, client, or even user by setting an environment variable: 

`export HADOOP_TOKEN_FILE_LOCATION=/root/dt.out`

I can then run a command and this will use the token: 

*With principal: beast*
```
DEBUG security.UserGroupInformation: Reading credentials from location set in HADOOP_TOKEN_FILE_LOCATION: /root/dt.out
DEBUG security.UserGroupInformation: Loaded 1 tokens
```


### Block Access Tokens 
These Tokens are unique to HDFS. They are provided to a DFSClient when performing a read and authorize the client to read the blocks for a file stored on a DataNode. 

Unlike Delegation tokens, Block Access tokens are not renewed beacuse they are expected to be short lived. The key is computed on the NameNode and updated during DN heartbeats. Expired Block tokens mean the DN has not checked back in with the NameNode for an updated token. 


## UserGroupInformation (UGI)

### LDAP Provider

LDAP users and auth can be performed in one of two ways: 

1. via Hadoop Group Mapping
2. SSSD to sync the users to local Linux OS (preferred)


_Hadoop Group Mapping_
Provides Hadoop components the necessary properties to query an ldap server for User/Group resolution. 

_SSSD_ (Recommended)
Linux service which will do the syncing so that Hadoop components need only check for the user locally to get the group resolution. 


__setup:__

_Hadoop Group Mapping_
The core-site.xml need to be updated to include the settings equivalent to an ldapsearch query.

Resources: [Set Up Hadoop Group Mapping for LDAP/AD](https://docs.cloudera.com/HDPDocuments/HDP3/HDP-3.1.0/installing-ranger/content/set_up_hadoop_group_mapping_for_ldap_ad.html)

Example: 
```
<property>
<name>hadoop.security.group.mapping</name>
<value>org.apache.hadoop.security.LdapGroupsMapping</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.bind.user</name>
<value>cn=oneaboveall,dc=ks,dc=com</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.bind.password</name>
<value>kevin.smith</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.url</name>
<value>ldap://ldap.ks.com:389/</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.base</name>
<value>dc=ks,dc=com</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.search.filter.user</name>
<value>(&(objectClass=posixAccount)(uid={0},ou=Users,dc=ks,dc=com))</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.search.filter.group</name>
<value>(objectclass=posixGroup)</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.search.attr.member</name>
<value>memberUid</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.search.attr.memberof</name>
<value>memberOf</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.search.attr.group.name</name>
<value>cn</value>
</property>

<property>
<name>hadoop.security.group.mapping.ldap.conversion.rule</name>
<value>to_lower</value>
</property>
```


>Set up on Cluster c346

_SSSD_
Set up by installing the linux packages and then configuring the sssd.conf file to connect and authenticate:

Example Config: [sssd.conf](./sssd.conf)

> Set up on Cluster c246

__usage:__

__common issues:__

## ACLs

Access Control Lists or 'ACLs' is a permissions model ontop of the normal POSIX permissions used by HDFS. 

Where traditionally we may have the `owner`, `group`, and `other`, ACLS allow fine grained permissions models by allowing admisn to set up additional permissions for users and groups. 

__Setting up:__

ACLs have to be enabled via a property. If not, we will get this error: 

```
# hdfs dfs -setfacl -m default:group:avengers:rwx /acls
setfacl: The ACL operation has been rejected.  Support for ACLs has been disabled by setting dfs.namenode.acls.enabled to false.
```

We need to set `dfs.namenode.acls.enabled` to true within the hdfs-site.xml to enable ACLs.


__Example:__

Here we create a directory and allow/deny permissions based on groups memberships. 


Verifying User group memberships
```
# hdfs groups beast
beast : x-men admins x-factor brotherhood_of_evil_mutants defenders exiles avengers

# hdfs groups wolverine
wolverine : new_x-men x-men avengers x-force s.h.i.e.l.d. the_hand
```

Let's create a directory and setup some acls.
```
# hdfs dfs -mkdir /acls
# hdfs dfs -setfacl -m default:group:avengers:rwx /acls
# hdfs dfs -mkdir /acls/avengers /acls/x-men /acls/brotherhood_of_evil_mutants
```

Now we can apply ACLS: 

```
# hdfs dfs -setfacl --set user::rwx,user:hadoop:rw-,group::r--,group:x-men:rwx,other::--- /acls/x-men

// As Beast:
# hdfs dfs -put /etc/hosts /acls/x-men/

// As Wolverine:
hdfs dfs -cat /acls/x-men/hosts
// Success

// As Thor: 

# hdfs dfs -cat /acls/x-men/hosts
cat: Permission denied: user=thor, access=EXECUTE, inode="/acls/x-men/hosts":hdfs:hdfs:drwxrwx---
# hdfs dfs -put /etc/hosts /acls/avengers/


# hdfs groups kingpin
kingpin : hydra

// As Kingpin:
# hdfs dfs -cat /acls/avengers/hosts
cat: Permission denied: user=kingpin, access=EXECUTE, inode="/acls/avengers/hosts":hdfs:hdfs:drw-rwx---

```

## Encryption
### Wire Encryption

Wire encryption is the use of certificates to encrypt the traffic. This is data encryption in transit. We do this be defining keystores and truststores for the various master and worker components as well as client. 

RPC connections with privacy enabled will use encrypted SASL connections

HTTP connections will use SSL for communicating 

__setup:__
1. Create certificates
2. Update hadoop configuration
3. Restart Components

> Resource: [Hadoop SSL](https://github.com/Raghav-Guru/hadoopssl)


### Encryption at Rest (KMS)

When data is encrypted at rest in Hadoop, it means that an key is used to encrypt the data before it is persisted to disk. That key is then placed behind access policies to add another layer of protection. 

Without the key, the data cannot be decrypted so a client must authenticate and then be allowed access to the decryption keys to decrypt the blocks build the file. 

__setup:__

Setting up Encryption-at-rest starts with defining a separate HDFS admin account. This will seperate the hdfs system account from the admin account to prevent the hdfs user from being able to decrypt blocks. 

The encryption metadata is available via the extended attributes so any transfer operation (i.e. distcp) should include flags to maintain attributes (i.e distcp -px)

>Resource: [Create an HDFS Admin User](https://docs.cloudera.com/HDPDocuments/HDP3/HDP-3.0.0/configuring-hdfs-encryption/content/create_an_hdfs_admin_user.html)


For the keys, we use a Key Management Server (kms) provided by Ranger. Ranger KMS will centralize the access and authorization to decryption keys.

Ranger KMS is accessed via the Ranger UI logging in with the KMS credentials: 

`keyadmin`:`keyadmin`

We will add the `operator` group to the Ranger Plicy policy to allow it access. Under the Encrytion tab, we can add a new key and call it `encr1`.

We can verify the key via the CLI: 

```
// With encr kerberos principal: 
# hadoop key list
Listing keys for KeyProvider: KMSClientProvider[http://c246-node1.supportlab.cloudera.com:9292/kms/v1/]
encr1

```

Now we will create an encryption Zone: 

```
# hdfs dfs -mkdir /encrypted
# hdfs crypto -createZone -keyName encr1 -path /encrypted
Added encryption zone /encrypted
--
# hdfs crypto -listZones
/encrypted  encr1
```

Data put into the `/encrypted` directory of HDFS will now be encrypted. If the user requesting data within an encryption zone does not have the proper policies in Ranger, we get the following error: 

```
# hdfs dfs -cat /encrypted/hosts
cat: User:kevin not allowed to do 'DECRYPT_EEK' on 'encr1'
```
A Similar message will appear if the user does not have access to the keys needed to encrypt data

```
# hdfs dfs -put /var/log/ambari-agent/ambari-agent.log /encrypted
put: User:kevin not allowed to do 'DECRYPT_EEK' on 'encr1'
```