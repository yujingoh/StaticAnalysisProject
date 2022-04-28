package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;

import sg.edu.nyp.sit.pvfs.proxy.RequestAuthentication;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class SliceStoresRestlet extends Restlet {
	private static final Log LOG = LogFactory.getLog(SliceStoresRestlet.class);
	
	private Map<String, SubscriberMetadataInfo> lst_usr;
	private RequestAuthentication ra;
	
	public SliceStoresRestlet(RequestAuthentication ra, Map<String, SubscriberMetadataInfo> lst_usr){
		this.lst_usr=lst_usr;
		this.ra=ra;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		String usr=request.getResourceRef().getQueryAsForm()
			.getFirstValue(RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value(), true, null);
		int verify;
		if(ra!=null && (verify=ra.checkSignature(usr, request.getResourceRef().getQuery()))<=0){
			if(verify==0)
				response.setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, "Unauthenticated request.");
			else if(verify==-1)
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Remote session does not exist.");
			
			return;
		}
		
		usr=usr.toUpperCase();
		if(lst_usr.get(usr).isSessionEnded()){
			response.setStatus(Status.CLIENT_ERROR_FORBIDDEN, "Remote session has ended.");
			return;
		}
		
		if(reqType.equalsIgnoreCase("REGISTER")){
			//http://localhost:6010/slicestore/register?user=VIC&id=testfs&host=localhost&type=1&mode=0&keyid=uuu&key=bb&status=1&s=bbb
			registerSliceStore(request, response, usr);
		}else if(reqType.equalsIgnoreCase("REMOVE")){
			//http://localhost:6010/slicestore/remove?user=VIC&id=testfs&s=bbb
			removeSliceStore(request, response, usr);
		}else if(reqType.equalsIgnoreCase("AVAILABLE")){
			//http://localhost:6010/slicestore/available?user=VIC&s=bbb
			getAllSliceStores(request, response, usr);
		}else if(reqType.equalsIgnoreCase("GET")){
			//http://localhost:6010/slicestore/get?user=VIC&id=testfs&s=bbb
			getSliceStore(request, response, usr);
		}else if(reqType.equalsIgnoreCase("MEM")){
			//http://localhost:6010/slicestore/mem?user=VIC&s=bbb
			getMemory(request, response, usr);
		}else if(reqType.equalsIgnoreCase("GET_NS")){
			//http://localhost:6010/slicestore/get_ns?user=VIC&s=bbb
			getDrivename(request, response, usr);
		}else{
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Not a valid request");
		}
	}
	
	private void getDrivename(org.restlet.Request request, org.restlet.Response response, String usr){
		try{
			response.setEntity(lst_usr.get(usr).getDriveName(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getMemory(org.restlet.Request request, org.restlet.Response response, String usr){
		try{
			response.setEntity(lst_usr.get(usr).getMemoryUsed()+"", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getSliceStore(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String svrId;
		try{
			svrId=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(svrId==null || svrId.length()==0 ){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			FileSliceServerInfo fss=lst_usr.get(usr).getSliceStoresAction().getSliceStore(svrId);
			
			if(fss==null){
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Slice store not found.");
				return;
			}
			
			StringBuilder resp=new StringBuilder();
			
			resp.append(FileSliceServerInfo.PropName.ID.value()+"="+Resources.encodeKeyValue(fss.getServerId())+"\n");
			resp.append(FileSliceServerInfo.PropName.HOST.value()+"="+Resources.encodeKeyValue(fss.getServerHost())+"\n");
			resp.append(FileSliceServerInfo.PropName.TYPE.value()+"="+fss.getType().value()+"\n");
			resp.append(FileSliceServerInfo.PropName.MODE.value()+"="+fss.getMode().value()+"\n");
			resp.append(FileSliceServerInfo.PropName.STATUS.value()+"="+fss.getStatus().value()+"\n");
			if(fss.getKeyId()!=null) resp.append(FileSliceServerInfo.PropName.KEYID.value()+"="+Resources.encodeKeyValue(fss.getKeyId())+"\n");
			if(fss.getKey()!=null) resp.append(FileSliceServerInfo.PropName.KEY.value()+"="+Resources.encodeKeyValue(fss.getKey())+"\n");
			
			if(fss.hasProperties()){
				for(@SuppressWarnings("rawtypes") Map.Entry k: fss.getAllProperties().entrySet()){
					resp.append(FileSliceServerInfo.PropName.OPT.value()+"."+k.getKey().toString()+"="
							+Resources.encodeKeyValue(k.getValue().toString())+"\n");
				}
			}
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getAllSliceStores(org.restlet.Request request, org.restlet.Response response, String usr){
		try{
			List<FileSliceServerInfo> stores=lst_usr.get(usr).getSliceStoresAction().getSliceStoresBasic();
			
			if(stores==null){
				response.setEntity(FileSliceServerInfo.PropName.COUNT.value()+"=0", MediaType.TEXT_PLAIN);
				return;
			}
			
			StringBuilder resp=new StringBuilder();
			
			resp.append(FileSliceServerInfo.PropName.COUNT.value()+"="+stores.size()+"\n");
			int cnt=0;
			for(FileSliceServerInfo fss: stores){
				resp.append(FileSliceServerInfo.PropName.ID.value()+cnt+"="+Resources.encodeKeyValue(fss.getServerId())+"\n");
				resp.append(FileSliceServerInfo.PropName.STATUS.value()+cnt+"="+fss.getStatus().value()+"\n");
				cnt++;
			}
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void removeSliceStore(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String svrId;
		try{
			svrId=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(svrId==null || svrId.length()==0 ){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			int success=lst_usr.get(usr).getSliceStoresAction().removeSliceStore(svrId);
			
			if(success==1)
				response.setEntity("OK", MediaType.TEXT_PLAIN);
			else if(success==0)
				response.setStatus(Status.CLIENT_ERROR_CONFLICT, "Slice store is in use.");
			else
				response.setStatus(Status.SERVER_ERROR_INTERNAL);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void registerSliceStore(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String svrId, host, typeStr, modeStr, keyId, key, statusStr;
		FileSliceServerInfo.Type type;
		FileIOMode mode;
		FileSliceServerInfo.Status status;
		try{
			svrId=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_ID.value(), true, null);
			host=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_HOST.value(), true, null);
			typeStr=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_TYPE.value(), true, null);
			modeStr=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_MODE.value(), true, null);
			keyId=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_KEY_ID.value(), true, null);
			key=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_KEY.value(), true, null);
			statusStr=urlQuery.getFirstValue(RestletProxyQueryPropName.SliceStore.SVR_STATUS.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}

		if(svrId==null || host==null || typeStr==null || modeStr==null || statusStr==null ||
				svrId.length()==0 || host.length()==0 || typeStr.length()==0 || modeStr.length()==0 || statusStr.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		try{
			type=FileSliceServerInfo.Type.valueOf(Integer.parseInt(typeStr));
			mode=FileIOMode.valueOf(Integer.parseInt(modeStr));
			status=FileSliceServerInfo.Status.valueOf(Integer.parseInt(statusStr));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}

		if(mode==null || (mode!=FileIOMode.STREAM && mode!=FileIOMode.NON_STREAM) || type==null || status==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}

		try{
			Properties data=new Properties();
			data.load(new BufferedReader(new InputStreamReader(request.getEntity().getStream())));
			
			lst_usr.get(usr).getSliceStoresAction().registerSliceStore(svrId, host, type, mode, keyId, key, status, data);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
}
