package sg.edu.nyp.sit.pvfs.proxy;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.util.Series;

import sg.edu.nyp.sit.pvfs.proxy.metadata.FileRestlet;
import sg.edu.nyp.sit.pvfs.proxy.metadata.MetadataAction;
import sg.edu.nyp.sit.pvfs.proxy.metadata.MetadataRestlet;
import sg.edu.nyp.sit.pvfs.proxy.metadata.SliceStoresRestlet;
import sg.edu.nyp.sit.pvfs.proxy.metadata.SubscriberMetadataInfo;
import sg.edu.nyp.sit.pvfs.proxy.subscriber.SubscriberAction;
import sg.edu.nyp.sit.pvfs.proxy.subscriber.SubscriberRestlet;
import sg.edu.nyp.sit.svds.Resources;

public class Main {
	private static final Log LOG = LogFactory.getLog(Main.class);
	
	private static String HTML_ROOT;
	
	private Component svr=null;
	
	//-sys_config	path to the proxyconfig.properties file
	//-port 		port to start the restlet
	//-path			location to save the SQLite database send by the android applications
	public static void main(String[] args) throws Exception{
		Hashtable<String, String> prop=Resources.transformValues(args);
		if(prop==null){
			LOG.fatal("Missing parameters.\nUnable to start.");
			return;
		}
		
		if(!prop.containsKey("SYS_CONFIG") || !prop.containsKey("PORT") || !prop.containsKey("PATH")){
			LOG.fatal("Missing system config path/port parameter.\nUnable to start.");
			return;
		}
		
		(new Main(prop.get("SYS_CONFIG"))).start(Integer.parseInt(prop.get("PORT")), prop.get("PATH"));
	}
	
	public Main(String sysConfigPath) throws Exception{
		ProxyProperties.load(Resources.findFile(sysConfigPath));
	}
	
	private Map<String, SubscriberMetadataInfo> lst_usr=Collections.synchronizedMap(new Hashtable<String, SubscriberMetadataInfo>());
	
	public void start(int port, String path) throws Exception{
		HTML_ROOT="file:///"+Resources.findFile("sg/edu/nyp/sit/pvfs/proxy/html");
		
		boolean ssl=false;
		String keystore=ProxyProperties.getString("ssl.keystore");
		String keystorepwd=ProxyProperties.getString("ssl.keystorepwd");
		String keystoretype=ProxyProperties.getString("ssl.keystoretype");
		String keypwd=ProxyProperties.getString("ssl.keypwd");
		
		if(keystore!=null && !keystore.isEmpty()){
			ssl=true;
			keystore=Resources.findFile(keystore);
		}
		
		Server svrHttp=null;
		Series<Parameter> parameters=null;
		svr=new Component();
		svr.getClients().add(Protocol.FILE);
		
		if(!ssl)
			svrHttp=svr.getServers().add(Protocol.HTTP, port);
		else if(ProxyProperties.getString("ssl.address").isEmpty())
			svrHttp=svr.getServers().add(Protocol.HTTPS, port);
		else
			svrHttp=svr.getServers().add(Protocol.HTTPS, 
					ProxyProperties.getString("ssl.address"), port);
		
		parameters=svrHttp.getContext().getParameters();
		parameters.add("maxThreads", "255");
		parameters.add("persistingConnections", "false");
		if(ssl){
			parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
			parameters.add("keystorePath", keystore);
			parameters.add("keystorePassword", keystorepwd);
			parameters.add("keyPassword", keypwd);
			parameters.add("keystoreType", keystoretype);	
		}

		RequestAuthentication ra=new RequestAuthentication();
		MetadataAction ma=new MetadataAction(path, lst_usr);
		SubscriberAction sa=new SubscriberAction(ma);
		
		svr.getDefaultHost().attach("/subscriber", new SubscriberRestlet(ra, sa));
		svr.getDefaultHost().attach("/metadata", new MetadataRestlet(ra, ma));
		svr.getDefaultHost().attach("/file", new FileRestlet(ra, lst_usr));
		svr.getDefaultHost().attach("/slicestore", new SliceStoresRestlet(ra, lst_usr));
		
		Application webRoot = new Application() {   
		    @Override  
		    public Restlet createInboundRoot() {   
		            return new Directory(getContext(), HTML_ROOT);   
		    }   
		}; 
		svr.getDefaultHost().attach("/web", webRoot);
		
		svr.start();
	}
	
	public void shutdown() throws Exception{
		if(svr!=null && svr.isStarted()){
			svr.stop();
			svr=null;
		}
	}
}