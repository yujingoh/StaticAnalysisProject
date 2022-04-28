package sg.edu.nyp.sit.svds.filestore;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Timer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.util.Series;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;

public class Main {
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(Main.class);
	
	private Component file_svr=null, status_svr=null;
	private Timer timer=null;
	
	private int retryLimit=10;
	private int retryInterval=3*1000;
	
	//Main
	//-id testfs1
	//-fport 8010
	//-sport 8011
	//-host <host name or the ip address of the host running the app that is used to bind with the restlet, can be an internal ip, if none is supplied then restlet takes the default given by machine>
	//-reghost <host name or the ip address of the host running the app that is send to register with the master server, can be public ip, can only be used if reg is 0>
	//-path <abs path to where the file slices are stored> 
	//-config <path to where the configuration file is located>
	//-reg <if it needs to register to the master server, default 0.
	//		0 - register with master server and periodically ping back
	//		1 - do not register with master server but still periodically ping back
	//		2 - do not register with master server and do not ping back
	public static void main(String[] args) throws Exception{
		Hashtable<String, String> prop=Resources.transformValues(args);
		if(prop==null){
			LOG.fatal("Missing parameters.\nUnable to start.");
			return;
		}
		
		if(!prop.containsKey("ID") || !prop.containsKey("CONFIG") || !prop.containsKey("FPORT")
				|| !prop.containsKey("PATH") || !prop.containsKey("SPORT")){
			LOG.fatal("Missing parameters.\nUnable to start.");
			return;
		}
		
		if(!prop.containsKey("REG")){
			prop.put("REG", "0");
		}
		
		String regHost="";
		if(prop.get("REG").equals("0")){
			if(prop.containsKey("REGHOST"))
				regHost=prop.get("REGHOST");
			else if(prop.containsKey("HOST"))
				regHost=prop.get("HOST");
			else{
				LOG.fatal("Missing parameters.\nUnable to start.");
				return;
			}
		}
		
		(new Main(prop.get("CONFIG"))).startup(prop.get("ID").toUpperCase(), 
				Integer.parseInt(prop.get("FPORT")), Integer.parseInt(prop.get("SPORT")), 
				prop.get("HOST"), regHost, prop.get("PATH"), Integer.parseInt(prop.get("REG")));
	}
	
	public Main(String configPath) throws Exception{
		SliceStoreProperties.load(Resources.findFile(configPath));
	}
	
	public void startup(String id, int filePort, int statusPort, String host, 
			String rootPath, int registerMaster) throws Exception{
		start(id, filePort, statusPort, host, host, rootPath, registerMaster);
	}
	
	public void startup(String id, int filePort, int statusPort, String host, String regHost,
			String rootPath, int registerMaster) throws Exception{
		start(id, filePort, statusPort, host, regHost, rootPath, registerMaster);
	}
	
