package sg.edu.nyp.sit.svds.master.namespace;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.security.cert.X509Certificate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.*;
import org.restlet.data.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.exception.NotSupportedSVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.file.IMasterFileAuthentication;
import sg.edu.nyp.sit.svds.master.file.MasterFileAuthenticationFactory;
import sg.edu.nyp.sit.svds.master.filestore.FileSliceStoreFactory;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class NamespaceRestlet extends Restlet{
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(NamespaceRestlet.class);
	
	private NamespaceAction na=null;
	private IMasterFileAuthentication auth=null;
	
	private boolean ssl, clientAuthentication;
	
	public NamespaceRestlet(NamespaceAction na){
		super();
		
		this.na=na;
		//for checking when retrieving file store key
		this.auth=MasterFileAuthenticationFactory.getInstance();
		
		ssl=(MasterProperties.getString("master.namespace.ssl").equalsIgnoreCase("off") ? false : true);
		clientAuthentication=(MasterProperties.getString("master.namespace.ssl.clientauth").equalsIgnoreCase("on") ? true: false);
	}
	
	private boolean checkClientCert(org.restlet.Request request){
		if(!ssl)
			return true;
		
		if(clientAuthentication){
			@SuppressWarnings({ "unchecked", "rawtypes" })
			List<X509Certificate> certs =
				(List)request.getAttributes().get("org.restlet.https.clientCertificates");
			
			if(certs==null || certs.size()==0)
				return false;
		}
		
		return true;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		//authentication
		if(reqType.equalsIgnoreCase("KEY") || reqType.equalsIgnoreCase("ACCESSURL")){
			//only client will request key from the slice server
			try{
				if(auth!=null && !auth.authenticateClient(request.getResourceRef().getQuery())){
					response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, "Not a valid user.");
					return;
				}
			}catch(Exception ex){
				LOG.error(ex);
				return;
			}
		}else if(reqType.equalsIgnoreCase("MEM")){
			//no authentication needed
		}else{
			if(!checkClientCert(request)){
				response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, "Not a valid user.");
				return;
			}
		}

		if(reqType.equalsIgnoreCase("ACCESSURL")){
			//http://localhost:9000/namespace/accessurl?id=1&slice=abc
			getFileSliceServerAccessURL(request, response);
		}else if(reqType.equalsIgnoreCase("REGISTER")){
			//http://localhost:9000/namespace/register?
			//name=urn:nyp.edu.sg,urn:sit.nyp.edu.sg&id=1&ip=172.20.134.102:8080&svr_type=1&mem=1024,1024&req_ver=off
			registerNamespaces(request, response);
		}else if(reqType.equalsIgnoreCase("REMOVE")){
			//http://localhost:9000/namespace/remove?id=1&name=urn:nyp.edu.sg,urn:sit.nyp.edu.sg
			removeNamespaces(request, response);
		}else if(reqType.equalsIgnoreCase("AVAILABLE")){
			//http://localhost:9000/namespace/available?name=urn:nyp.edu.sg
			getFileSliceServers(request, response);
		}else if(reqType.equalsIgnoreCase("ALIVE")){
			//http://localhost:9000/namespace/alive?id=1
			checkFileSliceServers(request, response);
		}else if(reqType.equalsIgnoreCase("MEM")){
			//http://localhost:9010/namespace/mem?name=urn:nyp.edu.sg
			checkMemory(request, response);
		}else if(reqType.equalsIgnoreCase("GET_SS")){
			//http://localhost:9000/namespace/get?id=1
			getFileSliceServer(request, response);
		}else if(reqType.equalsIgnoreCase("UPDATE_SS")){
			//http://localhost:9000/namespace/update?id=1
			updateFileSliceServer(request, response);
		}else if(reqType.equalsIgnoreCase("KEY")){
			//http://localhost:9010/namesapce/key?id=1
			getFileSliceServerKey(request, response);
		}
	}
	
	private void getFileSliceServerAccessURL(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String serverId, sliceName;
		try{
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
			sliceName=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SLICE_NAME.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(serverId==null || sliceName==null ||	serverId.length()==0 || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			FileSliceServerInfo fssi=na.resolveFileSliceServer(serverId);
			
			if(fssi==null){
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Slice server not found.");
				return;
			}
			
			IFileSliceStore fs=FileSliceStoreFactory.getInstance(fssi.getType());
			
			String resp="";
			for(String tmp: fs.getAccessURL(fssi, sliceName)){
				resp+=tmp+"\n";
			}
			
			response.setEntity(resp, MediaType.TEXT_PLAIN);
		}catch(NotSupportedSVDSException ex){
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Access URL not applicable for this file slice server");
		}catch(Exception ex){
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void updateFileSliceServer(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String serverId, ipAddr, strType, strMode;
		try{
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
			ipAddr=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_HOST.value(), true, null);
			strType=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_TYPE.value(), true, null);
			strMode=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_MODE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		FileSliceServerInfo.Type type;
		FileIOMode mode;
		if(serverId==null || ipAddr==null || strType==null || strMode==null || 
				serverId.length()==0 || ipAddr.length()==0 || strType.length()==0 || 
				strMode.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			type=FileSliceServerInfo.Type.valueOf(Integer.parseInt(strType));
			mode=FileIOMode.valueOf(Integer.parseInt(strMode));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid type/mode.");
			return;
		}
		
		if(mode==null || mode==FileIOMode.NONE || mode==FileIOMode.BOTH || type==null){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid mode/type/status.");
			return;
		}

		try{
			Properties data=null;
			InputStream in=request.getEntity().getStream();
			if(in!=null){
				data=new Properties();
				data.load(new BufferedReader(new InputStreamReader(in)));
			}
			
			na.updateFileSliceServer(serverId, ipAddr, type, mode, data, true);
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, ex.getMessage());
		}catch(Exception ex){
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getFileSliceServer(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String serverId;
		try{
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(serverId==null || serverId.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			FileSliceServerInfo fssi=na.resolveFileSliceServer(serverId);
			
			if(fssi==null){
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Slice server not found.");
				return;
			}
			
			StringBuilder resp=new StringBuilder();
			
			resp.append(FileSliceServerInfo.PropName.HOST.value()+"="+fssi.getServerHost()+"\n");
			resp.append(FileSliceServerInfo.PropName.MODE.value()+"="+fssi.getMode().value()+"\n");
			resp.append(FileSliceServerInfo.PropName.STATUS.value()+"="+fssi.getStatus().value()+"\n");
			resp.append(FileSliceServerInfo.PropName.TYPE.value()+"="+fssi.getType().value()+"\n");
			
			if(fssi.getType()!=FileSliceServerInfo.Type.RESTLET){
				if(fssi.getKeyId()!=null) resp.append(FileSliceServerInfo.PropName.KEYID.value()+"="+Resources.encodeKeyValue(fssi.getKeyId())+"\n");
				if(fssi.getKey()!=null) resp.append(FileSliceServerInfo.PropName.KEY.value()+"="+Resources.encodeKeyValue(fssi.getKey())+"\n");
			}

			if(fssi.hasProperties()){
				for(@SuppressWarnings("rawtypes") Map.Entry k: fssi.getAllProperties().entrySet()){
					resp.append(FileSliceServerInfo.PropName.OPT.value()+"."+k.getKey().toString()+"="
						+Resources.encodeKeyValue(k.getValue().toString())+"\n");
				}
			}
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getFileSliceServerKey(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String serverId;
		try{
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(serverId==null || serverId.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			String key=na.getFileSliceServerKey(serverId);
			if(key!=null)
				response.setEntity(key, MediaType.TEXT_PLAIN);
			else
				response.setStatus(Status.SUCCESS_NO_CONTENT);
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, ex.getMessage());
		}
	}
	
	private void checkMemory(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace=null;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.NAMESPACE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || namespace.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			NamespaceInfo ni=na.getNamespaceMemory(namespace);
			
			if(ni==null){
				response.setStatus(Status.SUCCESS_NO_CONTENT, "No namespace information found.");
				//response.setEntity("OK", MediaType.TEXT_PLAIN);
				return;
			}
			
			response.setEntity(NamespaceInfo.PropName.AVA_MEM.value()+"="+ni.getMemoryAvailable()+"\n"
					+NamespaceInfo.PropName.USED_MEM.value()+"="+ni.getMemoryUsed()
					, MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void removeNamespaces(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespaceStr, serverId;
		try{
			namespaceStr=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.NAMESPACE.value(), true, null);
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(namespaceStr==null || serverId==null 
				|| namespaceStr.length()==0 || serverId.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		ArrayList<String> namespaces=new ArrayList<String>();
		if(namespaceStr.indexOf(",")==-1)
			namespaces.add(namespaceStr);
		else
			namespaces.addAll(Arrays.asList(namespaceStr.split(",")));
		
		try{
			na.removeNamespaces((String[])namespaces.toArray(new String[namespaces.size()]), serverId, true);
			LOG.info("Removed file slice server! - " + namespaceStr + " from " + serverId);
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	//Sender: file slice servers
	//Purpose: To inform the master table that it is still alive; and therefore able to 
	//			service request.
	private void checkFileSliceServers(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String serverId;
		try{
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(serverId==null || serverId.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			na.checkFileSliceServer(serverId);
			LOG.info("File slice server is alive! - " + serverId);
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, ex.getMessage());
		}
	}
	
	//Sender: application
	//Purpose: To get the list of available file slice servers so as to send request
	//			to store the file slices later.
	private void getFileSliceServers(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.NAMESPACE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || namespace.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			StringBuilder resp=new StringBuilder();
			int cnt=0;
			for(FileSliceServerInfo i: na.getFileSliceServers(namespace, FileIOMode.BOTH)){
				resp.append(FileSliceServerInfo.PropName.ID.value()+cnt+"="+i.getServerId()+"\n");
				resp.append(FileSliceServerInfo.PropName.TYPE.value()+cnt+"="+i.getType().value()+"\n");
				resp.append(FileSliceServerInfo.PropName.HOST.value()+cnt+"="+i.getServerHost()+"\n");
				cnt++;
			}
			resp.append(FileSliceServerInfo.PropName.COUNT+"="+cnt+"\n");
	
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, ex.getMessage());
		}
	}
	
	//Sender: file slice servers
	//Purpose: To inform the master table that it is started up and will be able to service
	//			request.
	private void registerNamespaces(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespaceStr, serverId, ipAddr, strType, memStr, strVerOpt, strMode;
		try{
			namespaceStr=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.NAMESPACE.value(), true, null);
			serverId=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
			ipAddr=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_HOST.value(), true, null);
			strType=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_TYPE.value(), true, null);
			strMode=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_MODE.value(), true, null);
			strVerOpt=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value(), true, null);
			
			memStr=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.MEM.value(), true, null); //optional property
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		FileSliceServerInfo.Type type;
		FileIOMode mode;
		if(namespaceStr==null || serverId==null || ipAddr==null || strType==null
				|| strMode==null || namespaceStr.length()==0 || serverId.length()==0 
				|| ipAddr.length()==0 || strType.length()==0 || strMode.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			type=FileSliceServerInfo.Type.valueOf(Integer.parseInt(strType));
			mode=FileIOMode.valueOf(Integer.parseInt(strMode));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid type.");
			return;
		}
		
		if(mode==null || mode==FileIOMode.NONE || mode==FileIOMode.BOTH || type==null){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid mode/type.");
			return;
		}
		
		if(type==FileSliceServerInfo.Type.RESTLET){
			if(strVerOpt==null || strVerOpt.length()==0){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
				return;
			}
		}else
			strVerOpt="off";
		
		long[] avaMem=null;
		if(memStr!=null && memStr.length()>0){
			try{
				if(memStr.indexOf(",")!=-1){
					String[] tmp=memStr.split(",");
					avaMem=new long[tmp.length];
					for(int i=0; i<avaMem.length; i++)
						avaMem[i]=Long.parseLong(tmp[i]);
				}else{
					avaMem=new long[]{Long.parseLong(memStr)};
				}
			}catch(NumberFormatException ex){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid memory length.");
				return;
			}
		}
		
		ArrayList<String> namespaces=new ArrayList<String>();
		if(namespaceStr.indexOf(",")==-1)
			namespaces.add(namespaceStr);
		else
			namespaces.addAll(Arrays.asList(namespaceStr.split(",")));
		
		if(avaMem!=null && avaMem.length!=namespaces.size()){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Number of memory length does not match with number of registered namespace(s).");
			return;
		}
		
		try{
			boolean verOpt=(strVerOpt.equalsIgnoreCase("off")?false:true);
			LOG.debug(serverId + " req key @ restlet: " + verOpt);
			Properties data=null;
			InputStream in=request.getEntity().getStream();
			if(in!=null){
				data=new Properties();
				data.load(new BufferedReader(new InputStreamReader(in)));
			}
			
			String key=na.registerNamespaces((String[])namespaces.toArray(new String[namespaces.size()]), 
					(avaMem==null? null : avaMem), serverId, ipAddr, type, true, verOpt, mode, data);
			
			LOG.info("Registered file slice server! - " + namespaceStr + " to " + serverId);

			response.setEntity((verOpt?key:"OK"), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
}
