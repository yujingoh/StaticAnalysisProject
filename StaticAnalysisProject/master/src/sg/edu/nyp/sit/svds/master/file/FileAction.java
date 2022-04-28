package sg.edu.nyp.sit.svds.master.file;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;
import sg.edu.nyp.sit.svds.metadata.User;

public class FileAction {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(FileAction.class);
			
	private Map<String, SortedMap<String, FileInfo>> lst_fileNamespaces=null;
	private NamespaceAction na=null;
	private TransLogger transLog=null;
	private FileSliceSegmentRecovery recovery=null;
	private FileChangeMode chgMode=null;
	
	public FileAction(NamespaceAction na,
			Map<String, SortedMap<String, FileInfo>> lst_fileNamespaces,
			TransLogger transLog, FileSliceSegmentRecovery recovery,
			FileChangeMode chgMode){
		this.lst_fileNamespaces=lst_fileNamespaces;
		this.na=na;
		this.transLog=transLog;
		this.recovery=recovery;
		this.chgMode=chgMode;
	}
	
	//for viewer restlet ONLY
	public Map<String, SortedMap<String, FileInfo>> getAllFiles(){
		return Collections.unmodifiableMap(this.lst_fileNamespaces);
	}
	
	//for viewer restlet ONLY
	public Map<String, NamespaceInfo> getAllNamespaces(){
		return na.getAllNamespaces();
	}
	
	//for viewer restlet ONLY
	public FileSliceServerInfo getFileSliceServerSlices(String serverId, Hashtable<FileSliceInfo, FileInfo> slices){
		FileSliceServerInfo svr=na.resolveFileSliceServer(serverId);
		if(svr==null) return null;
		
		if(lst_fileNamespaces==null) return svr;
		
		for(SortedMap<String, FileInfo> files: lst_fileNamespaces.values()){
			for(FileInfo f: files.values()){
				if(f.getSlices()==null) continue;
				
				for(FileSliceInfo fsi: f.getSlices()){
					if(fsi.getServerId().equals(serverId))
						slices.put(fsi, f);
				}
			}
		}
		
		return svr;
	}
	
	//for restlet processing
	public Date addDirectoryInfo(String namespace, String path, String owner, String seq) throws Exception{
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(namespace));
		if(frmNewNamespace){
			createFileNamespace(namespace);
		}else if(lst_fileNamespaces.get(namespace).containsKey(path)){
			//using a message sequence is for the client to send repeated request
			//if it has send previously but failed to receive any response due to network failure
			//even if the request has been completed previously
			
			//however, if the repeated request is send after the master server is down and
			//restarted again, it would result in error, because the sequence no is
			//not persisted in secondary memory.
			
			//check for duplicates, throw error if the request is a new one
			if(!lst_fileNamespaces.get(namespace).get(path).msgSeq.equals(seq))
				throw new IllegalArgumentException("Duplicated file/directory name.");
			else
				return lst_fileNamespaces.get(namespace).get(path).getCreationDate();
		}
		
