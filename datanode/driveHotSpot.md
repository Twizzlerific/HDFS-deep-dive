# HDFS Datanode Drive Hotspot


## Overview
Disk hotspotting can occur when a particular datanode carries more blocks than the average block count of other DataNodes. This can occur due to a few reasons:

- DataNode decommisioning
- BlockPlacement Logic
- Storage Policies 
- etc. 

A common cause can be a result of HDFS short-circuiting where a client placing a file on HDFS may default to the local DN. If this client is an application that runs consistently than that local DN may start experiencing hotspotting. To avoid this, we can run apps on edge nodes.





