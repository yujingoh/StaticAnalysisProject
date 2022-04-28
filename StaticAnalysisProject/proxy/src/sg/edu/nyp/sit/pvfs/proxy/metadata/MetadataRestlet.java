package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.InputRepresentation;

import sg.edu.nyp.sit.pvfs.proxy.RequestAuthentication;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class MetadataRestlet extends Restlet {
	private static final Log LOG = LogFactory.getLog(MetadataRestlet.class);
	
	private RequestAuthentication ra;
	private MetadataAction ma;
	
	public MetadataRestlet(RequestAuthentication ra, MetadataAction ma){
		super();
		this.ma=ma;
		this.ra=ra;
	}

	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		String usr=request.getResourceRef().getQueryAsForm()
			.getFirstValue(RestletProxyQueryPropName.Subscriber.ID.value(), true, null);
		int verify;
		if(ra!=null && (verify=ra.checkSignature(usr, request.getResourceRef().getQuery()))<=0){
			if(verify==0)
				response.setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, "Unauthenticated request.");
			else if(verify==-1)
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Remote session does not exist.");
			
			return;
		}
		
		usr=usr.toUpperCase();
		if(reqType.equalsIgnoreCase("SAVE")){
			//http://localhost/metadata/save?id=xxx&dt=aaaa&mem=1024000&s=yyy
			saveMetadata(request, response, usr);
		}else if(reqType.equalsIgnoreCase("GET")){
			//http://localhost/metadata/get?id=xxx&dt=aaaa&s=yyy
			getMetadata(request, response, usr);
		}else{
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Not a valid request");
		}
	}
	
	private void saveMetadata(org.restlet.Request request, org.restlet.Response response, String id){
		Form urlQuery=request.getResourceRef().getQueryAsForm();

		String memStr, idaStr, driveName;
		try{
			memStr=urlQuery.getFirstValue(RestletProxyQueryPropName.Metadata.MEMORY.value(), true, null);
			idaStr=urlQuery.getFirstValue(RestletProxyQueryPropName.Metadata.CURR_IDA_VERSION.value(), true, null);
			driveName=urlQuery.getFirstValue(RestletProxyQueryPropName.Metadata.DRIVE_NAME.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(memStr==null || idaStr==null || driveName==null || memStr.length()==0 || idaStr.length()==0 || driveName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		long memory;
		int currIdaVersion;
		try{
			memory=Long.parseLong(memStr);
			currIdaVersion=Integer.parseInt(idaStr);
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}

		try{
			ma.saveSubscriberDB(id, memory, currIdaVersion, driveName, request.getEntity().getStream());
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(IOException ex){
			LOG.error(ex);
			response.setStatus(Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getMetadata(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			InputStream in=ma.getSubscriberDB(id);
			if(in==null)
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "File does not exist.");
			else
				response.setEntity(new InputRepresentation(in, MediaType.ALL));
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
}
