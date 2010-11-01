Neo4j Stand-alone Project
=========================

This project assembles the Neo4j stand-alone distribution, pulling together all
the deliverable artfifacts and packagin them into a downloadable installer.

Deliverable artifacts included:

* neo4j server - start/stop-able standalone neo4j server
* neo4j shell - text based shell for accessing the server
* webadmin - the web-based administration application
* neo4j libs - java library files

Building
--------

Running `mvn clean install` will produce packaged installers for Windows
and linux.


Directories
-----------

* ./src/main/assemblies - maven-assembly-plugin descriptors
* ./src/main/binary - distributable binary files
  * maps to root output directory (as if `cp -r src/main/binary/* $NEO4J_HOME}`
  * contents should be copied with no processing
* ./src/main/text - distributable text files
  * maps to root output directory (as if `cp -r src/main/binary/* $NEO4J_HOME}`
  * contents should be filtered and processed for the destination platform


Note that the "binary" and "text" directories should be identical in structure,
differing only in content.