		try{
			String parentDirectoryPath=null;
			if(path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
				parentDirectoryPath=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
				
				//if the directory is not added to the root, need to find if parent directory exists
				if(!lst_fileNamespaces.get(namespace).containsKey(parentDirectoryPath))
					throw new NoSuchFieldException("Parent path not found.");
				
				if(lst_fileNamespaces.get(namespace).get(parentDirectoryPath).getType()!=FileInfo.Type.DIRECTORY)
					throw new IllegalArgumentException("Parent path is not a directory.");
			}else
				parentDirectoryPath=FileInfo.PATH_SEPARATOR;

			//System.out.println("To add directory: " + path);
			FileInfo fi=new FileInfo(path, namespace, FileInfo.Type.DIRECTORY);
			fi.setOwner(new User(owner));
			fi.setCreationDate(new Date());
			fi.setLastModifiedDate(fi.getCreationDate());
			fi.msgSeq=seq;
			//System.out.println("Directory added: " + fi.getFullPath());
		
			//update log
			if(transLog!=null) transLog.directoryLog(TransLogger.LogEntry.DIR_ADD, fi);
			
			//update the last mod date of parent directory
			lst_fileNamespaces.get(namespace).get(parentDirectoryPath)
				.setLastModifiedDate(fi.getCreationDate());
			
			//update the file list in memory
			SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
			files.put(path, fi);
			
			return fi.getCreationDate();
		}catch(Exception ex){
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	//for image/trans log processing
	public void addDirectoryInfo(String namespace, String path, String owner, Date creationDate) throws Exception{
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(namespace));
		if(frmNewNamespace){
			createFileNamespace(namespace);
		}
		
		try{
			//System.out.println("To add directory: " + path);
			FileInfo fi=new FileInfo(path, namespace, FileInfo.Type.DIRECTORY);
			fi.setOwner(new User(owner));
			fi.setCreationDate(creationDate);
			fi.setLastModifiedDate(creationDate);
			//System.out.println("Directory added: " + fi.getFullPath());

			//update the file list in memory
			SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
			files.put(path, fi);
			
			lst_fileNamespaces.get(namespace).get(
					(path.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
							path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR))
							: FileInfo.PATH_SEPARATOR))
					.setLastModifiedDate(fi.getCreationDate());
		}catch(Exception ex){
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	//for restlet processing
	public void deleteDirectoryInfo(String namespace, String path) throws Exception{
		if(path.equals(FileInfo.PATH_SEPARATOR))
			throw new IllegalArgumentException("Cannot delete root directory.");
		
		SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
		if (files==null){
			//throw new NoSuchFieldException("File is not found.");
			return;
		}
		
		if(files.containsKey(path) && files.get(path).getType()!=FileInfo.Type.DIRECTORY)
			throw new IllegalArgumentException("Object is not a directory.");
		
		//check if there r files inside
		SortedMap<String, FileInfo> sub=files.tailMap(path);
		if(sub.size()>1){
			//System.out.println("To delete: " + path);
			synchronized(lst_fileNamespaces){
				for(FileInfo fi:sub.values()){
					//System.out.println(fi.getFullPath());
					if(fi.getFullPath().equals(path))
						continue;
					
					if(fi.getFullPath().startsWith(path+FileInfo.PATH_SEPARATOR)){
						throw new IllegalArgumentException("Directory is not empty.");
					}else
						break;
				}
			}
		}	
		
		//check if directory is locked
		if(files.get(path).getLockCnt()>0)
			throw new RejectedExecutionException("Directory is locked.");
		
		FileInfo fi=files.get(path);
		fi.setLastModifiedDate(new Date());
		
		//update log
		if(transLog!=null) transLog.directoryLog(TransLogger.LogEntry.DIR_DEL, fi);
		
		files.remove(path);

		files.get((path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ? 
				path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR))
				: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(fi.getLastModifiedDate());
	}
	
	public List<FileInfo> refreshDirectoryFiles(String namespace, String path, Date lastChkDate) throws Exception{	
		if(!lst_fileNamespaces.containsKey(namespace)){
			if(path.equals(FileInfo.PATH_SEPARATOR)){
				lastChkDate.setTime(new Date().getTime());
				return null;
			}
			
			throw new NoSuchFieldException("Existing path is not found.");
		}
		
		if(!lst_fileNamespaces.get(namespace).containsKey(path))
			throw new NoSuchFieldException("Existing path is not found.");
		
		FileInfo f=lst_fileNamespaces.get(namespace).get(path);
		
		if(f.getType()!=FileInfo.Type.DIRECTORY)
			throw new IllegalArgumentException("Path is not a directory.");
		
		//System.out.println("folder last mod="+(f.getLastModifiedDate()==null?0: f.getLastModifiedDate().getTime())
		//		+", current="+lastChkDate.getTime());
		//if last modified date is empty, means there is no activity in the directory
		if(f.getLastModifiedDate()==null || f.getLastModifiedDate().getTime()<lastChkDate.getTime()){
			lastChkDate.setTime(new Date().getTime());
			return null;
		}

		lastChkDate.setTime(new Date().getTime());
		
		return getDirectoryFiles(namespace, path);
	}
	
	public List<FileInfo> getDirectoryFiles(String namespace, String path) throws Exception{
		List<FileInfo> lst=new ArrayList<FileInfo>();
		SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
		if (files==null){
			return lst;
		}
		
		/*
		System.out.println("Files:");
		for(FileInfo fi: files.values())
			System.out.println(fi.getFullPath());
		System.out.println();
		*/
		
		//System.out.println("List files for path: " + path);
		synchronized(lst_fileNamespaces){
			for(FileInfo fi: files.tailMap(path).values()){
				//System.out.print(fi.getFullPath());
				if(fi.getFullPath().equals(path)){
					//System.out.println(" out");
					continue;
				}
				
				if(fi.getFullPath().startsWith(path)){
					if(fi.getFullPath().indexOf(FileInfo.PATH_SEPARATOR, path.length()+1)==-1){
						//System.out.println(" in");
						lst.add(fi);
					}else{
						//System.out.println(" out");
						continue;
					}
				}else{
					//System.out.println(" end");
					break;
				}
			}
		}
		
		return lst;
	}
	
	//for restlet processing
	public Date addFileInfo(String namespace, String fullPath, int version, long size, 
			String owner, int blkSize, String keyHash, Properties fileSlices, String seq) throws Exception{
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(namespace));
		if(frmNewNamespace){
			createFileNamespace(namespace);
		}else if(lst_fileNamespaces.get(namespace).containsKey(fullPath)){
			//using a message sequence is for the client to send repeated request
			//if it has send previously but failed to receive any response due to network failure
			//even if the request has been completed previously

			//however, if the repeated request is send after the master server is down and
			//restarted again, it would result in error, because the sequence no is
			//not persisted in secondary memory.

			//check for duplicates, throw error if the request is a new one
			if(!lst_fileNamespaces.get(namespace).get(fullPath).msgSeq.equals(seq))
				throw new IllegalArgumentException("Duplicated file/directory name.");
			else
				return lst_fileNamespaces.get(namespace).get(fullPath).getCreationDate();
		}
		
		try{
			String parentDirectoryPath=null;
			//if the file is not added to the root, need to find if directory exists
			if(fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
				parentDirectoryPath=fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR));
				
				//if the directory is not added to the root, need to find if parent directory exists
				if(!lst_fileNamespaces.get(namespace).containsKey(parentDirectoryPath))
					throw new NoSuchFieldException("Parent path not found.");
				
				if(lst_fileNamespaces.get(namespace).get(parentDirectoryPath).getType()!=FileInfo.Type.DIRECTORY)
					throw new IllegalArgumentException("Parent path is not a directory.");
			}else
				parentDirectoryPath=FileInfo.PATH_SEPARATOR;

			FileInfo fi=new FileInfo(fullPath, namespace, FileInfo.Type.FILE);
			fi.setCreationDate(new Date());
			fi.setLastModifiedDate(fi.getCreationDate());
			fi.setLastAccessedDate(fi.getCreationDate());
			fi.setOwner(new User(owner)); 
			fi.setFileSize(size);
			fi.setIdaVersion(version);
			fi.setBlkSize(blkSize);
			fi.setKeyHash(keyHash);
			fi.msgSeq=seq;
		
			if(size>0){
				List<FileSliceInfo> slices=processFileSlices(fileSlices);
				fi.setSlices(slices);
				//check which slice requires recovery
				for(FileSliceInfo fsi: slices){
					if(fsi.hasSegments() && recovery!=null){
						recovery.addSlice(fsi, fi);
					}
				}
				slices=null;
			}

			//update log
			if(transLog!=null) transLog.fileLog(TransLogger.LogEntry.FILE_ADD, fi, null);
			
			//update the file list in memory
			SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
			files.put(fullPath, fi);
			
			//update the last upd date of parent directory
			lst_fileNamespaces.get(namespace).get(parentDirectoryPath)
				.setLastModifiedDate(fi.getCreationDate());
			
			if(na!=null) na.useNamespaceMemory(namespace, size);
			
			return fi.getCreationDate();
		}catch(Exception ex){
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	private void createFileNamespace(String namespace) throws Exception{
		if(na!=null && !na.isNamespaceExist(namespace))
			throw new NoSuchFieldException("Namespace does not exist.");
		
		lst_fileNamespaces.put(namespace, Collections.synchronizedSortedMap(new TreeMap<String, FileInfo>()));
		
		FileInfo fi=new FileInfo(FileInfo.PATH_SEPARATOR, namespace, FileInfo.Type.DIRECTORY);
		fi.setOwner(new User("SYSTEM"));
		fi.setCreationDate(new Date());
		fi.setLastModifiedDate(fi.getCreationDate());
		fi.msgSeq=new Long(fi.getCreationDate().getTime()).toString();
		
		lst_fileNamespaces.get(namespace).put(FileInfo.PATH_SEPARATOR, fi);
	}
	
	//for image file/trans log processing
	public void addFileInfo(String namespace, String fullPath, int version, long size, 
			String owner, Date creationDate, Date lastModifiedDate, Date lastAccessedDate,
			int blkSize, String keyHash, List<FileSliceInfo> fileSlices) throws Exception{
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(namespace));
		if(frmNewNamespace){
			createFileNamespace(namespace);
		}
		
		try{
			FileInfo fi=new FileInfo(fullPath, namespace, FileInfo.Type.FILE);
			fi.setCreationDate(creationDate);
			fi.setLastModifiedDate(lastModifiedDate);
			fi.setLastAccessedDate(lastAccessedDate);
			fi.setOwner(new User(owner)); 
			fi.setFileSize(size);
			fi.setIdaVersion(version);
			fi.setBlkSize(blkSize);
			fi.setKeyHash(keyHash);
			fi.msgSeq=(new Long((new Date()).getTime())).toString();
				
			fi.setSlices(fileSlices);
			fileSlices=null;

			//update the file list in memory
			SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
			files.put(fullPath, fi);
			
			FileInfo pfi=lst_fileNamespaces.get(namespace).get(
					(fullPath.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
							fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR))
							: FileInfo.PATH_SEPARATOR));
			
			Date maxDate=new Date(Math.max(creationDate.getTime(), 
					Math.max((lastModifiedDate==null?0:lastModifiedDate.getTime()), 
							(lastAccessedDate==null?0:lastAccessedDate.getTime()))));
			
			if(pfi.getLastModifiedDate()!=null && pfi.getLastModifiedDate().getTime()<maxDate.getTime())
				pfi.setLastModifiedDate(maxDate);			
			
			if(na!=null) na.useNamespaceMemory(namespace, size);
		}catch(Exception ex){
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	private List<FileSliceInfo> processFileSlices(Properties fileSlices) throws Exception{
		int sliceCnt=Integer.parseInt(fileSlices.get(FileSliceInfo.PropName.COUNT.value()).toString());
		int segCnt=0;
		String segPrefix=null;
		FileSliceInfo fsi=null;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		
		long timestamp=new Date().getTime();
		
		for(int cnt=0; cnt<sliceCnt; cnt++){
			if(!fileSlices.containsKey(FileSliceInfo.PropName.NAME.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.SVR.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.SEQ.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.LEN.value()+cnt) ||
				!fileSlices.containsKey(FileSliceInfo.PropName.SEG_CNT.value()+cnt))
					throw new NoSuchFieldException("Missing slice info parameter.");
				
			fsi=new FileSliceInfo(fileSlices.get(FileSliceInfo.PropName.NAME.value()+cnt).toString(),
					fileSlices.get(FileSliceInfo.PropName.SVR.value()+cnt).toString(), 
					Long.parseLong(fileSlices.get(FileSliceInfo.PropName.LEN.value()+cnt).toString()),
					(!fileSlices.containsKey(FileSliceInfo.PropName.CHECKSUM.value()+cnt) ? null:
						fileSlices.get(FileSliceInfo.PropName.CHECKSUM.value()+cnt).toString()),
					Integer.parseInt(fileSlices.get(FileSliceInfo.PropName.SEQ.value()+cnt).toString()));
			
			segCnt=Integer.parseInt(fileSlices.get(FileSliceInfo.PropName.SEG_CNT.value()+cnt).toString());
			if(segCnt>0){
				segPrefix=FileSliceInfo.PropName.SEG.value()+cnt+"_";
				
				for(int s=0; s<segCnt; s++){
					//segments are added in the order they are received
					fsi.addSegment(new FileSliceInfo(fileSlices.get(segPrefix+FileSliceInfo.PropName.NAME.value()+s).toString(),
							fileSlices.get(segPrefix+FileSliceInfo.PropName.SVR.value()+s).toString(),
							Long.parseLong(fileSlices.get(segPrefix+FileSliceInfo.PropName.SEG_OFFSET.value()+s).toString()),
							Long.parseLong(fileSlices.get(segPrefix+FileSliceInfo.PropName.SEG_LEN.value()+s).toString()),
							timestamp));
				}
			}
			
			slices.add(fsi);
			fsi=null;
		}
		
		return slices;
	}
	
	//for restlet processing
	public Date updateFileInfo(String namespace, String fullPath, long size, int blkSize, 
			String keyHash, Properties fileSlices, String seq, String user) throws Exception{
	
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");
		
		if(!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			throw new NoSuchFieldException("Existing file is not found.");
		
		//if it's repeated request and it has been completed before, no need to process again
		if(lst_fileNamespaces.get(namespace).get(fullPath).msgSeq.equals(seq)){
			return lst_fileNamespaces.get(namespace).get(fullPath).getLastModifiedDate();
		}
		
		FileInfo fi=lst_fileNamespaces.get(namespace).get(fullPath);
		
		boolean isPreLocked=true;
		//check if the file is locked by the user for updating first
		synchronized(fi){
			if(fi.getLockBy()!=null && !fi.getLockBy().getId().equalsIgnoreCase(user)){
				//System.out.println("update " + fullPath+"; currently lock by: " + fi.getLockBy() + ". update by: " + user);
				//check if lock expires
				if(((new Date()).getTime()-fi.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
						throw new RejectedExecutionException("File is already locked by another user");
				else
					unlockFileInfo(namespace, fullPath);
			}
			
			if(fi.getLockBy()==null){
				//if the file is not currently lock by any user, then allow the user to accquire the
				//lock immediately in this method. But the lock will be release when method returns
				lockFileInfo(namespace, fullPath, user, false);
				isPreLocked=false;
			}
		}

		long oldFileSize=fi.getFileSize();
		
		fi.setFileSize(size);
		fi.setBlkSize(blkSize);
		fi.setKeyHash(keyHash);
		fi.msgSeq=seq;

		try{
			//Check to compare the slices then update.
			//then if any slices requires recovery put into the queue
			List<FileSliceInfo> slices=processFileSlices(fileSlices);
			
			//compare slices that exist in current list
			int cnt=0;
			FileSliceInfo fsi=null,upd_fsi=null;
			boolean hasSeg;
			while(cnt<fi.getSlices().size()){
				fsi=fi.getSlices().get(cnt);
				//check if the slices exist within the current list
				upd_fsi=null;
				for(FileSliceInfo i: slices)
					if (i.getSliceSeq()==fsi.getSliceSeq())
						upd_fsi=i;

				//if the slice cannot be found then remove from the current list
				if(upd_fsi==null){
					fi.removeSlice(fsi);
					continue;
				}

				fsi.setServerId(upd_fsi.getServerId());
				fsi.setSliceChecksum(upd_fsi.getSliceChecksum());
				fsi.setSliceName(upd_fsi.getSliceName());
				
				//only update the length of the slice if it does not have segments (means not in
				//recovery) because if it is, then the length will be updated as segments are
				//written back to the slice by the recovery thread
				if(!fsi.hasSegments() && upd_fsi.getLength()>fsi.getLength()) {
					fsi.setLength(upd_fsi.getLength());
				}

				if(upd_fsi.hasSegments()){
					hasSeg=fsi.hasSegments();
					//add the seg to the current slice
					for(FileSliceInfo seg: upd_fsi.getSegments()){
						fsi.addSegment(seg);
					}

					//check current slice has seg, if yes, means still in process of recovery
					//or in recovery queue, else add into recovery queue
					if(!hasSeg && recovery!=null){
						recovery.addSlice(fsi, fi);
					}
				}

				slices.remove(upd_fsi);
				
				cnt++;
			}

			//find slices that are new (if any) and add to file info
			for(FileSliceInfo tfsi: slices){
				fi.addSlice(tfsi);
				if(tfsi.hasSegments() && recovery!=null){
					recovery.addSlice(tfsi, fi);	
				}
			}
			
			slices.clear();
			slices=null;
			
			fi.setLastModifiedDate(new Date());
			fi.setLastAccessedDate(fi.getLastModifiedDate());

			//update log
			if(transLog!=null) transLog.fileLog(TransLogger.LogEntry.FILE_UPD, fi, null);
			
			lst_fileNamespaces.get(namespace).get(
					(fullPath.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
							fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR))
							: FileInfo.PATH_SEPARATOR))
					.setLastModifiedDate(fi.getLastModifiedDate());
			
			if(na!=null){
				na.freeNamespaceMemory(namespace, oldFileSize);
				na.useNamespaceMemory(namespace, size);
			}
			
			return fi.getLastModifiedDate();
		}catch(Exception ex){	
			throw ex;
		}finally{
			if(!isPreLocked){
				unlockFileInfo(namespace, fullPath);
			}
		}
	}
	
	//for image file/trans log processing
	public void updateFileInfo(String namespace, String fullPath, long size, Date lastModifiedDate, 
			int blkSize, String keyHash, List<FileSliceInfo> fileSlices) throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace) ||
				!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			return;
		
		FileInfo fi=lst_fileNamespaces.get(namespace).get(fullPath);
		
		long oldFileSize=fi.getFileSize();
		
		fi.setLastModifiedDate(lastModifiedDate);
		fi.setLastAccessedDate(lastModifiedDate);
		fi.setFileSize(size);
		fi.setBlkSize(blkSize);
		fi.setKeyHash(keyHash);
		fi.msgSeq=(new Long((new Date()).getTime())).toString();
		fi.setSlices(null);
		fi.setSlices(fileSlices);

		try{
			fileSlices=null;
			
			lst_fileNamespaces.get(namespace).get(
					(fullPath.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
							fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR))
							: FileInfo.PATH_SEPARATOR))
					.setLastModifiedDate(fi.getLastModifiedDate());
			
			if(na!=null){
				na.freeNamespaceMemory(namespace, oldFileSize);
				na.useNamespaceMemory(namespace, size);
			}
		}catch(Exception ex){	
			throw ex;
		}
	}
	
	public void lockFileInfo(String namespace, String fullPath, String user, boolean toLog) throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");
		
		if(!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			throw new NoSuchFieldException("Existing file is not found.");
		
		SortedMap<String, FileInfo>files=lst_fileNamespaces.get(namespace);
		FileInfo fi=files.get(fullPath);
		
		if(!fi.getType().equals(FileInfo.Type.FILE))
			throw new IllegalArgumentException("Object is not a file.");
		
		synchronized(fi){
			//System.out.println("To lock "+fullPath+ "; currently lock by: " + fi.getLockBy() + ". new: " + user);
			if(fi.getLockBy()==null){
				fi.setLockBy(new User(user));
			}else if(!fi.getLockBy().getId().equalsIgnoreCase(user)){
				if(((new Date()).getTime()-fi.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
					throw new RejectedExecutionException("File is already locked by another user");
				else{
					fi.setLockBy(new User(user));
					return;	//no need to lock the folders again
				}
			}else{
				fi.refreshLock();
				return;
			}
		}
		
		//increment lock cnt on associated folders
		String parent=fi.getFullPath().substring(0, fi.getFullPath().lastIndexOf(FileInfo.PATH_SEPARATOR));
		
		//if the file resides on the root folder, then no need to lock
		if(parent.length()==0)
			return;
		
		while(parent.length()>0){
			files.get(parent).incrementLock();
			parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
		}
		
		if(transLog!=null && toLog) transLog.fileLog(TransLogger.LogEntry.FILE_LOCK, fi, null);
	}
	
	//for restlet processing
	public void unlockFileInfo(String namespace, String fullPath, String user) throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");
		
		if(!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			throw new NoSuchFieldException("Existing file is not found.");
		
		SortedMap<String, FileInfo>files=lst_fileNamespaces.get(namespace);
		FileInfo fi=files.get(fullPath);
		
		if(!fi.getType().equals(FileInfo.Type.FILE))
			throw new IllegalArgumentException("Object is not a file.");
		
		synchronized(fi){
			//System.out.println("To unlock " +fullPath + "; currently lock by: "+fi.getLockBy()+". unlock by: " + user);
			if(fi.getLockBy()==null){
				return;
			}else if(!fi.getLockBy().getId().equalsIgnoreCase(user)){
				if(((new Date()).getTime()-fi.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
					throw new RejectedExecutionException("File is not locked by the current user.");
				else
					fi.setLockBy(null);
			}else{
				fi.setLockBy(null);
			}
		}

		//decrement lock cnt on associated folders
		String parent=fi.getFullPath().substring(0, fi.getFullPath().lastIndexOf(FileInfo.PATH_SEPARATOR));
		
		//if the file resides on the root folder, then no need to lock
		if(parent.length()==0)
			return;
		
		while(parent.length()>0){
			files.get(parent).decrementLock();
			parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
		}
		
		if(transLog!=null) transLog.fileLog(TransLogger.LogEntry.FILE_UNLOCK, fi, null);
	}
	
	//for image file/trans log processing
	public void unlockFileInfo(String namespace, String fullPath) throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace) ||
				!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			return;
		
		SortedMap<String, FileInfo>files=lst_fileNamespaces.get(namespace);
		FileInfo fi=files.get(fullPath);
		
		if(!fi.getType().equals(FileInfo.Type.FILE))
			throw new IllegalArgumentException("Object is not a file.");
		
		synchronized(fi){
			if(fi.getLockBy()==null){
				return;
			}else{
				fi.setLockBy(null);
			}
		}

		//decrement lock cnt on associated folders
		String parent=fi.getFullPath().substring(0, fi.getFullPath().lastIndexOf(FileInfo.PATH_SEPARATOR));
		
		//if the file resides on the root folder, then no need to lock
		if(parent.length()==0)
			return;
		
		while(parent.length()>0){
			files.get(parent).decrementLock();
			parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
		}
	}
	
	//for restlet processing
	//can change namespace but then will not change any slice servers (may happen when slice server 
	//does not support new namespace)
	public Date moveFileInfo(String namespace, String new_namespace, String fullPath, 
			String new_fullPath, String seq)throws Exception{
		
		if(fullPath.equals(FileInfo.PATH_SEPARATOR) || new_fullPath.equals(FileInfo.PATH_SEPARATOR))
			throw new IllegalArgumentException("Cannot move or rename root directory.");
		
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(new_namespace));
		FileInfo.Type newFileType=null;
		
		//if it's repeated request and it has been completed before, no need to process again
		if(!frmNewNamespace && lst_fileNamespaces.get(new_namespace).containsKey(new_fullPath)){
			if(lst_fileNamespaces.get(new_namespace).get(new_fullPath).msgSeq.equals(seq))
					return lst_fileNamespaces.get(new_namespace).get(new_fullPath).getLastAccessedDate();
			
			newFileType=lst_fileNamespaces.get(new_namespace).get(new_fullPath).getType();
		}
		
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");

		FileInfo fi=lst_fileNamespaces.get(namespace).get(fullPath);
		
		if(fi==null)
			throw new NoSuchFieldException("Existing path is not found.");
		
		if(newFileType!=null && fi.getType()!=newFileType)
			throw new IllegalArgumentException("Cannot move or rename " + fi.getFilename() + ": The file already exist.");
		
		if(frmNewNamespace)
			createFileNamespace(new_namespace);

		try{
			String new_parentDirectoryPath=null;
			if(new_fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
				new_parentDirectoryPath=new_fullPath.substring(0, new_fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR));
				
				//if the directory is not added to the root, need to find if parent directory exists
				if(!lst_fileNamespaces.get(new_namespace).containsKey(new_parentDirectoryPath))
					throw new NoSuchFieldException("Parent path not found.");
				
				if(lst_fileNamespaces.get(new_namespace).get(new_parentDirectoryPath).getType()!=FileInfo.Type.DIRECTORY)
					throw new IllegalArgumentException("Parent path is not a directory.");
			}else
				new_parentDirectoryPath=FileInfo.PATH_SEPARATOR;
			
			if(fi.getType()==FileInfo.Type.FILE)
				moveFile(fi, new_namespace, new_fullPath, true);
			else
				moveDirectory(fi, new_namespace, new_fullPath, true);
			
			fi.msgSeq=seq;
			
			Date dt=new Date();
			fi.setLastAccessedDate(dt);
			
			//update log
			if(transLog!=null) transLog.fileLog(TransLogger.LogEntry.FILE_MV, fi, new FileInfo(fullPath,namespace, fi.getType()));
			
			lst_fileNamespaces.get(namespace).get(
					(fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ?
							fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)) 
							: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(dt);
			
			lst_fileNamespaces.get(new_namespace).get(new_parentDirectoryPath)
				.setLastModifiedDate(dt);
			
			return dt;
		}catch(IllegalArgumentException ex){
			throw ex;
		}catch(Exception ex){	
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	//for image file/trans log processing
	public void moveFileInfo(String namespace, String new_namespace, String fullPath, 
			String new_fullPath, Date lastAccessedDate)throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace)
				|| !lst_fileNamespaces.get(namespace).containsKey(fullPath))
			return;
		
		boolean frmNewNamespace=(!lst_fileNamespaces.containsKey(new_namespace));

		FileInfo fi=lst_fileNamespaces.get(namespace).get(fullPath);

		if(frmNewNamespace)
			createFileNamespace(new_namespace);
		
		try{
			if(fi.getType()==FileInfo.Type.FILE)
				moveFile(fi, new_namespace, new_fullPath, false);
			else
				moveDirectory(fi, new_namespace, new_fullPath, false);
			
			fi.setLastAccessedDate(lastAccessedDate);
			
			fi.msgSeq=(new Long(new Date().getTime())).toString();
			
			lst_fileNamespaces.get(namespace).get(
					(fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ?
							fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)) 
							: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(lastAccessedDate);
			
			lst_fileNamespaces.get(new_namespace).get(
					(new_fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ?
							new_fullPath.substring(0, new_fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)) 
							: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(lastAccessedDate);
		}catch(Exception ex){	
			if(frmNewNamespace)
				lst_fileNamespaces.remove(namespace);
			throw ex;
		}
	}
	
	private void moveDirectory(FileInfo fi, String new_namespace, String new_fullPath, boolean checkLock) throws Exception{
		//lock the folder first
		fi.incrementLock();
		
		List<FileInfo> subFiles=new ArrayList<FileInfo>();
		List<FileInfo> tmp=new ArrayList<FileInfo>();
		
		try{
			//check if sub directories and/or files are locked
			SortedMap<String, FileInfo>files = lst_fileNamespaces.get(fi.getNamespace());
			synchronized(lst_fileNamespaces){
				for(FileInfo i: files.tailMap(fi.getFullPath()).values()){
					if(i.getFullPath().equals(fi.getFullPath())){
						continue;
					}

					if(i.getFullPath().startsWith(fi.getFullPath()+FileInfo.PATH_SEPARATOR)){
						if(checkLock){
							synchronized(i){
								if(i.getType()==FileInfo.Type.DIRECTORY){
									if(i.getLockCnt()>0){
										//add to a temp list to check again later for locks
										//as it might be lock due to file that resides in it
										//where the file lock already expires
										tmp.add(i);
										continue;
									}

									//lock it so no one can delete it
									i.incrementLock();
								}else{
									if(i.getLockBy()!=null){
										//check if lock expires
										if(((new Date()).getTime()-i.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
											throw new RejectedExecutionException("File within the directory is locked.");
										else
											unlockFileInfo(i.getNamespace(), i.getFullPath());
									}

									//lock it so no one can access it
									i.setLockBy(new User("SYSTEM"));
								}
							}
						}
						subFiles.add(i);
					}else
						break;
				}

				if(checkLock){
					//when all the files have unlocked (if any), and lock counter
					//is still more than 1 (1 is because at the start the lock is incremented),
					//means other user are locking it
					if(fi.getLockCnt()>1)
						throw new RejectedExecutionException("Directory is locked.");
	
					//check the files in the tmp list to see if it's lock
					for(FileInfo i: tmp){
						synchronized(i){
							if(i.getLockCnt()>0)
								throw new RejectedExecutionException("Sub directory is locked.");
	
							//lock it so no one can delete it
							i.incrementLock();
						}
						subFiles.add(i);
					}
				}
				
				tmp.clear();
				tmp=null;
			}
			
			Collections.sort(subFiles);
			
			SortedMap<String, FileInfo>nfiles = lst_fileNamespaces.get(new_namespace);

			String newPath=null;
			for(FileInfo i: subFiles){		
				newPath=i.getFullPath().replace(fi.getFullPath(), new_fullPath);
				
				//if new namespace already have the path and the type is different
				//throw error as cannot allow
				if(nfiles.containsKey(newPath) &&
						!nfiles.get(newPath).getType().equals(i.getType())){
					throw new IllegalArgumentException("Cannot move or rename " + i.getFilename() + ": The file already exist.");
				}
				
				//remove file from ori namespace
				files.remove(i.getFullPath());
				
				//update the directory name and/or namespace
				i.setNamespace(new_namespace);
				i.setFullPath(newPath);

				//put the file into the new namespace with updated path
				nfiles.put(newPath, i);
			}
			
			files.remove(fi.getFullPath());
			fi.setNamespace(new_namespace);
			fi.setFullPath(new_fullPath);
			
			nfiles.put(new_fullPath, fi);
		}catch(Exception ex){
			//ex.printStackTrace();
			throw ex;
		}finally{
			fi.decrementLock();
			if(checkLock){
				for(FileInfo i: subFiles){
					if(i.getType()==FileInfo.Type.DIRECTORY)
						i.decrementLock();
					else
						i.setLockBy(null);
				}
			}
		}
	}
	
	private void moveFile(FileInfo fi, String new_namespace, String new_fullPath, boolean checkLock) throws Exception{
		if(checkLock){
			//file can only be rename or move if it is not locked by anyone, even the user.
			synchronized(fi){
				if(fi.getLockBy()!=null){
					//check if lock expires
					if(((new Date()).getTime()-fi.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
							throw new RejectedExecutionException("File is locked.");
					else
						unlockFileInfo(fi.getNamespace(), fi.getFullPath());
				}

				//lock by system so that in the mean time, no one can access the file for update or delete
				fi.setLockBy(new User("SYSTEM"));
			}
		}
		
		SortedMap<String, FileInfo>files=lst_fileNamespaces.get(fi.getNamespace());
		
		files.remove(fi.getFullPath());
		
		if(na!=null) na.freeNamespaceMemory(fi.getNamespace(), fi.getFileSize());
			
		fi.setNamespace(new_namespace);

		fi.setFullPath(new_fullPath);
		
		files = lst_fileNamespaces.get(new_namespace);
		files.put(new_fullPath, fi);

		if(na!=null) na.useNamespaceMemory(new_namespace, fi.getFileSize());
		
		//unlock the file
		fi.setLockBy(null);
	}
	
	//for restlet processing
	public void deleteFileInfo(String namespace, String fullPath) throws Exception{
		SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
		if (files==null){
			//throw new NoSuchFieldException("File is not found.");
			return;
		}
		
		if(files.containsKey(fullPath) && files.get(fullPath).getType()!=FileInfo.Type.FILE)
			throw new IllegalArgumentException("Object is not a file.");
		
		//file can only be removed when it is not lock by any user
		//even if the user has accquire the lock on the file, must release it before deleting
		FileInfo fi=files.get(fullPath);
		synchronized(fi){
			if(fi.getLockBy()!=null){
				//check if lock expires
				if(((new Date()).getTime()-fi.getLockOn().getTime())<MasterProperties.getLong(MasterProperties.PropName.FILE_LOCK_INTERVAL)*1000)
						throw new RejectedExecutionException("File is locked.");
				else
					unlockFileInfo(namespace, fullPath);
			}
			
			//lock by system so that in the mean time, no one can access the file for update or delete
			//no need to unlock later because it is removed frm the list and no longer exist
			fi.setLockBy(new User("SYSTEM"));
		}
		
		fi.setLastModifiedDate(new Date());
		//indicate that if there is recovery, it should stop as the file is already deleted
		fi.isDeleted=true;
		
		//update log
		if(transLog!=null) transLog.fileLog(TransLogger.LogEntry.FILE_DEL, fi, null);
		
		files.remove(fullPath);
		
		files.get((fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ? 
				fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR))
				: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(fi.getLastModifiedDate());
		
		if(na!=null) na.freeNamespaceMemory(namespace, fi.getFileSize());
	}
	
	//for image/trans log processing
	public void deleteFileInfo(String namespace, String fullPath, Date lastModifiedDate) throws Exception{
		SortedMap<String, FileInfo>files = lst_fileNamespaces.get(namespace);
		if(files==null)
			return;
		
		FileInfo fi=files.get(fullPath);
		if(fi==null)
			return;
		
		files.remove(fullPath);
		
		files.get((fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ? 
				fullPath.substring(0, fullPath.lastIndexOf(FileInfo.PATH_SEPARATOR))
				: FileInfo.PATH_SEPARATOR)).setLastModifiedDate(lastModifiedDate);
		
		if(na!=null) na.freeNamespaceMemory(namespace, fi.getFileSize());
	}
	
	public FileInfo getFileInfo(String namespace, String fullPath, List<FileSliceServerInfo> serverMappings) throws Exception{
		if(na==null)
			throw new NullPointerException("Namespace list is null.");

		//if the user attempt to get the root directory when it is not created,
		//create it first
		if(!lst_fileNamespaces.containsKey(namespace) && fullPath.equals(FileInfo.PATH_SEPARATOR)){
			createFileNamespace(namespace);
		}
		
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(fullPath))
			return null;
		
		FileInfo fi = lst_fileNamespaces.get(namespace).get(fullPath);
		
		if(fi.getType()==FileInfo.Type.FILE && serverMappings!=null){
			for(FileSliceInfo i: fi.getSlices()){
				FileSliceServerInfo fssi=na.resolveFileSliceServer(i.getServerId());
				if(fssi!=null && fssi.getStatus()==FileSliceServerInfo.Status.ACTIVE){
					if(!serverMappings.contains(fssi)) serverMappings.add(fssi);
				}
			}
		}
		
		return fi;
	}
	
	public List<FileSliceInfo> genFileInfo(String namespace, int reqNum, FileIOMode pref,
			List<FileSliceServerInfo> servers) throws Exception{
		if(na==null)
			throw new NullPointerException("Namespace list is null.");
		
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		List<FileSliceServerInfo> avaServers=na.getFileSliceServers(namespace, pref);
		
		if(avaServers.size()==0)
			throw new NoSuchFieldException("No slice server registered under the namespace.");
		
		LOG.debug("Available slice servers: " + avaServers.size());
		LOG.debug("Require no of slice: " + reqNum);
		
		//aim is to spread out among servers
		//but if ava servers less than req slices then have to have duplicates
		if(reqNum<avaServers.size()){
			Random r = new Random();
			for(int i=0; i<reqNum; i++){
				FileSliceServerInfo fss=null;
				do{
					fss=avaServers.get(r.nextInt(avaServers.size()));
				}while(servers.contains(fss));
				servers.add(fss);
				
				LOG.debug("Assigned slice server " + fss.getServerId() + " at " + fss.getServerHost());
				slices.add(new FileSliceInfo(UUID.randomUUID().toString(), fss.getServerId()));
			}
		}else{
			for(FileSliceServerInfo fss: avaServers){
				servers.add(fss);
			
				LOG.debug("Assigned slice server " + fss.getServerId() + " at " + fss.getServerHost());
				slices.add(new FileSliceInfo(UUID.randomUUID().toString(), fss.getServerId()));
			}
			
			if(reqNum>avaServers.size()){
				Random r = new Random();
				for(int i=avaServers.size(); i<reqNum; i++){
					FileSliceServerInfo fss=avaServers.get(r.nextInt(avaServers.size()));
					
					LOG.debug("Assigned slice server " + fss.getServerId() + " at " + fss.getServerHost());
					slices.add(new FileSliceInfo(UUID.randomUUID().toString(), fss.getServerId()));
				}
			}
		}
		
		avaServers.clear();
		avaServers=null;
		
		return slices;
	}

	//for restlet processing
	public void updateFileInfoLastAccessed(String namespace, String filename) throws Exception{
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");
		
		if(!lst_fileNamespaces.get(namespace).containsKey(filename))
			throw new NoSuchFieldException("Existing file is not found.");
		
		if(lst_fileNamespaces.get(namespace).get(filename).getType()!=FileInfo.Type.FILE)
			throw new IllegalArgumentException("Object is not a file.");
		
		FileInfo fi=lst_fileNamespaces.get(namespace).get(filename);
		fi.setLastAccessedDate(new Date());
		
		if(transLog!=null)
			transLog.fileLog(TransLogger.LogEntry.FILE_ACC, fi, null);
		
		lst_fileNamespaces.get(namespace).get(
				(filename.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
						filename.substring(0, filename.lastIndexOf(FileInfo.PATH_SEPARATOR))
						: FileInfo.PATH_SEPARATOR))
				.setLastModifiedDate(fi.getLastAccessedDate());
	}
	
	//for edit log processing
	public void updateFileInfoLastAccessed(String namespace, String filename, Date lastAccessedDate){
		if(!lst_fileNamespaces.containsKey(namespace) ||
				!lst_fileNamespaces.get(namespace).containsKey(filename))
			return;
		
		lst_fileNamespaces.get(namespace).get(filename).setLastAccessedDate(lastAccessedDate);
		
		lst_fileNamespaces.get(namespace).get(
				(filename.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
						filename.substring(0, filename.lastIndexOf(FileInfo.PATH_SEPARATOR))
						: FileInfo.PATH_SEPARATOR))
				.setLastModifiedDate(lastAccessedDate);
	}
	
	//for edit log processing
	public void fileSliceRecoveryDone(String namespace, String filename, int sliceSeq, String sliceName,
			long sliceLen, String sliceChecksum){
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(filename) ||
				lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq)==null)
			return;
		
		FileSliceInfo fsi=lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq);
		if(!fsi.getSliceName().equals(sliceName))
			return;
		
		fsi.clearSegments();
		fsi.setLength(sliceLen);
		if(sliceChecksum!=null)fsi.setSliceChecksum(sliceChecksum);
	}
	
	//for edit log processing
	public void fileSliceRecoveryFail(String namespace, String filename, int sliceSeq, String sliceName){
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(filename) ||
				lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq)==null)
			return;
		
		FileSliceInfo fsi=lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq);
		if(fsi==null || !fsi.getSliceName().equals(sliceName))
			return;
			
		lst_fileNamespaces.get(namespace).get(filename).removeSlice(sliceSeq);
	}
	
	//for edit log processing
	public void fileSliceSegRecovery(String namespace, String filename, int sliceSeq, String segname,
			long sliceLen){
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(filename) ||
				lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq)==null)
			return;
		
		FileSliceInfo fsi=lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq);
		if(!fsi.containsSegment(segname)) return;
		
		fsi.setLength(sliceLen);
		fsi.removeSegment(segname);
	}
	
	//for edit log processing
	public void fileSliceMove(String namespace, String filename, int sliceSeq, String newServerId){
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(filename) ||
				lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq)==null)
			return;
		
		lst_fileNamespaces.get(namespace).get(filename).getSlice(sliceSeq).setServerId(newServerId);
	}
	
	//for edit log processing
	public void fileChangeMode(String namespace, String filename, FileIOMode mode){
		if(!lst_fileNamespaces.containsKey(namespace) || 
				!lst_fileNamespaces.get(namespace).containsKey(filename))
			return;
		
		lst_fileNamespaces.get(namespace).get(filename).setChgMode(mode);
	}
	
	//for restlet processing
	public void fileChangeMode(String namespace, String filename, FileIOMode mode, String user) throws Exception{
		if(chgMode==null)
			throw new UnsupportedOperationException("Not supported.");
			
		if(!lst_fileNamespaces.containsKey(namespace))
			throw new NoSuchFieldException("Existing namespace is not found.");
		
		if(!lst_fileNamespaces.get(namespace).containsKey(filename))
			throw new NoSuchFieldException("Existing file is not found.");
		
		if(lst_fileNamespaces.get(namespace).get(filename).getType()!=FileInfo.Type.FILE)
			throw new IllegalArgumentException("Object is not a file.");
		
		FileInfo fi=lst_fileNamespaces.get(namespace).get(filename);
		
		chgMode.addFile(fi, mode);
	}
}
