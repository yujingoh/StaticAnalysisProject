package sg.edu.nyp.sit.svds.master;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.util.Series;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.namespace.*;
import sg.edu.nyp.sit.svds.master.persistence.MasterImage;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;
import sg.edu.nyp.sit.svds.master.viewer.ViewerRestlet;
import sg.edu.nyp.sit.svds.master.file.*;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;

public class Main {
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(Main.class);

	private Timer timer=null;
	private Component file_svr=null, namespace_svr=null, maintainence_svr=null, viewer_svr=null;
	private FileSliceSegmentRecovery recovery=null;
	private FileChangeMode chgMode=null;
	
	private String keystore=null, keystorepwd=null, keystoretype=null, keypwd=null;
	private String truststore=null, truststorepwd=null, truststoretype=null;
	
	private static String LDAP_SSL_PROP="client.file.authentication.ldap.ssl";
	
	//for main server setup
	private TransLogger transLog=null;
	private NamespaceLogger namespaceLog=null;
	
	//Main 
	//-type <main|secondary>
	//-ida_config <rel path from user directory java prop containing the ida matrxi and stuff> (if start as main)
	//-sys_config <rel path from user directory java prop containing sys properties>
	public static void main(String[] args) throws Exception {	
		Hashtable<String, String> prop=Resources.transformValues(args);
		if(prop==null){
			LOG.fatal("Missing parameters.\nUnable to start.");
			return;
		}
		
		String mode=null;
		if(!prop.containsKey("TYPE")){
			LOG.fatal("Missing type parameter.\n Unable to start.");
			return;
		}else{
			mode=prop.get("TYPE").toUpperCase();
			if(!mode.equals("MAIN") && !mode.equals("SECONDARY")){
				LOG.fatal("Invalid type value. Unable to start.");
				return;
			}
		}
		
		if(mode.equals("MAIN")){
			if(!prop.containsKey("IDA_CONFIG") || !prop.containsKey("SYS_CONFIG")){
				LOG.fatal("Missing path, port and/or IDA/system config path parameter.\nUnable to start.");
				return;
			}
		
			(new Main(prop.get("IDA_CONFIG"), prop.get("SYS_CONFIG"))).startupMain();
		}else if(mode.equals("SECONDARY")){
			if(!prop.containsKey("SYS_CONFIG")){
				LOG.fatal("Missing sys config parameter.\nUnable to start.");
				return;
			}
				
			(new Main(prop.get("SYS_CONFIG"))).startupSecondary();
		}
	}
	
	//for secondary server instance
	public Main(String sysConfigPath) throws Exception{
		MasterProperties.load(Resources.findFile(sysConfigPath));
	}
	
	//for main server instance
	public Main(String idaConfigPath, String sysConfigPath) throws Exception{
		populateMainAppProps(idaConfigPath, sysConfigPath);
	}
	
	public void startupSecondary() throws Exception{
		final String host=MasterProperties.getString("master.main.address");
		final String connector=MasterProperties.getString("master.main.connector");
		final long size=MasterProperties.getLong("check.size");
		final long interval=MasterProperties.getLong("check.interval");
		
		if(connector.equalsIgnoreCase("https")){
			System.setProperty(Resources.TRUST_STORE, Resources.findFile(MasterProperties.getString("master.main.truststore")));
			System.setProperty(Resources.TRUST_STORE_PWD, MasterProperties.getString("master.main.truststorepwd"));
			System.setProperty(Resources.TRUST_STORE_TYPE, MasterProperties.getString("master.main.truststoretype"));
			
			String keystore=MasterProperties.getString("master.main.keystore");
			if(keystore!=null && !keystore.isEmpty()){
				System.setProperty(Resources.KEY_STORE, Resources.findFile(keystore));
				System.setProperty(Resources.KEY_STORE_PWD, MasterProperties.getString("master.main.keystorepwd"));
				System.setProperty(Resources.KEY_PWD, MasterProperties.getString("master.main.keypwd"));
				System.setProperty(Resources.KEY_STORE_TYPE, MasterProperties.getString("master.main.keystoretype"));
			}
		}
		
		timer=new Timer();
		
		timer.schedule(new TimerTask(){
			public void run(){
				SecondaryServer ss=new SecondaryServer(host, connector);
				
				ss.checkpointMaster();
			}
		}, 1000*interval, 1000*interval);
		
		timer.schedule(new TimerTask(){
			public void run(){
				SecondaryServer ss=new SecondaryServer(host, connector);
				
				if(ss.getLogSize()>size)
					ss.checkpointMaster();
			}
		}, 1000*60*5, 1000*60*5);
	}
	
