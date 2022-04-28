set path=C:\Program Files\Java\jdk1.6.0_27\bin
set proppath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\resource
set libpath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib
set classpath=.;%libpath%\microsoft-windowsazure-api-0.2.0.jar;%libpath%\commons-lang3-3.1.jar;%libpath%\commons-logging-1.1.1.jar;%libpath%\jackson-core-asl-1.8.3.jar;%libpath%\jackson-jaxrs-1.8.3.jar;%libpath%\jackson-mapper-asl-1.8.3.jar;%libpath%\jackson-xc-1.8.3.jar;%libpath%\javax.inject-1.jar;%libpath%\jaxb-impl-2.2.3-1.jar;%libpath%\jersey-client-1.10-b02.jar;%libpath%\jersey-core-1.10-b02.jar;%libpath%\jersey-json-1.10-b02.jar;%libpath%\jettison-1.1.jar;%libpath%\stax-api-1.0.1.jar
java -Djava.util.logging.config.file=%proppath%\restlet-logging.properties sg.edu.nyp.sit.svds.master.filestore.AzureSliceStoreRegistration azureSliceStores(sample).txt localhost:9011 http
pause