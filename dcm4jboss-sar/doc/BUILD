BUILD INSTRUCTIONS FOR DCM4CHEE ARCHIVE:
========================================

In order to build all components of the dcm4chee archive application you have
to download and install:

- Ant 1.7.0 [http://ant.apache.org]

- XDoclet 1.2.3 [http://xdoclet.sourceforge.net]

- JBoss Application Server 4.2.3.GA [http://labs.jboss.com/jbossas/downloads]

- Apache FOP 0.95 [http://xmlgraphics.apache.org/fop/]

- JCraft JSch 0.1.49 Java Secure Channel [http://www.jcraft.com/jsch/]

- Java CIFS Client Library 1.3.17 [http://jcifs.samba.org/src/]

- CAStorSDK Caringo CAStor SDKm 1.3.1 [repo1.maven.org/maven2/com/caringo/client/CAStorSDK/1.3.1-CS40/CAStorSDK-1.3.1-CS40.jar]

- Jakarta Cactus 1.8.1 [http://jakarta.apache.org/cactus/]
(only needed to build test suite for dcm4jboss-ejb - not need to build
application)

dcm4chee-2.17.4 archive also relies on

- dcm4che-1.4.34 : DICOM Library

which binary and source distribution package is also available at
http://sourceforge.net/projects/dcm4che. The binary package is sufficient to
build dcm4chee archive. If you download the source package, you have to build
all components of dcm4che-1.4.34 (ant release) before building dcm4chee-2.17.4.

dcm4chee-2.17.4 archive also make use of

- dcm4che-audit-logger : JMX Service for emitting DICOM Supp95 Audit Trail
  Messages, and
- dcm4che-audit-login : JAAS login module emitting User Authentication message

which sources are not included by dcm4chee-2.17.4-src.zip, but provided in a
separate ZIP archive: dcm4chee-audit-2.17.4-src.zip at
http://sourceforge.net/projects/dcm4che. Follow the build instructions include
in dcm4chee-audit, to build and install these artifacts in your local Maven
Repository (default: ~/.m2/repository) for usage by following build task for 
dcm4chee-2.17.4 archive.

dcm4chee-2.17.4 archive also make use of

- dcm4chee-web : WEB based administration tool 

which sources are not included by dcm4chee-2.17.4-src.zip, but provided in a
separate ZIP archive: dcm4chee-web-3.0.4-src.zip at
http://sourceforge.net/projects/dcm4che. Follow the build instructions include
in dcm4chee-web, to build and install these artifacts for your appropriate database
in your local Maven Repository (default: ~/.m2/repository) for usage by following 
build task for dcm4chee-2.17.4 archive.

dcm4chee-2.17.4 archive also make use of

- dcm4chee-docstore : Generic Document Storage (used by RID - Retrieve Information for Display) 

which sources are not included by dcm4chee-2.17.4-src.zip, but provided in a
separate ZIP archive: dcm4chee-docstore-1.0.3-src.zip at
http://sourceforge.net/projects/dcm4che. Follow the build instructions include
in dcm4chee-docstore, to build and install these artifacts in your local Maven
Repository (default: ~/.m2/repository) for usage by following build task for 
dcm4chee-2.17.4 archive.

dcm4chee-2.17.4 archive has compile dependency to

- dcm4chee-xds-infoset : XDS Infoset classes (XDS Query for Consumer and Document Lifecycle)

which sources are not included by dcm4chee-2.17.4-src.zip, but provided in a
separate ZIP archive: dcm4chee-xds-infoset-1.0.2-src.zip at
http://sourceforge.net/projects/dcm4che. Follow the build instructions include
in dcm4chee-xds-infoset, to build and install these artifacts in your local Maven
Repository (default: ~/.m2/repository) for usage by following build task for 
dcm4chee-2.17.4 archive.

Create an environment variable pointing to the Ant home directory (ANT_HOME)
and add the $ANT_HOME/bin directory to your PATH.

The build scripts (dcm4jboss-all/dcm4jboss-xxx/build.xml) assumes following
locations of dcm4che-1.4.34, XDoclet, JBoss, Apache FOP, Jsch, JCifs, Jakarta Cactus and
your local Maven2 Repository:

dcm4che14.home=${user.home}/dcm4che14/build/dcm4che-1.4.34
xdoclet.home=${user.home}/xdoclet-1.2.3
jboss.home=${user.home}/jboss-4.2.3.GA
fop.home=${user.home}/fop-0.95
jsch.home=${user.home}/jsch-0.1.49
jcifs.home=${user.home}/jcifs-1.3.17
castor.home=${user.home}/castor-1.3.1
cactus.home=${user.home}/cactus-1.8.1-bin
m2.repos=${user.home}/.m2/repository.

Instead of modifying the build scripts directly, you may adjust these values
to your build environment by renaming file 

dcm4jboss-all/build.properties.default
to
dcm4jboss-all/build.properties

and adjust these values there.

Change the working directory:
>cd dcm4jboss-all/dcm4jboss-build

and invoke:
>ant <database>-dist

to build all components and pack them in binary distribution package for your appropriate database

Supported <database> arguments:
  db2, firebird, hsql, mssql, mysql, oracle, psql

To build all components and pack them in binary distribution packages for each supported database:

precondition: build dcm4chee-web for all supported db's

>ant dist

target/dcm4chee-<version>-db2.zip
target/dcm4chee-<version>-hsql.zip
target/dcm4chee-<version>-firebird.zip
target/dcm4chee-<version>-mssql.zip
target/dcm4chee-<version>-mysql.zip
target/dcm4chee-<version>-oracle.zip
target/dcm4chee-<version>-psql.zip
.