	public void startupMain() throws Exception{
		String path=MasterProperties.getString("master.directory");
		
		truststore=MasterProperties.getString("ssl.truststore");
		truststorepwd=MasterProperties.getString("ssl.truststorepwd");
		truststoretype=MasterProperties.getString("ssl.truststoretype");
		if(truststore!=null && !truststore.isEmpty()){
			truststore=Resources.findFile(truststore);
			
			System.setProperty(Resources.TRUST_STORE, truststore);
			System.setProperty(Resources.TRUST_STORE_PWD, truststorepwd);
			System.setProperty(Resources.TRUST_STORE_TYPE, truststoretype);
		}
		
		//currently, both path are the same
		namespaceLog=new NamespaceLogger(path);
		MasterImage mi=new MasterImage(path);
		//TransLogger transLog=new TransLogger(path);
		transLog=new TransLogger(path);
		//load the image containing the namespaces and metadata
		Map<String, SortedMap<String, FileInfo>> lst_fileNamespaces=Collections.synchronizedMap(new HashMap<String, SortedMap<String, FileInfo>>());
		Map<String, NamespaceInfo> lst_namespaces=Collections.synchronizedMap(new HashMap<String, NamespaceInfo>());
		Map<String, FileSliceServerInfo> lst_sliceServers=Collections.synchronizedMap(new HashMap<String, FileSliceServerInfo>());
		
		MasterServer ms=new MasterServer(mi, transLog, namespaceLog);
		ms.startUp(lst_namespaces, lst_sliceServers, lst_fileNamespaces);
		ms=null;
		
		NamespaceAction na=new NamespaceAction(lst_namespaces, lst_sliceServers, namespaceLog);
		recovery=new FileSliceSegmentRecovery(na, transLog);
		chgMode=new FileChangeMode(na, new FileAction(na, lst_fileNamespaces, null, null, null)
				, transLog);

		//search through to find slices req recovery or changing mode
		for(SortedMap<String, FileInfo> files: lst_fileNamespaces.values()){
			for(FileInfo fi: files.values()){
				if(fi.isChgMode())
					chgMode.addFile(fi, fi.getChgMode());
				else{
					for(FileSliceInfo fsi: fi.getSlices()){
						if(fsi.hasSegments())
							recovery.addSlice(fsi, fi);
					}
				}
			}
		}
		
		FileAction fa=new FileAction(na, lst_fileNamespaces, transLog, recovery, chgMode);
		
		Server svrHttp=null;
		Series<Parameter> parameters=null;
		
		/*
		file_svr = new Component();
		int port=MasterProperties.getInt("master.port");
		if(MasterProperties.getString("main.ssl").equalsIgnoreCase("off")){
			svrHttp=file_svr.getServers().add(Protocol.HTTP, port);
			parameters=svrHttp.getContext().getParameters();
			parameters.add("maxThreads", "255");
			parameters.add("persistingConnections", "false");
		}else{
			svrHttp=file_svr.getServers().add(Protocol.HTTPS, 
					MasterProperties.getString("main.address"), 9010);
			parameters=svrHttp.getContext().getParameters();
			parameters.add("maxThreads", "255");
			parameters.add("persistingConnections", "false");
			
			parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
			parameters.add("keystorePath", MasterProperties.getString("main.keystore"));
			parameters.add("keystorePassword", MasterProperties.getString("main.keystorepwd"));
			parameters.add("keyPassword", MasterProperties.getString("main.keypwd"));
			parameters.add("keystoreType", MasterProperties.getString("main.keystoretype"));
			//parameters.add("truststorePath", "D:\\Work\\CloudComputing\\Certs\\nyp_moeif_trust.jks");
			//parameters.add("truststorePassword", "moeifssl");
			//parameters.add("truststoreType", "JKS");
			//parameters.add("needClientAuthentication", "false");
		}
		file_svr.getDefaultHost().attach("/namespace", new NamespaceRestlet(na));
		file_svr.getDefaultHost().attach("/file", new FileRestlet(fa));
		file_svr.start();
		*/
		
		boolean ssl, clientAuth;
		keystore=MasterProperties.getString("ssl.keystore");
		keystorepwd=MasterProperties.getString("ssl.keystorepwd");
		keystoretype=MasterProperties.getString("ssl.keystoretype");
		keypwd=MasterProperties.getString("ssl.keypwd");
		
		if(keystore!=null && !keystore.isEmpty())
			keystore=Resources.findFile(keystore);
		
		//start file restlet
		int fport=MasterProperties.getInt("master.file.port");
		file_svr = new Component();
		ssl=(MasterProperties.getString("master.file.ssl").equalsIgnoreCase("off") ? false: true);
		if(!ssl)
			svrHttp=file_svr.getServers().add(Protocol.HTTP, fport);
		else if(MasterProperties.getString("master.file.ssl.address").isEmpty())
			svrHttp=file_svr.getServers().add(Protocol.HTTPS, fport);
		else
			svrHttp=file_svr.getServers().add(Protocol.HTTPS, 
					MasterProperties.getString("master.file.ssl.address"), fport);
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
		if(MasterProperties.exist(LDAP_SSL_PROP) 
				&& MasterProperties.getString(LDAP_SSL_PROP).equalsIgnoreCase("on")){
			parameters.add("truststorePath", truststore);
			parameters.add("truststorePassword", truststorepwd);
			parameters.add("truststoreType", truststoretype);
		}
		file_svr.getDefaultHost().attach("/file", new FileRestlet(fa));
		file_svr.start();
		
		//start namespace restlet
		int nsport=MasterProperties.getInt("master.namespace.port");
		namespace_svr = new Component();
		ssl=(MasterProperties.getString("master.namespace.ssl").equalsIgnoreCase("off") ? false: true);
		if(!ssl)
			svrHttp=namespace_svr.getServers().add(Protocol.HTTP, nsport);
		else if(MasterProperties.getString("master.namespace.ssl.address").isEmpty())
			svrHttp=namespace_svr.getServers().add(Protocol.HTTPS, nsport);
		else
			svrHttp=namespace_svr.getServers().add(Protocol.HTTPS, 
					MasterProperties.getString("master.namespace.ssl.address"), nsport);
		parameters=svrHttp.getContext().getParameters();
		parameters.add("maxThreads", "128");
		parameters.add("persistingConnections", "false");
		clientAuth=false;
		if(ssl){
			parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
			parameters.add("keystorePath", keystore);
			parameters.add("keystorePassword", keystorepwd);
			parameters.add("keyPassword", keypwd);
			parameters.add("keystoreType", keystoretype);
			
			if(MasterProperties.getString("master.namespace.ssl.clientauth").equalsIgnoreCase("on"))
				clientAuth=true;
		}
		if(clientAuth || (MasterProperties.exist(LDAP_SSL_PROP) 
				&& MasterProperties.getString(LDAP_SSL_PROP).equalsIgnoreCase("on"))){
			parameters.add("truststorePath", truststore);
			parameters.add("truststorePassword", truststorepwd);
			parameters.add("truststoreType", truststoretype);

			if(clientAuth) parameters.add("wantClientAuthentication", "true");
		}
		namespace_svr.getDefaultHost().attach("/namespace", new NamespaceRestlet(na));
		namespace_svr.start();
		
		//start maintainence restlet if needed
		int mport=MasterProperties.getInt("master.maintainence.port");
		if(mport!=0){
			maintainence_svr=new Component();
			ssl=(MasterProperties.getString("master.maintainence.ssl").equalsIgnoreCase("off") ? false: true);
			if(!ssl)
				svrHttp=maintainence_svr.getServers().add(Protocol.HTTP, mport);
			else if(MasterProperties.getString("master.maintainence.ssl.address").isEmpty())
				svrHttp=maintainence_svr.getServers().add(Protocol.HTTPS, mport);
			else
				svrHttp=maintainence_svr.getServers().add(Protocol.HTTPS, 
						MasterProperties.getString("master.maintainence.ssl.address"), mport);
			parameters=svrHttp.getContext().getParameters();
			parameters.add("maxThreads", "20");
			parameters.add("persistingConnections", "false");
			if(ssl){
				parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
				parameters.add("keystorePath", keystore);
				parameters.add("keystorePassword", keystorepwd);
				parameters.add("keyPassword", keypwd);
				parameters.add("keystoreType", keystoretype);
				
				clientAuth=MasterProperties.getString("master.maintainence.ssl.clientauth").equalsIgnoreCase("on")?true:false;
				
				if(clientAuth){
					parameters.add("truststorePath", truststore);
					parameters.add("truststorePassword", truststorepwd);
					parameters.add("truststoreType", truststoretype);
					parameters.add("needClientAuthentication", "true");
				}
			}
			maintainence_svr.getDefaultHost().attach("/master", new MasterRestlet(mi, transLog, namespaceLog));
			maintainence_svr.start();
		}
		
		//start viewer restlet if needed
		int vport=MasterProperties.getInt("master.viewer.port");
		if(vport!=0){
			viewer_svr=new Component();
			ssl=(MasterProperties.getString("master.viewer.ssl").equalsIgnoreCase("off") ? false: true);
			if(!ssl)
				svrHttp=viewer_svr.getServers().add(Protocol.HTTP, vport);
			else if(MasterProperties.getString("master.viewer.ssl.address").isEmpty())
				svrHttp=viewer_svr.getServers().add(Protocol.HTTPS, vport);
			else
				svrHttp=viewer_svr.getServers().add(Protocol.HTTPS, 
						MasterProperties.getString("master.viewer.ssl.address"), vport);
			parameters=svrHttp.getContext().getParameters();
			parameters.add("maxThreads", "20");
			parameters.add("persistingConnections", "false");
			if(ssl){
				parameters.add("sslContextFactory","org.restlet.ext.ssl.PkixSslContextFactory");
				parameters.add("keystorePath", keystore);
				parameters.add("keystorePassword", keystorepwd);
				parameters.add("keyPassword", keypwd);
				parameters.add("keystoreType", keystoretype);	
			}
			viewer_svr.getDefaultHost().attach("/viewer", new ViewerRestlet(fa));
			viewer_svr.start();
		}
		
		//start timer task to periodically check the last ping back from its 
		//registered file slice servers
		timer=new Timer();
		timer.schedule(new FileSliceServerCheck(lst_sliceServers), 
				MasterProperties.getLong(MasterProperties.PropName.SLICESTORE_CHECK_INTERVAL)*1000,
				MasterProperties.getLong(MasterProperties.PropName.SLICESTORE_CHECK_INTERVAL)*1000);
	}
	
