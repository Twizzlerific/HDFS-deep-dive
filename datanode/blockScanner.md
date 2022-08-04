# Datanode Block Scanner

Block scanner is the mechanism by which a datanode checks for data integrity. It slowly iterates through the blocks on the volumes by initiating [VolumeScanners](./volumeMGMT). 

During writes to HDFS, block data is written alongside a checksum which is used to verify any data corruption. This same checksum is verified by the client when retrieving the data. A similar operation occurs on an interval based on `dfs.datanode.scan.period.hours` where block scanner will begin checking the blocks by kicking off Volume Scanner threads. 

Since block scanner's operations depend on the size of the data, as the data grows the amount of time it takes for the scanner to complete will need to grow as well. This can be tuned using other properties such as `dfs.block.scanner.volume.bytes.per.second` which will control teh speed but ahs the caveat of increasing i/o overhead. 




