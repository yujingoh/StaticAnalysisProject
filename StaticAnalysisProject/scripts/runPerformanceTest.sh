export CLASSPATH=./resources:./svdstestclient.jar:lib/svdscore.jar:lib/svdsclient.jar:lib/commons-logging-1.1.1.jar:lib/log4j-1.2.9.jar:lib/httpclient-4.1.1.jar:lib/httpcore-4.1.jar:lib/aws-java-sdk-1.2.2.jar:lib/commons-codec-1.4.jar:lib/jackson-core-asl-1.8.3.jar:lib/mail-1.4.3.jar:lib/stax-1.2.0.jar:lib/stax-api-1.0.1.jar:lib/microsoft-windowsazure-api-0.2.0.jar:lib/commons-lang3-3.1.jar:lib/jackson-jaxrs-1.8.3.jar:lib/jackson-mapper-asl-1.8.3.jar:lib/jackson-xc-1.8.3.jar:lib/javax.inject-1.jar:lib/jaxb-impl-2.2.3-1.jar:lib/jersey-client-1.10-b02.jar:lib/jersey-core-1.10-b02.jar:lib/jersey-json-1.10-b02.jar:lib/jettison-1.1.jar
java -cp $CLASSPATH sg.edu.nyp.sit.svds.client.FileLoadTestApp -mode SVDS -clients 1 -filesize $1 -streaming yes