	private void start(String id, int filePort, int statusPort, String host, String regHost,
			String rootPath, int registerMaster) throws Exception{
		String connector=SliceStoreProperties.getString("master.connector");
		
		String truststore=SliceStoreProperties.getString("ssl.truststore");
		String keystore=SliceStoreProperties.getString("ssl.keystore");
		
		if(keystore!=null && !keystore.isEmpty()){
			keystore=Resources.findFile(keystore);
			
			if(keystore==null)
				throw new IOException("Unable to locate keystore path");
		}
		
		if(truststore!=null && !truststore.isEmpty()){
			truststore=Resources.findFile(truststore);
			if(truststore==null)
				throw new IOException("Unable to locate truststore path");
			
			System.setProperty(Resources.TRUST_STORE, truststore);
			System.setProperty(Resources.TRUST_STORE_PWD, SliceStoreProperties.getString("ssl.truststorepwd"));
			System.setProperty(Resources.TRUST_STORE_TYPE, SliceStoreProperties.getString("ssl.truststoretype"));

			if(keystore!=null && !keystore.isEmpty()){
				System.setProperty(Resources.KEY_STORE, keystore);
				System.setProperty(Resources.KEY_STORE_PWD, SliceStoreProperties.getString("ssl.keystorepwd"));
				System.setProperty(Resources.KEY_PWD, SliceStoreProperties.getString("ssl.keypwd"));
				System.setProperty(Resources.KEY_STORE_TYPE, SliceStoreProperties.getString("ssl.keystoretype"));
			}
		}
		
		//register with the master table
		PingBackMaster pgm=null;
		if(registerMaster<2){
			pgm=new PingBackMaster(SliceStoreProperties.getString("master.address"), connector, id, 
									SliceStoreProperties.getString("slicestore.namespace"), regHost+":"+filePort);
			
			if(registerMaster==0){
				Properties p=new Properties();
				p.put(FileSliceServerInfo.RestletPropName.STATUS_HOST.value(), regHost+":"+statusPort);
				p.put(FileSliceServerInfo.RestletPropName.STATUS_SSL.value(), 
						SliceStoreProperties.getString(FileSliceServerInfo.RestletPropName.STATUS_SSL.value()));
				
				int retryCnt=0;
				while(!pgm.registerFileSliceServer(p)){
					if(retryCnt<retryLimit){
						LOG.warn("Problem registering to master server. Retry in " + (retryInterval/1000) + " seconds.");
						Thread.sleep(retryInterval);
					}else{
						LOG.error("Unable to register to master server. Unable to start.");
						return;
					}
						
					retryCnt++;
				}
			}
		}
		
		Server svrHttp=null;
		Series<Parameter> parameters=null;
		
		file_svr = new Component();  	
		if(host!=null) svrHttp=file_svr.getServers().add(Protocol.HTTP, host, filePort);
		else svrHttp=file_svr.getServers().add(Protocol.HTTP, filePort);
		parameters=svrHttp.getContext().getParameters();
		parameters.add("maxThreads", "255");
		parameters.add("persistingConnections", "false");
		file_svr.getDefaultHost().attach("/svds", new FileRestlet(rootPath, id));
		file_svr.start();
		
		status_svr=new Component();
		boolean status_ssl=SliceStoreProperties.getString(FileSliceServerInfo.RestletPropName.STATUS_SSL.value()).equalsIgnoreCase("on")?
							true : false;
		if(status_ssl) svrHttp=status_svr.getServers().add(Protocol.HTTPS, 
				SliceStoreProperties.getString(FileSliceServerInfo.RestletPropName.STATUS_HOST.value()), statusPort);
		else if(host!=null) svrHttp=status_svr.getServers().add(Protocol.HTTP, host, statusPort);
		else svrHttp=status_svr.getServers().add(Protocol.HTTP, statusPort);
		parameters=svrHttp.getContext().getParameters();
		parameters.add("maxThreads", "128");
		parameters.add("persistingConnections", "false");
		if(status_ssl){
			parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
			parameters.add("keystorePath", keystore);
			parameters.add("keystorePassword", SliceStoreProperties.getString("ssl.keystorepwd"));
			parameters.add("keyPassword", SliceStoreProperties.getString("ssl.keypwd"));
			parameters.add("keystoreType", SliceStoreProperties.getString("ssl.keystoretype"));
		}
		status_svr.getDefaultHost().attach("/status", new StatusRestlet(rootPath, id));
		status_svr.start();

		if(registerMaster<2){
			//start timer task to periodically connect the master server
			timer=new Timer();
			
			//if the registraton to the master fail, the mater server will register the server
			//on the next successful ping back
			timer.schedule(pgm, 
					SliceStoreProperties.getLong(SliceStoreProperties.PropName.MASTER_PINGBACK_INTERVAL)*1000, 
					SliceStoreProperties.getLong(SliceStoreProperties.PropName.MASTER_PINGBACK_INTERVAL)*1000);
		}
	}
	
	public void shutdown() throws Exception{
		if(file_svr!=null && file_svr.isStarted()){
			file_svr.stop();
			file_svr=null;
		}
		
		if(status_svr!=null && status_svr.isStarted()){
			status_svr.stop();
			status_svr=null;
		}
		
		if(timer!=null){
			timer.cancel();
			timer=null;
		}
	}
}