	private void populateMainAppProps(String idaConfigPath, String sysConfigPath) throws Exception{
		//load the properties file containing the IDA info
		MasterProperties.idaProp=new Properties();
		FileInputStream in=new FileInputStream(Resources.findFile(idaConfigPath));
		MasterProperties.idaProp.load(in);
		in.close();
		in=null;

		if(MasterProperties.idaProp.containsKey("ida.current.version")){
			MasterProperties.set(MasterProperties.PropName.CURR_IDA_VERSION, 
					Integer.parseInt(MasterProperties.idaProp.get("ida.current.version").toString()));
			MasterProperties.idaProp.remove("ida.current.version");
		}else
			throw new IOException("Missing current version property in IDA config file.");
		
		MasterProperties.load(Resources.findFile(sysConfigPath));
		
		//sysProp.load(this.getClass().getResourceAsStream("/MasterConfig.properties"));
	}
	
	public void shutdown() throws Exception{
		if(file_svr!=null && file_svr.isStarted()){
			file_svr.stop();
			file_svr=null;
		}
		
		if(namespace_svr!=null && namespace_svr.isStarted()){
			namespace_svr.stop();
			namespace_svr=null;
		}
		
		if(maintainence_svr!=null && maintainence_svr.isStarted()){
			maintainence_svr.stop();
			maintainence_svr=null;
		}
		
		if(viewer_svr!=null && viewer_svr.isStarted()){
			viewer_svr.stop();
			viewer_svr=null;
		}
		
		if(timer!=null){
			timer.cancel();
			timer=null;
		}
		
		if(recovery!=null){
			recovery.stop();
			recovery=null;
		}
		
		if(chgMode!=null){
			chgMode.stop();
			chgMode=null;
		}
		
		if(transLog!=null){
			transLog.closeLogForWrite();
			transLog=null;
		}
		
		if(namespaceLog!=null){
			namespaceLog.closeLogForWrite();
			namespaceLog=null;
		}
	}
}
