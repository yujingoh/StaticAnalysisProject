set path=C:\Program Files\Java\jdk1.6.0_27\bin
set proppath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\resource
set libpath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib
set classpath=.;%libpath%\commons-logging-1.1.1.jar;%libpath%\log4j-1.2.9.jar;%proppath%
java -Djava.util.logging.config.file=%proppath%\restlet-logging.properties sg.edu.nyp.sit.svds.client.FileLoadTestApp -clients 1 -filesize 1024 -mode NFS -nfspath D:\\Projects\\CloudComputing\\Development\\DiffusedCloudStorage\\filestore\\storage
pause