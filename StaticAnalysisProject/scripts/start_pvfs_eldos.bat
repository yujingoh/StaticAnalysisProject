cd D:\Projects\CloudComputing\Development\DiffusedCloudStorage\bin
set path=C:\Program Files\Java\jdk1.6.0_27\bin
set libpath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib
set proppath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\resource
set classpath=.;svdsclient.jar;%libpath%\svdscore.jar;%libpath%\aws-java-sdk-1.2.2.jar;%libpath%\bluecove-2.1.1-SNAPSHOT.jar;%libpath%\commons-codec-1.4.jar;%libpath%\httpclient-4.1.1.jar;%libpath%\httpcore-4.1.jar;%libpath%\log4j-1.2.9.jar;%libpath%\mail-1.4.3.jar;%libpath%\stax-1.2.0.jar;%libpath%\microsoft-windowsazure-api-0.2.0.jar;%libpath%\commons-lang3-3.1.jar;%libpath%\commons-logging-1.1.1.jar;%libpath%\jackson-core-asl-1.8.3.jar;%libpath%\jackson-jaxrs-1.8.3.jar;%libpath%\jackson-mapper-asl-1.8.3.jar;%libpath%\jackson-xc-1.8.3.jar;%libpath%\javax.inject-1.jar;%libpath%\jaxb-impl-2.2.3-1.jar;%libpath%\jersey-client-1.10-b02.jar;%libpath%\jersey-core-1.10-b02.jar;%libpath%\jersey-json-1.10-b02.jar;%libpath%\jettison-1.1.jar;%libpath%\stax-api-1.0.1.jar;%libpath%\eldos.cbfs.jar;%libpath%\commons-io-1.4.jar;%proppath%
java -Djava.library.path=%libpath% sg.edu.nyp.sit.pvfs.virtualdisk.eldos.InstallEldos
exit