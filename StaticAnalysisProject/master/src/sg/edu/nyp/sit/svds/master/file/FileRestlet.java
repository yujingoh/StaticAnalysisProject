package sg.edu.nyp.sit.svds.master.file;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.*;
import org.restlet.data.*;

import sg.edu.nyp.sit.svds.exception.LockedSVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.IdaInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class FileRestlet extends Restlet{
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(FileRestlet.class);

	private FileAction fa=null;
	private IMasterFileAuthentication auth=null;
	
	public FileRestlet(FileAction fa){
		super();
		this.fa=fa;
		this.auth=MasterFileAuthenticationFactory.getInstance();
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		try{
			if(auth!=null && !auth.authenticateClient(request.getResourceRef().getQuery())){
				response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED, "Not a valid user.");
				return;
			}
		}catch(Exception ex){
			LOG.error(ex);
			return;
		}

		if(reqType.equalsIgnoreCase("REFRESH")){
			//http://localhost:9010/file/refresh?namespace=urn:nyp.edu.sg&path=/abc&lastChk=1297407368734
			refreshDirectoryFiles(request, response);
		}else if(reqType.equalsIgnoreCase("GET")){
			//http://localhost:9000/file/get?namespace=urn:nyp.edu.sg&path=/secret.doc
			getFileInfo(request, response);
		}else if(reqType.equalsIgnoreCase("LOCK")){
			//http://localhost:9000/file/lock?namespace=urn:nyp.edu.sg&filename=/test.txt&by=victoria
			lockFileInfo(request, response, true);
		}else if(reqType.equalsIgnoreCase("UNLOCK")){
			//http://localhost:9000/file/unlock?namespace=urn:nyp.edu.sg&filename=/test.txt&by=victoria
			lockFileInfo(request, response, false);	
		}else if(reqType.equalsIgnoreCase("ACCESS")){
			//http://localhost:9000/file/access?namespace=urn:nyp.edu.sg&filename=/secret.doc
			accessFile(request, response);
		}else if(reqType.equalsIgnoreCase("GENERATE")){
			//http://localhost:9000/file/generate?namespace=urn:nyp.edu.sg&size=1
			genFileInfo(request, response);
		}else if(reqType.equalsIgnoreCase("ADD")){
			//http://localhost:9000/file/add?namespace=urn:nyp.edu.sg&filename=/abc/secret.doc&
			//size=100&checksum=XXX&type=file&timestamp=257545872425425
			//body will contain the file slices info
			
			//OR
			
			//http://localhost:9000/file/add?namespace=urn:nyp.edu.sg&path=/abc/def&
			//owner=xxx&type=dir&timestamp=257545872425425
			addFileInfo(request, response);
		}else if(reqType.equalsIgnoreCase("UPDATE")){
			//http://localhost:9000/file/update?namespace=urn:nyp.edu.sg&
			//filename=/secret.doc&size=100&checksum=XXX&timestamp=257545872425425
			//body will contain the file slices info
			updateFileInfo(request, response);
		}else if(reqType.equalsIgnoreCase("MOVE")){
			//http://localhost:9000/file/update?namespace=urn:nyp.edu.sg&old_namespace=urn.sit.nyp.edu.sg&
			//filename=/secret.doc&old_filename=/old.doc
			moveFileInfo(request, response);
		}else if(reqType.equalsIgnoreCase("DELETE")){
			//http://localhost:9000/file/delete?namespace=urn:nyp.edu.sg&filename=/secret.doc&type=file
			
			//OR
			
			//http://localhost:9000/file/delete?namespace=urn:nyp.edu.sg&path=/abc/def&type=dir
			deleteFileInfo(request, response);
		
		}else if(reqType.equalsIgnoreCase("LIST")){
			//http://localhost:9000/file/list?namespace=urn:nyp.edu.sg&path=/abc/def
			getDirectoryFiles(request, response);
		}else if(reqType.equalsIgnoreCase("CHGMODE")){
			//http://localhost:9010/file/chgmode?namespace=urn:nyp.edu.sg&filename=/abc&mode=1&by=abc
			changeFileMode(request, response);
		}else if(reqType.equalsIgnoreCase("CHKSTATUS")){
			//http://localhost:9010/file/chkstatus?namespace=urn:nyp.edu.sg&filename=/abc&status=REV|MODE
			checkFileStatus(request, response);
		}
	}
	
	private void checkFileStatus(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename, status;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			status=urlQuery.getFirstValue(RestletMasterQueryPropName.File.STATUS.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || filename==null || status==null ||
				namespace.length()==0 || filename.length()==0 || status.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			FileInfo fi=fa.getFileInfo(namespace, filename, null);
			
			if(fi==null){
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "File does not exist.");
				return;
			}
			
			if(status.equalsIgnoreCase("MODE"))
				response.setEntity(fi.getChgMode().value()+"", MediaType.TEXT_PLAIN);
			else if(status.equalsIgnoreCase("REV")){
				for(FileSliceInfo fsi: fi.getSlices()){
					if(fsi.hasSegments() && !fsi.isAllSegmentsRemoved()){
						response.setEntity("1", MediaType.TEXT_PLAIN);
						return;
					}
				}
				response.setEntity("0", MediaType.TEXT_PLAIN);
			}else{
				response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid status.");
			}
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void changeFileMode(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename, strMode, user;
		FileIOMode mode;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			strMode=urlQuery.getFirstValue(RestletMasterQueryPropName.File.MODE.value(), true, null);
			user=urlQuery.getFirstValue(RestletMasterQueryPropName.File.USER.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || filename==null || strMode==null || user==null ||
				namespace.length()==0 || filename.length()==0 || strMode.length()==0 ||
				user.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			mode=FileIOMode.valueOf(Integer.parseInt(strMode));
		}catch(NumberFormatException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid mode.");
			return;
		}
		
		if(mode==null || mode==FileIOMode.NONE || mode==FileIOMode.BOTH){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid mode.");
			return;
		}
		
		try{
			fa.fileChangeMode(namespace, filename, mode, user);
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
		}catch(IllegalArgumentException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
		}catch(LockedSVDSException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void accessFile(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || filename==null || namespace.length()==0 || filename.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		try{
			fa.updateFileInfoLastAccessed(namespace, filename);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void refreshDirectoryFiles(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, path, strLastChk;
		Date lastChkDate=null;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			path=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			strLastChk=urlQuery.getFirstValue(RestletMasterQueryPropName.File.LAST_CHECK.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || path==null || strLastChk==null || 
				namespace.length()==0 || path.length()==0 || strLastChk.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			lastChkDate=new Date(Long.parseLong(strLastChk));
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid timestamp.");
			return;
		}
		
		try{
			//System.out.println("curr last chk date=" + lastChkDate.getTime());
			List<FileInfo>subFiles=fa.refreshDirectoryFiles(namespace, path, lastChkDate);
			//System.out.println("new last chk date=" + lastChkDate.getTime());
			StringBuilder resp=new StringBuilder();
			
			//System.out.println("count: " +(subFiles==null?-1:subFiles.size()));
			resp.append(FileInfo.PropName.DIR_LASTCHECK.value()+"="+lastChkDate.getTime()+"\n");
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
		}catch(NoSuchFieldException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
		}catch(IllegalArgumentException ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE,  ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void lockFileInfo(org.restlet.Request request, org.restlet.Response response, boolean lock){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename, user;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			user=urlQuery.getFirstValue(RestletMasterQueryPropName.File.USER.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || filename==null || user==null ||
				namespace.length()==0 || filename.length()==0 || user.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			if(lock) 
				fa.lockFileInfo(namespace, filename, user, true);
			else
				fa.unlockFileInfo(namespace, filename, user);
			
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(RejectedExecutionException rex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, rex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void addFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, strSize, strSeq, owner;
		String path, strType, strVersion, keyHash, strBlkSize;
		long size=0;
		int version=0, blkSize=0;
		FileInfo.Type type=null;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			owner=urlQuery.getFirstValue(RestletMasterQueryPropName.File.OWNER.value(), true, null);
			strSeq=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SEQ.value(), true, null);
			strType=urlQuery.getFirstValue(RestletMasterQueryPropName.File.TYPE.value(), true, null);
			
			strSize=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SIZE.value(), true, null);
			strVersion=urlQuery.getFirstValue(RestletMasterQueryPropName.File.IDA_VERSION.value(), true, null);
			strBlkSize=urlQuery.getFirstValue(RestletMasterQueryPropName.File.FILE_BLKSIZE.value(), true, null);
			keyHash=urlQuery.getFirstValue(RestletMasterQueryPropName.File.FILE_KEYHASH.value(), true, null);

			path=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || strSeq==null || strType==null || path==null || path.length()==0 ||
				namespace.length()==0 || strSeq.length()==0 || strType.length()==0){
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
				
				creationDate=fa.addFileInfo(namespace, path, version, size, owner, 
						blkSize, (blkSize==0? null: keyHash), data, strSeq);
			}else{
				creationDate=fa.addDirectoryInfo(namespace, path, owner, strSeq);
			}
			
			response.setEntity(creationDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}

	}
	
	private void moveFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();

		String namespace, new_namespace, filename, new_filename;
		String strSeq;

		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.OLD_NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.OLD_PATH.value(), true, null);
			new_namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			new_filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			strSeq=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SEQ.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(new_namespace==null || new_filename==null || namespace==null 
				|| filename==null || strSeq==null || 
				new_namespace.length()==0 || new_filename.length()==0 
				|| namespace.length()==0 || filename.length()==0
				|| strSeq.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		try{
			Date lastAccessedDate=fa.moveFileInfo(namespace, new_namespace, filename, new_filename, strSeq);
			
			response.setEntity(lastAccessedDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(IllegalArgumentException iex){
			response.setStatus(Status.CLIENT_ERROR_CONFLICT, iex.getMessage());
		}catch(RejectedExecutionException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void updateFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename, strSize, strUser, keyHash;
		String strSeq, strBlkSize;
		long size=0;
		int blkSize=0;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
			strSize=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SIZE.value(), true, null);
			strSeq=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SEQ.value(), true, null);
			strUser=urlQuery.getFirstValue(RestletMasterQueryPropName.File.USER.value(), true, null);
			strBlkSize=urlQuery.getFirstValue(RestletMasterQueryPropName.File.FILE_BLKSIZE.value(), true, null);
			keyHash=urlQuery.getFirstValue(RestletMasterQueryPropName.File.FILE_KEYHASH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(strSize==null || strSeq==null || strUser==null || strBlkSize==null ||
				strSize.length()==0 || strSeq.length()==0 || strUser.length()==0 
				|| strBlkSize.length()==0 || filename==null || filename.length()==0){
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
			
			lastModifiedDate=fa.updateFileInfo(namespace, filename, size, blkSize, keyHash, data, strSeq, 
					strUser);
			
			response.setEntity(lastModifiedDate.getTime()+"", MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(RejectedExecutionException ex){
			response.setStatus(Status.CLIENT_ERROR_LOCKED, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void deleteFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace,  strType, path;
		FileInfo.Type type=null;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			strType=urlQuery.getFirstValue(RestletMasterQueryPropName.File.TYPE.value(), true, null);
			path=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || strType==null || namespace.length()==0 || strType.length()==0
				|| path==null || path.length()==0){
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
				fa.deleteFileInfo(namespace, path);
			}else{
				fa.deleteDirectoryInfo(namespace, path);
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
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
			return;
		}
	}
	
	private void getFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, path;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			path=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || path==null || namespace.length()==0 || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}

		FileInfo fi=null;
		List<FileSliceServerInfo> mappings=new ArrayList<FileSliceServerInfo>();
		try{
			fi=fa.getFileInfo(namespace, path, mappings);
			
			StringBuilder resp=new StringBuilder();
			
			int idaVersion=(fi==null || fi.getType()!=FileInfo.Type.FILE ?
					MasterProperties.getInt(MasterProperties.PropName.CURR_IDA_VERSION) 
					: fi.getIdaVersion());
		
			resp.append(FileInfo.PropName.IDA_VERSION.value()+"=" + idaVersion + "\n");
			resp.append(IdaInfo.PropName.MATRIX.value()+"="+Resources.encodeKeyValue(MasterProperties.idaProp.get(idaVersion+".matrix"))+"\n");
			resp.append(IdaInfo.PropName.SHARES.value()+"="+MasterProperties.idaProp.get(idaVersion+".shares")+"\n");
			resp.append(IdaInfo.PropName.QUORUM.value()+"="+MasterProperties.idaProp.get(idaVersion+".quorum")+"\n");
			
			if(fi==null){
				response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
				return;
			}
			
			resp.append(FileInfo.PropName.TYPE.value()+"="+fi.getType().value()+"\n");
			resp.append(FileInfo.PropName.OWNER.value()+"="+Resources.encodeKeyValue(fi.getOwner().getId())+"\n");
			resp.append(FileInfo.PropName.CREATION.value()+"="+fi.getCreationDate().getTime()+"\n");
			resp.append(FileInfo.PropName.LASTMOD.value()+"="+fi.getLastModifiedDate().getTime()+"\n");
			resp.append(FileInfo.PropName.LASTACC.value()+"="+fi.getLastAccessedDate().getTime()+"\n");
			
			if(fi.getType()==FileInfo.Type.FILE){
				resp.append(FileInfo.PropName.SIZE.value()+"="+Resources.encodeKeyValue(fi.getFileSize())+"\n");
				if(fi.verifyChecksum()){
					resp.append(FileInfo.PropName.SLICE_BLKSIZE.value()+"="+fi.getBlkSize()+"\n");
					resp.append(FileInfo.PropName.SLICE_KEYHASH.value()+"="+Resources.encodeKeyValue(fi.getKeyHash())+"\n");
				}
				resp.append(FileInfo.PropName.CHGMODE.value()+"="+fi.getChgMode().value()+"\n");
				
				int cnt=0;
				for(FileSliceInfo i: fi.getSlices()){
					resp.append(FileSliceInfo.PropName.NAME.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceName())+"\n");
					resp.append(FileSliceInfo.PropName.SEQ.value()+cnt+"="+i.getSliceSeq()+"\n");
					resp.append(FileSliceInfo.PropName.LEN.value()+cnt+"="+i.getLength()+"\n");
					if(fi.verifyChecksum()) resp.append(FileSliceInfo.PropName.CHECKSUM.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceChecksum())+"\n");
					resp.append(FileSliceInfo.PropName.SVR.value()+cnt+"="+Resources.encodeKeyValue(i.getServerId())+"\n");
					resp.append(FileSliceInfo.PropName.SEG_RECOVERY.value()+cnt+"="+(i.hasSegments()? "1" : "0")+"\n");
					cnt++;
				}
				resp.append(FileSliceInfo.PropName.COUNT.value()+"="+cnt+"\n");
				
				//piggy back the server mappings back
				writeFileSliceServers(mappings, resp);
			}else{
				//currently no need to return anything else
			}

			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(NoSuchFieldException nex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, nex.getMessage());
		}catch(Exception ex){
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
			return;
		}finally{
			mappings.clear();
			mappings=null;
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
			
			if(!MasterProperties.getBool(MasterProperties.PropName.SLICESTORE_USE_SHARED_ACCESS)){
				resp.append(FileSliceServerInfo.PropName.KEYID.value()+cnt+"="+
						(fssi.getKeyId()==null? "" : Resources.encodeKeyValue(fssi.getKeyId()))+"\n");
				resp.append(FileSliceServerInfo.PropName.KEY.value()+cnt+"="+
						(fssi.getKey()==null? "" :	Resources.encodeKeyValue(fssi.getKey()))+"\n");
			}
			
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
	
	private void genFileInfo(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();

		String namespace, strReq, strPref;
		int req;
		FileIOMode pref;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			strReq=urlQuery.getFirstValue(RestletMasterQueryPropName.File.SIZE.value(), true, null);
			strPref=urlQuery.getFirstValue(RestletMasterQueryPropName.File.MODE.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || strReq==null || strPref==null ||
				namespace.length()==0 || strReq.length()==0 || strPref.length()==0){
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
		List<FileSliceInfo> slices=null;
		try{
			slices=fa.genFileInfo(namespace, req, pref, servers);
			
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
			ex.printStackTrace();
			LOG.error(ex);
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}finally{
			servers.clear();
			servers=null;
			if(slices!=null) slices.clear();
			slices=null;
		}
	}
	
	private void getDirectoryFiles(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, path;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			path=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || path==null || namespace.length()==0 || path.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		List<FileInfo> lst=null;
		try{
			lst=fa.getDirectoryFiles(namespace, path);
		}catch(Exception ex){
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
			return;
		}	
		
		if(lst!=null && lst.size()>0){
			StringBuilder resp=new StringBuilder();
			for(FileInfo fi:lst){
				resp.append(fi.getFullPath()+"\n");
			}
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}else{
			response.setStatus(Status.SUCCESS_OK);
		}
	}
}
