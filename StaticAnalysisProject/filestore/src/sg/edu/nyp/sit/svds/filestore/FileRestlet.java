package sg.edu.nyp.sit.svds.filestore;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.StreamCorruptedException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.*;
import org.restlet.data.*;
import org.restlet.representation.*;
//import org.restlet.resource.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.RestletFileSliceServerQueryPropName;

public class FileRestlet extends Restlet {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(FileRestlet.class);
			
	private FileAction fa=null;
	private String serverId=null;
	private ISliceStoreClientAuthentication ssa=null;
	
	public FileRestlet(String path, String serverId){
		super();
		
		fa=new FileAction(path);
		this.serverId=serverId;
		ssa=SliceStoreClientAuthenticationFactory.getInstance();
	}

	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		//System.out.println("received request");
		
		try{
			if(!ssa.authenticateClient(request.getResourceRef().getQuery()+"|"+serverId)){
			//if(!verifySignature(request.getResourceRef().getQuery())){
				response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, "Signature does not match.");
				return;
			}
		}catch(Exception ex){
			LOG.error(ex);
			return;
		}

		if(reqType.equalsIgnoreCase("PUT")){
			//http://localhost:8010/svds/put?slicename=abc&checksum=123&offset=<number>|APPEND 
			storeFile(request, response);
		}else if(reqType.equalsIgnoreCase("GET")){
			//http://localhost:8010/svds/get?slicename=abc&offset=<number>&len=<number>
			getFile(request, response);
		}else if(reqType.equalsIgnoreCase("DELETE")){
			//http://localhost:8010/svds/delete?slicename=abc
			delFile(request, response);
		}else if(reqType.equalsIgnoreCase("HGET")){
			//http://localhost:8010/svds/hget?slicename=abc
			getFileHashes(request, response);
		}else if(reqType.equalsIgnoreCase("HPUT")){
			//http://localhost:8010/svds/hput?slicename=abc
			storeFileHashes(request, response);
		}else{
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "No such method");
		}
	}
	
	//note that query must be in url encoded form
	/*
	private boolean verifySignature(String query){
		LOG.debug("Req verification: " + SliceStoreProperties.getString(SliceStoreProperties.REQUEST_VERIFY_PROP));
		if(SliceStoreProperties.getString(SliceStoreProperties.REQUEST_VERIFY_PROP).equalsIgnoreCase("off"))
			return true;
		
		try{
			int signPos=query.indexOf(SliceStoreProperties.SIGNATURE_QUERY_PROP+"=");
			
			//signature value does not exist, return false
			String signature=null;
			if(signPos==-1)
				return false;
			else if(signPos==0){
				//signature value at the front
				signature=query.substring(SliceStoreProperties.SIGNATURE_QUERY_PROP.length()+1
						, query.indexOf("&"));
				query=query.substring(query.indexOf("&"));
			}else{
				//signature value at the end
				signature=query.substring(signPos+SliceStoreProperties.SIGNATURE_QUERY_PROP.length()+1);
				query=query.substring(0, signPos-1);
			}
			
			String calSignature=Resources.computeSignature(query,
					SliceStoreProperties.getString(SliceStoreProperties.SLICE_STORE_KEY_PROP+"."+serverId));
			
			LOG.debug("client query: " + query + "\n"+"cal sign: " + calSignature + "\nget sign: " + signature);
			
			return calSignature.equals(signature);
		}catch(Exception ex){
			LOG.error(ex);
			return false;
		}
	}
	*/
	
	public void storeFileHashes(org.restlet.Request request, org.restlet.Response response){	
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		String sliceName=null;
		
		try{
			sliceName=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.NAME.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(sliceName==null || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			fa.storeFileHashes(sliceName, request.getEntity().getStream());
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	public void getFileHashes(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		String sliceName=null;
		
		try{
			sliceName=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.NAME.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(sliceName==null || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		InputStream in=fa.getFileHashes(sliceName);
		if(in==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "File does not exist.");
		}else{
			response.setEntity(new InputRepresentation(in, MediaType.ALL));
		}
	}
	
	public void getFile(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		String sliceName=null, strOffset=null, strLen=null, strBlkSize=null;
		long offset=0L;
		int len=0, blkSize=0;
		
		try{
			sliceName=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.NAME.value(), true, null);
			strOffset=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.OFFSET.value(), true, null);
			strLen=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.LENGTH.value(), true, null);
			strBlkSize=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.FILE_BLKSIZE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(sliceName==null || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		if(strOffset!=null && strOffset.length()>0){
			try{
				offset=Long.parseLong(strOffset);
			}catch(NumberFormatException ex){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
				return;
			}
		}else strOffset=null;
		if(strLen!=null && strLen.length()>0){
			try{
				len=Integer.parseInt(strLen);
			}catch(NumberFormatException ex){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
				return;
			}
		}else strLen=null;
		
		byte[] checksum=null;
		if(strBlkSize!=null && strBlkSize.length()>0){
			try{
				blkSize=Integer.parseInt(strBlkSize);
			}catch(NumberFormatException ex){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
				return;
			}
			
			if(blkSize>0) checksum=new byte[Resources.HASH_BIN_LEN];
		}else strBlkSize=null;

		InputStream in=null;
			                  
		if(strOffset==null && strLen==null){
			in=fa.getFile(sliceName, (strBlkSize==null?0:blkSize), checksum);
		}else{
			in=fa.getFile(sliceName, (strOffset==null?0L:offset), (strLen==null?0:len), (strBlkSize==null?0:blkSize), checksum);
		}
		
		if(in==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "File does not exist.");
		}else{
			if(checksum!=null){
				ByteArrayInputStream chkStream=new ByteArrayInputStream(checksum);
				response.setEntity(new InputRepresentation(new SequenceInputStream(chkStream, in)
					, MediaType.ALL));
			}else
				response.setEntity(new InputRepresentation(in, MediaType.ALL));
		}
	}
	
	public void storeFile(org.restlet.Request request, org.restlet.Response response){
		String sliceName=null, strOffset=null, checksum=null, key=null, strBlk=null;
		boolean isAppend=false;
		long offset=0L;
		int blkSize=0;
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		try{
			sliceName=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.NAME.value(), true, null);
			strOffset=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.OFFSET.value(), true, null);
			checksum=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.CHECKSUM.value(), true, null);
			key=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.FILE_KEYHASH.value(), true, null);
			strBlk=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.FILE_BLKSIZE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(sliceName==null || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		if(checksum!=null){
			if(strBlk==null || strBlk.length()==0){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
				return;
			}

			try{
				blkSize=Integer.parseInt(strBlk);
			}catch(NumberFormatException ex){
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
				return;
			}
			
			if(blkSize<=0){
				response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
				return;
			}
		}
		
		//if offset urlquery not found, default to new file slice created
		if(strOffset!=null && strOffset.length()>0){
			if(strOffset.equalsIgnoreCase("APPEND"))
				isAppend=true;
			else if(!strOffset.equals("0")){
				try{
					offset=Long.parseLong(strOffset);
				}catch(NumberFormatException ex){
					response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
					return;
				}
			}
		}

		try{
			if(isAppend)
				fa.storeFile(sliceName, request.getEntity().getStream(), checksum, key, blkSize);
			else
				fa.storeFile(sliceName, request.getEntity().getStream(), offset, checksum, key, blkSize);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(StreamCorruptedException ex){
			response.setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, "Checksum does not match.");
		}catch(Exception ex){
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	public void delFile(org.restlet.Request request, org.restlet.Response response){
		String sliceName=null;
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		try{
			sliceName=urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Slice.NAME.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
			return;
		}
		
		if(sliceName==null || sliceName.length()==0){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing/Empty arguments.");
			return;
		}
		
		try{
			fa.delFile(sliceName);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, ex.getMessage());
			return;
		}
	}
}
