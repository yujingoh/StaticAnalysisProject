REM set path=C:\Program Files\Java\jdk1.6.0_26\bin
echo off 
set libpath=.\lib
set classpath=.\resources;.\svdstestclient.jar;%libpath%\svdscore.jar;%libpath%\svdsclient.jar;%libpath%\commons-logging-1.1.1.jar;%libpath%\log4j-1.2.9.jar;%libpath%\httpclient-4.1.1.jar;%libpath%\httpcore-4.1.jar;%libpath%\aws-java-sdk-1.2.2.jar;%libpath%\commons-codec-1.4.jar;%libpath%\jackson-core-asl-1.8.3.jar;%libpath%\mail-1.4.3.jar;%libpath%\stax-1.2.0.jar;%libpath%\stax-api-1.0.1.jar;%libpath%\microsoft-windowsazure-api-0.2.0.jar;%libpath%\commons-lang3-3.1.jar;%libpath%\jackson-jaxrs-1.8.3.jar;%libpath%\jackson-mapper-asl-1.8.3.jar;%libpath%\jackson-xc-1.8.3.jar;%libpath%\javax.inject-1.jar;%libpath%\jaxb-impl-2.2.3-1.jar;%libpath%\jersey-client-1.10-b02.jar;%libpath%\jersey-core-1.10-b02.jar;%libpath%\jersey-json-1.10-b02.jar;%libpath%\jettison-1.1.jar
echo on
java sg.edu.nyp.sit.svds.client.FileLoadTestApp -mode SVDS -clients 1 -filesize %1 -streaming yes
