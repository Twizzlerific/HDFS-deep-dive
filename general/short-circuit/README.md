# HDFS Short Circuit Reads

## Overview
Short Circuit reads bypass the need for local clients to query files through the DN. Instead they can read the file directly. 


__Requirements:__
Short Circuit reads require the native Libraries. Specifically `libhadoop.so` must be available and enabled.

__Enabling:__
Sometimes enabled by default, Short circuit reads rely on the following properties: 


`dfs.client.read.shortcircuit` 
This enables short circuit reads

`dfs.domain.socket.path` 
This is the path to the OS level UNIX domain socket that the client can use to read the files. This path must be something that the DN can create but shouldn't be accessible to other users. 

__In action:__
When reading a file from HDFS, the client will connect to the NN to gather the block locations and then the clietn can utilize the path to connect aod collect the path. In debug logging we should see similar messages display the memory location of the cache kept when 

```
DEBUG shortcircuit.ShortCircuitCache: ShortCircuitCache(0xfac80)
```


