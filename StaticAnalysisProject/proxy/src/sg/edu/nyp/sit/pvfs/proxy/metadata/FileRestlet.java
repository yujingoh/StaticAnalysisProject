package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;

import sg.edu.nyp.sit.pvfs.proxy.RequestAuthentication;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.IdaInfo;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class FileRestlet extends Restlet {
	private static final Log LOG = LogFactory.getLog(FileRestlet.class);
	
	private Map<String, SubscriberMetadataInfo> lst_usr;
	private RequestAuthentication ra;
	
	public FileRestlet(RequestAuthentication ra, Map<String, SubscriberMetadataInfo> lst_usr){
		this.lst_usr=lst_usr;
		this.ra=ra;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		String usr=request.getResourceRef().getQueryAsForm()
			.getFirstValue(RestletProxyQueryPropName.File.SUBSCRIBER.value(), true, null);
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
		
		if(reqType.equalsIgnoreCase("REFRESH")){
			//http://localhost:9000/file/refresh?user=VIC&path=/def&lastChk=1234578910&s=md374d64b93b52
			refreshDirFiles(request, response, usr);
		}else if(reqType.equalsIgnoreCase("GET")){
			//http://localhost:9000/file/get?user=VIC&path=/abc/secret.doc&s=md374d64b93b52
			getFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("LOCK")){
			//http://localhost:9000/file/lock?user=VIC&path=/def/secret.doc&by=XXX&s=md374d64b93b52
			lockFileInfo(request, response, true, usr);
		}else if(reqType.equalsIgnoreCase("UNLOCK")){
			//http://localhost:9000/file/unlock?user=VIC&path=/def/secret.doc&by=XXX&s=md374d64b93b52
			lockFileInfo(request, response, false, usr);
		}else if(reqType.equalsIgnoreCase("ACCESS")){
			//http://localhost:9000/file/access?user=VIC&path=/def/secret.doc&s=md374d64b93b52
			accessFile(request, response, usr);
		}else if(reqType.equalsIgnoreCase("GENERATE")){
			//http://lhttp://localhost:9000/file/generate?user=VIC&size=2&mode=1&s=md374d64b93b52
			genFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("ADD")){
			//http://localhost:9000/file/add?user=VIC&path=/abc/secret.doc&size=100&version=1& blksize=102400&key=abc&type=file&s=md374d64b93b52
			addFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("UPDATE")){
			//http://localhost:9000/file/update?user=VIC&path=/abc/secret.doc&size=100& blksize=102400&key=abc&by=XXX&s=md374d64b93b52
			updateFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("MOVE")){
			//http://localhost:9000/file/move?user=VIC&old_filename=/abc/secret.doc&path=/def/secret.doc &s=md374d64b93b52
			moveFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("DELETE")){
			//http://localhost:9000/file/delete?user=VIC&path=/def/secret.doc&type=0&s=md374d64b93b52
			deleteFileInfo(request, response, usr);
		}else if(reqType.equalsIgnoreCase("LIST")){
			//http://localhost:9000/file/list?user=VIC&path=/def&s=md374d64b93b52
			getDirectoryFiles(request, response, usr);
		}else{
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Not a valid request");
		}
	}
	
	private void getDirectoryFiles(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(path==null || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		List<String> lst=null;
		try{
			lst=lst_usr.get(usr).getFileAction().getDirectoryFiles(path);
			
			if(lst!=null && lst.size()>0){
				StringBuilder resp=new StringBuilder();
				for(String p:lst){
					resp.append(p+"\n");
				}
				response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
			}else{
				response.setStatus(Status.SUCCESS_NO_CONTENT);
			}
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void deleteFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String strType, path;
		FileInfo.Type type=null;
		try{
			strType=urlQuery.getFirstValue(RestletProxyQueryPropName.File.TYPE.value(), true, null);
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(strType==null || strType.length()==0 || path==null || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		try{
			type=FileInfo.Type.valueOf(Integer.parseInt(strType));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(type==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		try{
			if(type==FileInfo.Type.FILE){
				lst_usr.get(usr).getFileAction().deleteFileInfo(path);
			}else{
				lst_usr.get(usr).getFileAction().deleteDirectoryInfo(path);
			}
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(IllegalArgumentException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
			return;
		}catch(RejectedExecutionException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
			return;
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void moveFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path, new_path;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.OLD_PATH.value(), true, null);
			new_path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(new_path==null || path==null || new_path.length()==0 || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			Date lastAccessedDate=lst_usr.get(usr).getFileAction().moveFileInfo(path, new_path);
			System.out.println("move file okie!");
			response.setEntity(lastAccessedDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(RejectedExecutionException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void updateFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path, strSize, strBy, keyHash;
		String strBlkSize;
		long size=0;
		int blkSize=0;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
			strSize=urlQuery.getFirstValue(RestletProxyQueryPropName.File.SIZE.value(), true, null);
			strBy=urlQuery.getFirstValue(RestletProxyQueryPropName.File.USER.value(), true, null);
			strBlkSize=urlQuery.getFirstValue(RestletProxyQueryPropName.File.FILE_BLKSIZE.value(), true, null);
			keyHash=urlQuery.getFirstValue(RestletProxyQueryPropName.File.FILE_KEYHASH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(strSize==null || strBy==null || strBlkSize==null ||
				strSize.length()==0 || strBy.length()==0 
				|| strBlkSize.length()==0 || path==null || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			size=Long.parseLong(strSize);
			blkSize=Integer.parseInt(strBlkSize);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid size.");
			return;
		}
		
		try{
			Date lastModifiedDate=null;
			
			Properties data=new Properties();
			data.load(new BufferedReader(new InputStreamReader(request.getEntity().getStream())));
			
			if(!data.containsKey(FileSliceInfo.PropName.COUNT.value()))
				throw new NoSuchFieldException("Missing slice count parameter.");
			
			lastModifiedDate=lst_usr.get(usr).getFileAction().updateFileInfo(path, size, blkSize, keyHash, processFileSlices(data), strBy);
			
			response.setEntity(lastModifiedDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(RejectedExecutionException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void addFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String strSize;
		String path, strType, strVersion, keyHash, strBlkSize;
		long size=0;
		int version=0, blkSize=0;
		FileInfo.Type type=null;
		try{
			strType=urlQuery.getFirstValue(RestletProxyQueryPropName.File.TYPE.value(), true, null);
			
			strSize=urlQuery.getFirstValue(RestletProxyQueryPropName.File.SIZE.value(), true, null);
			strVersion=urlQuery.getFirstValue(RestletProxyQueryPropName.File.IDA_VERSION.value(), true, null);
			strBlkSize=urlQuery.getFirstValue(RestletProxyQueryPropName.File.FILE_BLKSIZE.value(), true, null);
			keyHash=urlQuery.getFirstValue(RestletProxyQueryPropName.File.FILE_KEYHASH.value(), true, null);

			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(strType==null || path==null || path.length()==0 || strType.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			//long timestamp=Long.parseLong(strTimeStamp);
			//creationDate=new Date(timestamp);
			type=FileInfo.Type.valueOf(Integer.parseInt(strType));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(type==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}else if(type==FileInfo.Type.FILE){
			if(strSize==null || strVersion==null ||
					strSize.length()==0 || strVersion.length()==0){
				response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
				return;
			}
		
			try{
				size=Long.parseLong(strSize);
				version=Integer.parseInt(strVersion);
				if(strBlkSize!=null && strBlkSize.length()>0)
					blkSize=Integer.parseInt(strBlkSize);
			}catch(Exception ex){
				response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid number for size, version or padding.");
				return;
			}
		}
		
		try{
			Date creationDate=null;
			
			if(type==FileInfo.Type.FILE){
				Properties data=null;
				if(size>0){
					data=new Properties();
					data.load(new BufferedReader(new InputStreamReader(request.getEntity().getStream())));
					
					if(!data.containsKey(FileSliceInfo.PropName.COUNT.value())){
						response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing slice count parameter.");
						return;
					}
				}
				
				creationDate=lst_usr.get(usr).getFileAction().addFileInfo(path, version, size, blkSize, (blkSize==0? null: keyHash), 
						(size>0 ? processFileSlices(data) : null));
			}else{
				creationDate=lst_usr.get(usr).getFileAction().addDirectoryInfo(path);
			}
			
			response.setEntity(creationDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void genFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String strReq, strPref;
		int req;
		FileIOMode pref;
		try{
			strReq=urlQuery.getFirstValue(RestletProxyQueryPropName.File.SIZE.value(), true, null);
			strPref=urlQuery.getFirstValue(RestletProxyQueryPropName.File.MODE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(strReq==null || strPref==null || strReq.length()==0 || strPref.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		try{
			req=Integer.parseInt(strReq);
			pref=FileIOMode.valueOf(Integer.parseInt(strPref));
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid size.");
			return;
		}
		
		if(pref==null || pref==FileIOMode.NONE){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid mode.");
			return;
		}
		
		List<FileSliceServerInfo> servers=new ArrayList<FileSliceServerInfo>();
		FileSliceInfo[] slices=null;
		try{
			slices=lst_usr.get(usr).getFileAction().genFileInfo(req, pref, servers);
			
			StringBuilder resp=new StringBuilder();
			int cnt=0;
			for(FileSliceInfo i: slices){
				resp.append(FileSliceInfo.PropName.NAME.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceName())+"\n");
				resp.append(FileSliceInfo.PropName.SVR.value()+cnt+"="+Resources.encodeKeyValue(i.getServerId())+"\n");
				cnt++;
			}
			
			//include the server mapping to be piggy back
			writeFileSliceServers(servers, resp);
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			nex.printStackTrace();
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}finally{
			slices=null;
			servers.clear();
			servers=null;
		}
	}
	
	private void accessFile(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(path==null || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			lst_usr.get(usr).getFileAction().updateFileInfoLastAccessed(path);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void lockFileInfo(org.restlet.Request request, org.restlet.Response response, boolean lock, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path, by;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
			by=urlQuery.getFirstValue(RestletProxyQueryPropName.File.USER.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(path==null || by==null || path.length()==0 || by.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			if(lock)
				lst_usr.get(usr).getFileAction().lockFileInfo(path, by);
			else
				lst_usr.get(usr).getFileAction().unlockFileInfo(path, by);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(RejectedExecutionException rex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, rex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void getFileInfo(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(path==null || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		FileInfo fi=null;
		List<FileSliceServerInfo> mappings=new ArrayList<FileSliceServerInfo>();
		try{
			fi=lst_usr.get(usr).getFileAction().getFileInfo(path, mappings);
			
			StringBuilder resp=new StringBuilder();
			
			int idaVersion=(fi==null || fi.getType()!=FileInfo.Type.FILE ?
					lst_usr.get(usr).getCurrIdaVersion()
					: fi.getIdaVersion());
			
			resp.append(FileInfo.PropName.IDA_VERSION.value()+"=" + idaVersion + "\n");
			IdaInfo ida=lst_usr.get(usr).getFileAction().getIdaInfo(idaVersion);
			resp.append(IdaInfo.PropName.MATRIX.value()+"="+Resources.encodeKeyValue(ida.getStrMatrix())+"\n");
			resp.append(IdaInfo.PropName.SHARES.value()+"="+ida.getShares()+"\n");
			resp.append(IdaInfo.PropName.QUORUM.value()+"="+ida.getQuorum()+"\n");
			
			if(fi==null){
				response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
				return;
			}
			
			resp.append(FileInfo.PropName.TYPE.value()+"="+fi.getType().value()+"\n");
			resp.append(FileInfo.PropName.CREATION.value()+"="+fi.getCreationDate().getTime()+"\n");
			resp.append(FileInfo.PropName.LASTMOD.value()+"="+fi.getLastModifiedDate().getTime()+"\n");
			resp.append(FileInfo.PropName.LASTACC.value()+"="+fi.getLastAccessedDate().getTime()+"\n");
			
			if(fi.getType()==FileInfo.Type.FILE){
				resp.append(FileInfo.PropName.SIZE.value()+"="+Resources.encodeKeyValue(fi.getFileSize())+"\n");
				if(fi.verifyChecksum()){
					resp.append(FileInfo.PropName.SLICE_BLKSIZE.value()+"="+fi.getBlkSize()+"\n");
					resp.append(FileInfo.PropName.SLICE_KEYHASH.value()+"="+Resources.encodeKeyValue(fi.getKeyHash())+"\n");
				}
				
				int cnt=0;
				for(FileSliceInfo i: fi.getSlices()){
					resp.append(FileSliceInfo.PropName.NAME.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceName())+"\n");
					resp.append(FileSliceInfo.PropName.SEQ.value()+cnt+"="+i.getSliceSeq()+"\n");
					resp.append(FileSliceInfo.PropName.LEN.value()+cnt+"="+i.getLength()+"\n");
					if(fi.verifyChecksum()) resp.append(FileSliceInfo.PropName.CHECKSUM.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceChecksum())+"\n");
					resp.append(FileSliceInfo.PropName.SVR.value()+cnt+"="+Resources.encodeKeyValue(i.getServerId())+"\n");
					cnt++;
				}
				resp.append(FileSliceInfo.PropName.COUNT.value()+"="+cnt+"\n");
				
				//piggy back the server mappings back
				writeFileSliceServers(mappings, resp);
			}else{
				//currently no need to return anything else
			}

			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}finally{
			mappings.clear();
			mappings=null;
		}
	}

	private void refreshDirFiles(org.restlet.Request request, org.restlet.Response response, String usr){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String path, strLastChk;
		try{
			path=urlQuery.getFirstValue(RestletProxyQueryPropName.File.PATH.value(), true, null);
			strLastChk=urlQuery.getFirstValue(RestletProxyQueryPropName.File.LAST_CHECK.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(path==null || strLastChk==null || path.length()==0 || strLastChk.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		Date lastChk=null;
		try{
			lastChk=new Date(Long.parseLong(strLastChk));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid last check value.");
		}
		
		try{
			List<FileInfo> subFiles=lst_usr.get(usr).getFileAction().refreshDirectoryFiles(path, lastChk);
			
			StringBuilder resp=new StringBuilder();
			resp.append(FileInfo.PropName.DIR_LASTCHECK.value()+"="+lastChk.getTime()+"\n");
			resp.append(FileInfo.PropName.COUNT.value()+"="+(subFiles==null?-1:subFiles.size())+"\n");
			
			if(subFiles!=null){
				FileInfo fi=null;
				for(int i=0; i<subFiles.size(); i++){
					fi=subFiles.get(i);
					
					resp.append(FileInfo.PropName.PATH.value()+i+"="+Resources.encodeKeyValue(fi.getFullPath())+"\n");
					resp.append(FileInfo.PropName.SIZE.value()+i+"="+fi.getFileSize()+"\n");
					resp.append(FileInfo.PropName.TYPE.value()+i+"="+fi.getType().value()+"\n");
					resp.append(FileInfo.PropName.CREATION.value()+i+"="+fi.getCreationDate().getTime()+"\n");
					resp.append(FileInfo.PropName.LASTMOD.value()+i+"="+fi.getLastModifiedDate().getTime()+"\n");
					resp.append(FileInfo.PropName.LASTACC.value()+i+"="+fi.getLastAccessedDate().getTime()+"\n");
				}
				
				subFiles.clear();
				subFiles=null;
			}
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void writeFileSliceServers(List<FileSliceServerInfo> servers, StringBuilder resp){
		resp.append(FileSliceServerInfo.PropName.COUNT.value()+"="+servers.size()+"\n");
		int cnt=0;
		for(FileSliceServerInfo fssi: servers){
			//System.out.println(fssi.getServerId());
			resp.append(FileSliceServerInfo.PropName.ID.value()+cnt+"="+Resources.encodeKeyValue(fssi.getServerId())+"\n");
			resp.append(FileSliceServerInfo.PropName.HOST.value()+cnt+"="+Resources.encodeKeyValue(fssi.getServerHost())+"\n");
			resp.append(FileSliceServerInfo.PropName.TYPE.value()+cnt+"="+fssi.getType().value()+"\n");
			resp.append(FileSliceServerInfo.PropName.MODE.value()+cnt+"="+fssi.getMode().value()+"\n");
			
			resp.append(FileSliceServerInfo.PropName.KEYID.value()+cnt+"="+
					(fssi.getKeyId()==null? "" : Resources.encodeKeyValue(fssi.getKeyId()))+"\n");
			resp.append(FileSliceServerInfo.PropName.KEY.value()+cnt+"="+
					(fssi.getKey()==null? "" :	Resources.encodeKeyValue(fssi.getKey()))+"\n");
			
			if(fssi.hasProperties()){
				for(@SuppressWarnings("rawtypes") Map.Entry k: fssi.getAllProperties().entrySet()){
					if(!k.toString().equals(FileSliceServerInfo.RestletPropName.STATUS_HOST.value()) 
							&& !k.toString().equals(FileSliceServerInfo.RestletPropName.STATUS_SSL.value()))
						resp.append(FileSliceServerInfo.PropName.OPT.value()+cnt+"."+k.getKey().toString()+"="
								+Resources.encodeKeyValue(k.getValue().toString())+"\n");
				}
			}
			
			cnt++;
		}
	}
	
	private FileSliceInfo[] processFileSlices(Properties fileSlices) throws Exception{
		int sliceCnt=Integer.parseInt(fileSlices.get(FileSliceInfo.PropName.COUNT.value()).toString());
		FileSliceInfo fsi=null;
		FileSliceInfo[] slices=new FileSliceInfo[sliceCnt];
		
		for(int cnt=0; cnt<sliceCnt; cnt++){
			if(!fileSlices.containsKey(FileSliceInfo.PropName.NAME.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.SVR.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.SEQ.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.LEN.value()+cnt))
					throw new NoSuchFieldException("Missing slice info parameter.");
				
			fsi=new FileSliceInfo(fileSlices.get(FileSliceInfo.PropName.NAME.value()+cnt).toString(),
					fileSlices.get(FileSliceInfo.PropName.SVR.value()+cnt).toString(), 
					Long.parseLong(fileSlices.get(FileSliceInfo.PropName.LEN.value()+cnt).toString()),
					(!fileSlices.containsKey(FileSliceInfo.PropName.CHECKSUM.value()+cnt) ? null:
						fileSlices.get(FileSliceInfo.PropName.CHECKSUM.value()+cnt).toString()),
					Integer.parseInt(fileSlices.get(FileSliceInfo.PropName.SEQ.value()+cnt).toString()));
			
			slices[cnt]=fsi;
			fsi=null;
		}
		
		return slices;
	}
}
