echo on
SET CLASSPATH=svdsmaster.jar;lib\org.osgi.core.jar;lib\org.restlet.jar;lib\javax.servlet.jar;lib\org.eclipse.jetty.ajp.jar;lib\org.eclipse.jetty.continuations.jar;lib\org.eclipse.jetty.http.jar;lib\org.eclipse.jetty.io.jar;lib\org.eclipse.jetty.server.jar;lib\org.eclipse.jetty.util.jar;lib\org.jsslutils.jar;lib\org.restlet.ext.jetty.jar;lib\org.restlet.ext.ssl.jar;lib\commons-logging-1.1.1.jar;lib\log4j-1.2.9.jar;lib\httpclient-4.1.1.jar;lib\httpcore-4.1.jar;lib\aws-java-sdk-1.2.2.jar;lib\mail-1.4.3.jar;lib\stax-1.2.0.jar;lib\microsoft-windowsazure-api-0.2.0.jar;lib\commons-lang3-3.1.jar;lib\jackson-core-asl-1.8.3.jar;lib\jackson-jaxrs-1.8.3.jar;lib\jackson-mapper-asl-1.8.3.jar;lib\jackson-xc-1.8.3.jar;lib\javax.inject-1.jar;lib\jaxb-impl-2.2.3-1.jar;lib\jersey-client-1.10-b02.jar;lib\jersey-core-1.10-b02.jar;lib\jersey-json-1.10-b02.jar;lib\jettison-1.1.jar;lib\stax-api-1.0.1.jar;resources;.
java -cp %CLASSPATH% -Djava.util.logging.config.file=resources\restlet-logging.properties sg.edu.nyp.sit.svds.master.Main -type main -ida_config IDAProp.properties -sys_config MasterConfig.properties