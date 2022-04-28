package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import sg.edu.nyp.sit.pvfs.proxy.persistence.DBParameter;
import sg.edu.nyp.sit.pvfs.proxy.persistence.SQLiteDBManager;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.IdaInfo;

public class FileAction {
	private static long FILE_LOCK_INTERVAL=300L;

	private SQLiteDBManager db;
	private SubscriberMetadataInfo umi;
	
	public FileAction(SubscriberMetadataInfo umi) throws Exception{
		this.umi=umi;
		db=new SQLiteDBManager(umi.getDBPath());
	}
	
	public void closeDB(){
		db.close();
	}
	
	private Map<Integer, IdaInfo> idas=Collections.synchronizedMap(new Hashtable<Integer, IdaInfo>());
	public IdaInfo getIdaInfo(int version) throws Exception{
		if(idas.containsKey(version)) return idas.get(version);

		ResultSet rs=(ResultSet) db.query("SELECT shares, quorum, matrix FROM " + SubscriberMetadataInfo.Tables.IdaInfo.toString() 
				+ " WHERE version=?", new DBParameter(version, Types.INTEGER)); 
		
		try{
			if(!rs.next()) return null;
			
			idas.put(version, new IdaInfo(rs.getInt(1), rs.getInt(2), rs.getString(3)));
		}finally{ rs.close(); }
		
		return idas.get(version);
	}
	
	//Description:	Method to return the updated file info for a selected folder/directory
	//Input:		path 		-	The absolute path to the selected folder/directory, including the folder name
	//				lastChkDate	- 	Date when the user last invoked the method. Used to compared with the
	//								lastModDT field in the folder/directory record in the SQLite DB
	//Output:		An array list of immediate sub files/folder as FileInfo object. Only populate the path, type, 
	//				size, creation date, last modified date, last accessed date properties of the object. 
	//				If the value lastModDT field of the selected folder/directory is earlier than the 
	//				lastChkDate, then null is returned to indicate that there is no changes in the sub 
	//				files/folder of the selected directory. Else if the selected folder/directory has no
	//				sub files/folder, then an empty array list is returned. 
	public List<FileInfo> refreshDirectoryFiles(String path, Date lastChkDate) throws Exception{
		Long lastMod=(Long) db.queryScalar("SELECT lastModDT FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
				new DBParameter(path, Types.VARCHAR));
		
		if(lastMod==null || (lastChkDate!=null && lastMod.longValue()<lastChkDate.getTime())){
			lastChkDate.setTime(new Date().getTime());
			return null;
		}

		ResultSet rs=(ResultSet) db.query("SELECT path, type, size, createDT, lastModDT, lastAccDT "
				+ "FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path LIKE ? AND path NOT LIKE ? "
				+ "ORDER BY type DESC, path ASC", new DBParameter[]{
					new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "_" : FileInfo.PATH_SEPARATOR)+"%", Types.VARCHAR),
					new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "" : FileInfo.PATH_SEPARATOR)+"%"
							+FileInfo.PATH_SEPARATOR+"%", Types.VARCHAR)}
				);
		
		List<FileInfo> files=new ArrayList<FileInfo>();
		
		try{
			FileInfo fi;
			if(!rs.next()) return files;
			
			do{
				fi=new FileInfo(rs.getString(1), null, FileInfo.Type.valueOf(rs.getInt(2)));
				fi.setFileSize(rs.getLong(3));
				fi.setCreationDate(new Date(rs.getLong(4)));
				
				long tmp=rs.getLong(5);
				if(!rs.wasNull()) fi.setLastModifiedDate(new Date(tmp));
				tmp=rs.getLong(6);
				if(!rs.wasNull()) fi.setLastAccessedDate(new Date(tmp));
				
				files.add(fi);
			}while(rs.next());
			
			return files;
		}finally{ rs.close(); }
	}
	
	//Description:	Method to get the sub files/folders of the selected folder/directory
	//Input:		path - The selected folder/directory
	//Output:		An array list containing the path of the sub files/folders of the selected folder/directory
	public List<String> getDirectoryFiles(String path) throws Exception{
		ResultSet rs=(ResultSet) db.query("SELECT path FROM " + SubscriberMetadataInfo.Tables.Files.toString() 
				+ " WHERE path LIKE ? AND path NOT LIKE ? "
				+ "ORDER BY path ASC", new DBParameter[]{
					new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "_" : FileInfo.PATH_SEPARATOR)+"%", Types.VARCHAR),
					new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "" : FileInfo.PATH_SEPARATOR)+"%"
							+FileInfo.PATH_SEPARATOR+"%", Types.VARCHAR)}
				);
		
		try{
			if(!rs.next()) return null;
			
			List<String> files=new ArrayList<String>();
			do{
				files.add(rs.getString(1));
			}while(rs.next());
			
			return files;
		}finally{ rs.close(); }
	}
	
	//Description:	Method to get the detail information of the selected file/folder
	//Input:		fullPath	-	The absolute path to the selected file/folder, including the file/folder name
	//				mappings	-	An empty array list to be populated with the information of the file 
	//								slice server that stores the slices of the selected file  (folder 
	//								is not applicable)
	//Output:		A FileInfo object containing all the information about the selected file/folder
	public FileInfo getFileInfo(String fullPath, List<FileSliceServerInfo> mappings) throws Exception{
		DBParameter param=new DBParameter(fullPath, Types.VARCHAR);
		
		ResultSet rs=(ResultSet) db.query("SELECT type, size, idaVersion, blkSize, keyHash, createDT, lastModDT, lastAccDT FROM "
				+ SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", param);
		
		FileInfo fi=null;
		try{
			if(!rs.next()) return null;
			
			fi=new FileInfo(fullPath, null,  FileInfo.Type.valueOf(rs.getInt(1)));
			fi.setFileSize(rs.getLong(2));
			int tmp=rs.getInt(3);
			if(!rs.wasNull()) fi.setIdaVersion(tmp);
			tmp=rs.getInt(4);
			if(!rs.wasNull()) fi.setBlkSize(tmp);
			fi.setKeyHash(rs.getString(5));
			fi.setCreationDate(new Date(rs.getLong(6)));
			fi.setLastModifiedDate(new Date(rs.getLong(7)));
			fi.setLastAccessedDate(new Date(rs.getLong(8)));
		}finally{ rs.close(); }
		
		if(fi.getType()==FileInfo.Type.DIRECTORY)
			return fi;
		
		rs=(ResultSet) db.query("SELECT sliceSeq, sliceName, svrID, length, checksum FROM " + SubscriberMetadataInfo.Tables.Slices.toString()
				+ " WHERE path=? AND active=1 ORDER BY sliceSeq ASC", param);
		
		HashSet<String> svrs=new HashSet<String>();
		try{
			if(!rs.next()) return fi;
			
			do{
				fi.addSlice(new FileSliceInfo(rs.getString(2), rs.getString(3), rs.getLong(4), 
						rs.getString(5), rs.getInt(1)));

				svrs.add(rs.getString(3));
			}while(rs.next());
		}finally{ rs.close(); }
		
		if(mappings==null) return fi;
		StringBuilder sql=new StringBuilder();
		sql.append("SELECT svrID, host, type, mode, keyId, key FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString() + " WHERE svrID in (");
		Iterator<String> iter=svrs.iterator();
		while(iter.hasNext()){
			sql.append("'" + iter.next() + "',");
		}
		sql.replace(sql.length()-1, sql.length(), ")");
		
		rs=(ResultSet) db.query(sql.toString());
		
		try{
			if(!rs.next()) throw new Exception("Slice stores records not found.");
			
			FileSliceServerInfo fssi;
			ResultSet rsSub;
			do{
				fssi=new FileSliceServerInfo(rs.getString(1), rs.getString(2), 
						FileSliceServerInfo.Type.valueOf(rs.getInt(3)), FileIOMode.valueOf(rs.getInt(4)));
				fssi.setKeyId(rs.getString(5));
				fssi.setKey(rs.getString(6));
				
				rsSub=(ResultSet) db.query("SELECT name, value FROM " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() 
						+ " WHERE svrID=?", new DBParameter(rs.getString(1), Types.VARCHAR));
				try{
					if(rsSub.next()){
						do{
							fssi.setProperty(rsSub.getString(1), rsSub.getString(2));
						}while(rsSub.next());
					}
				}finally{ rsSub.close(); }
				
				mappings.add(fssi);
			}while(rs.next());
		}finally{ rs.close(); }
		
		return fi;
	}
	
	//Description:	Method to update the last acccessed date of the selected file/folder
	//Input:		filepath	- The absolute path to the selected file/folder
	public void updateFileInfoLastAccessed(String filename) throws Exception{
		db.startTransaction();
		
		try{
			Date dtLastAcc=new Date();
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastAccDT=? WHERE path=?", new DBParameter[]{
				new DBParameter(dtLastAcc.getTime(), Types.NUMERIC),
				new DBParameter(filename, Types.VARCHAR)
			});
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", new DBParameter[]{
				new DBParameter(dtLastAcc.getTime(), Types.NUMERIC),
				new DBParameter((filename.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
						filename.substring(0, filename.lastIndexOf(FileInfo.PATH_SEPARATOR))
						: FileInfo.PATH_SEPARATOR), Types.VARCHAR)
			});
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	//Description:	Method to generate information on the slices for a file. The information mainly includes
	//				the assignment of a file slice server to store the contents of the file slice and an UUID
	//				as the name of the file slice
	//Input:		reqNum	-	The number of file slice information to generate	
	//				pref	- 	If the file slice server assigned is in streaming or non-streaming mode
	//				servers	-	An empty array list to be populated with the information of the file slice
	//							server that will store the slice of the file
	public FileSliceInfo[] genFileInfo(int reqNum, FileIOMode pref,
			List<FileSliceServerInfo> servers) throws Exception{
		int noOfFS=(Integer) db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString()
				+ " WHERE (mode=" + FileIOMode.STREAM.value() + " OR mode=" + pref.value() + ") AND active=1");
			
		if(noOfFS==0) throw new NoSuchFieldException("No slice server registered.");

		FileSliceServerInfo fssi;
		FileSliceInfo[] slices=new FileSliceInfo[reqNum];
		Random r = new Random();
		
		String sql="SELECT svrID, host, type, mode, keyId, key FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString()
			+ " WHERE (mode=" + FileIOMode.STREAM.value() + " OR mode=" + pref.value() + ") AND active=1";
		String sqlSub="SELECT name, value FROM " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() + " WHERE svrID=?";
		
		if(reqNum<noOfFS){
			sql=sql+ " LIMIT 1 OFFSET ?";
			ResultSet rs, rsSub;
			DBParameter param=new DBParameter(null, Types.VARCHAR);
			DBParameter paramSub=new DBParameter(null, Types.VARCHAR);
			
			boolean isFound;
			int[] pos=new int[reqNum];
			
			for(int i=0; i<reqNum; i++){
				do{
					isFound=false;
					pos[i]=r.nextInt(noOfFS);
					for(int n=0; n<i; n++)
						if(pos[n]==pos[i]) isFound=true;
				}while(isFound);
				
				param.value=pos[i];
				rs=(ResultSet)db.query(sql, param);
				
				try{
					if(servers!=null){
						fssi=new FileSliceServerInfo(rs.getString(1), rs.getString(2), 
								FileSliceServerInfo.Type.valueOf(rs.getInt(3)), 
								FileIOMode.valueOf(rs.getInt(4)));
						
						fssi.setKeyId(rs.getString(5));
						fssi.setKey(rs.getString(6));
						
						paramSub.value=rs.getString(1);
						rsSub=(ResultSet) db.query(sqlSub, paramSub);
						
						try{
							while(rsSub.next()) fssi.setProperty(rsSub.getString(1), rsSub.getString(2));
						}finally{ rsSub.close(); }
						
						servers.add(fssi);
					}
					
					slices[i]=new FileSliceInfo(UUID.randomUUID().toString(), rs.getString(1));
				}finally{ rs.close(); }
			}
		}else{
			ResultSet rs=(ResultSet) db.query(sql);
			
			ResultSet rsSub;
			DBParameter param=new DBParameter(null, Types.VARCHAR);
			int i=0;
			try{
				while(rs.next()){
					if(servers!=null){
						fssi=new FileSliceServerInfo(rs.getString(1), rs.getString(2), 
								FileSliceServerInfo.Type.valueOf(rs.getInt(3)), 
								FileIOMode.valueOf(rs.getInt(4)));
						
						fssi.setKeyId(rs.getString(5));
						fssi.setKey(rs.getString(6));
						
						param.value=rs.getString(1);
						rsSub=(ResultSet) db.query(sqlSub, param);
						
						try{
							while(rsSub.next()) fssi.setProperty(rsSub.getString(1), rsSub.getString(2));
						}finally{ rsSub.close(); }
						
						servers.add(fssi);
					}
					
					slices[i]=new FileSliceInfo(UUID.randomUUID().toString(), rs.getString(1));
					i++;
				}
			}finally{ rs.close(); }
			
			int pos;
			for(int n=i; n<reqNum; n++){
				pos=r.nextInt(noOfFS);
				
				slices[n]=new FileSliceInfo(UUID.randomUUID().toString(), slices[pos].getServerId());
			}
		}

		return slices;
	}
	
	//Description: 	Method to update the selected file information
	//Input: 		path		-	The absolute path to the file, including the file name
	//				size		-	The new file size in bytes
	//				blkSize		-	The size of the block to compute the file slice block hash.
	//				keyHash		-	The salt used in the computation of the file slice block hash
	//				fileSlices	-	The file slices of the file
	//				usr			- 	The user invoking this method
	//Output:		The last modified date of the file/folder
	public Date updateFileInfo(String path, long size, int blkSize, 
			String keyHash, FileSliceInfo[] fileSlices, String usr) throws Exception{
		FileInfo.Type type=getFileType(path);
		if(type==null) throw new NoSuchFieldException("Existing file is not found.");
		else if(type==FileInfo.Type.DIRECTORY) throw new IllegalArgumentException("Object is not a file.");
		
		db.startTransaction();
		try{
			ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn, size FROM " + SubscriberMetadataInfo.Tables.Files.toString() 
					+ " WHERE path=?", new DBParameter(path, Types.VARCHAR));
			
			long oriSize;
			Date lastModDt=new Date();
			
			try{
				rs.next();
				
				oriSize=rs.getLong(3);
				
				if(rs.getString(1)!=null && !rs.getString(1).equalsIgnoreCase(usr)){
					if((lastModDt.getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
						throw new RejectedExecutionException("File is already locked by another user");
				}
			}finally{ rs.close(); }
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET size=?, blkSize=?, keyHash=?, lastModDT=?, "
					+ "lastAccDT=? WHERE path=?", new DBParameter[]{
				new DBParameter(size, Types.NUMERIC),
				new DBParameter(blkSize, Types.NUMERIC),
				new DBParameter(keyHash, (keyHash==null ? Types.NULL:Types.VARCHAR)),
				new DBParameter(lastModDt.getTime(), Types.NUMERIC),
				new DBParameter(lastModDt.getTime(), Types.NUMERIC),
				new DBParameter(path, Types.VARCHAR)
			});
			
			//update last mod date of parent folder
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=?, lastAccDT=? WHERE path=?", 
					new DBParameter[]{
				new DBParameter(lastModDt.getTime(), Types.NUMERIC),
				new DBParameter(lastModDt.getTime(), Types.NUMERIC),
				new DBParameter((path.indexOf(FileInfo.PATH_SEPARATOR)>0 ?
						path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR))
						: FileInfo.PATH_SEPARATOR), Types.VARCHAR)
			});
			
			List<FileSliceInfo> existSlices=new ArrayList<FileSliceInfo>();
			FileSliceInfo es;
			
			//update any existing slices in the DB
			rs=(ResultSet) db.query("SELECT sliceSeq, sliceName, svrID, length FROM " + SubscriberMetadataInfo.Tables.Slices.toString()
					+ " WHERE path=?", new DBParameter(path, Types.VARCHAR));
			
			try{
				if(rs.next()){
					DBParameter paramsExistActive[]=new DBParameter[]{
						new DBParameter(null, Types.NUMERIC),
						new DBParameter(null, Types.VARCHAR),
						new DBParameter(path, Types.VARCHAR),
						new DBParameter(null, Types.INTEGER),
						new DBParameter(null, Types.VARCHAR),
						new DBParameter(null, Types.VARCHAR)
					};
					
					DBParameter paramsExistInactive[]=new DBParameter[]{
						new DBParameter(path, Types.VARCHAR),
						new DBParameter(null, Types.INTEGER),
						new DBParameter(null, Types.VARCHAR),
						new DBParameter(null, Types.VARCHAR)
					};
					
					do{
						es=null;
						for(FileSliceInfo fsi: fileSlices){
							if(fsi.getSliceSeq()==rs.getInt(1) && fsi.getSliceName().equals(rs.getString(2)) 
									&& fsi.getServerId().equals(rs.getString(3))){
								es=fsi;
								break;
							}
						}
						
						if(es!=null){
							//existing slice found in the updated slices, so update its values
							existSlices.add(es);
							
							paramsExistActive[0].value=(es.getLength()>rs.getLong(4)?es.getLength(): rs.getLong(4));
							paramsExistActive[1].value=(es.getSliceChecksum()==null? Types.NULL: es.getSliceChecksum());
							paramsExistActive[3].value=rs.getInt(1);
							paramsExistActive[4].value=rs.getString(2);
							paramsExistActive[5].value=rs.getString(3);
							                  
							db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Slices.toString() + " SET active=1, length=?, checksum=? "
									+ " WHERE path=? AND sliceSeq=? AND sliceName=? AND svrID=?", paramsExistActive);
						}else{
							//existing slice not found in updated slices, change the existing slice status to inactive
							paramsExistInactive[1].value=rs.getInt(1);
							paramsExistInactive[2].value=rs.getString(2);
							paramsExistInactive[3].value=rs.getString(3);
							
							db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Slices.toString() + " SET active=0 WHERE path=? AND sliceSeq=? AND sliceName=? AND svrID=?" , 
									paramsExistInactive);
						}
					}while(rs.next());
				}
			}finally{ rs.close(); }
			
			//insert the new slices into the DB
			boolean isFound;
			DBParameter paramsNew[]=new DBParameter[]{
					new DBParameter(path, Types.VARCHAR),
					new DBParameter(null, Types.INTEGER),
					new DBParameter(null, Types.VARCHAR),
					new DBParameter(null, Types.VARCHAR),
					new DBParameter(null, Types.NUMERIC),
					new DBParameter(null, Types.VARCHAR)	
			};
			
			for(FileSliceInfo fsi: fileSlices){
				isFound=false;
				for(FileSliceInfo tmp: existSlices){
					if(tmp==fsi){
						isFound=true;
						break;
					}
				}
				
				if(isFound) continue;
				
				paramsNew[1].value=fsi.getSliceSeq();
				paramsNew[2].value=fsi.getSliceName();
				paramsNew[3].value=fsi.getServerId();
				paramsNew[4].value=fsi.getLength();
				paramsNew[5].value=(fsi.getSliceChecksum()==null?Types.NULL:fsi.getSliceChecksum());
				db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.Slices.toString() + " (path, sliceSeq, sliceName, svrID, length, checksum) "
						+ "VALUES (?, ?, ?, ?, ?, ?)", paramsNew) ;
			}
			
			//update the total space used
			umi.freeMemory(oriSize);
			umi.useMemory(size);
			
			db.commitTransaction();
			
			return lastModDt;
		}catch(Exception ex){
			db.rollbackTransaction();
			throw ex;
		}
	}
	
	//Description:	Method to create a folder record. If the new folder is a sub folder (does not reside at root),
	//				then there must be a check to determine if the parent folder exist
	//Input:		path	-	The absolute path of the new folder, including the folder name 
	public Date addDirectoryInfo(String path) throws Exception{
		db.startTransaction();
		
		try{
			if(isFileExist(path)) throw new IllegalArgumentException("Duplicated file/directory name.");
			
			String parentDirectoryPath=null;
			if(path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
				parentDirectoryPath=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
				
				//if the directory is not added to the root, need to find if parent directory exists
				if(!isFileExist(parentDirectoryPath))
					throw new NoSuchFieldException("Parent path not found.");
				
				if(getFileType(parentDirectoryPath)!=FileInfo.Type.DIRECTORY)
					throw new IllegalArgumentException("Parent path is not a directory.");
			}else{
				parentDirectoryPath=FileInfo.PATH_SEPARATOR;
			}
			
			Date createDt=new Date();
			
			db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.Files.toString() + " (path, type, size, createDT, lastModDT, lastAccDT) "
					+ "VALUES (?, ?, ?, ?, ?, ?)", new DBParameter[]{
						new DBParameter(path, Types.VARCHAR),
						new DBParameter(FileInfo.Type.DIRECTORY.value(), Types.INTEGER),
						new DBParameter(0, Types.NUMERIC),
						new DBParameter(createDt.getTime(), Types.NUMERIC),
						new DBParameter(createDt.getTime(), Types.NUMERIC),
						new DBParameter(createDt.getTime(), Types.NUMERIC)
			});
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", new DBParameter[]{
				new DBParameter(createDt.getTime(), Types.NUMERIC),
				new DBParameter(parentDirectoryPath, Types.VARCHAR)
			});
			
			db.commitTransaction();
			
			return createDt;
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	//Description:	Method to create a new file record. If the file is in a folder, then there must
	//				be a check to determine if the folder record exist
	//Input:		path		-	The absolute path to the file, including the file name
	//				version		-	The IDA version no used in the file content transformation
	//				size		-	The size of the file contents in bytes
	//				blkSize		-	The size of the block to compute the file slice block hash
	//				keyHash		-	The salt used in the computation of the file slice block hash
	//				fileSlices	-	The file slices of the file, if it is empty or null, means the file
	//								does not have any contents yet
	//Output:		The created date of the file
	public Date addFileInfo(String path, int version, long size, 
			int blkSize, String keyHash, FileSliceInfo[] fileSlices) throws Exception{
		db.startTransaction();
		
		try{
			//check if the file exist
			if(isFileExist(path)){
				throw new IllegalArgumentException("Duplicated file/directory name.");
			}
			
			String parentDirectoryPath=null;
			if(path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
				parentDirectoryPath=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
				
				//if the directory is not added to the root, need to find if parent directory exists
				if(!isFileExist(parentDirectoryPath))
					throw new NoSuchFieldException("Parent path not found.");
				
				if(getFileType(parentDirectoryPath)!=FileInfo.Type.DIRECTORY)
					throw new IllegalArgumentException("Parent path is not a directory.");
			}else{
				parentDirectoryPath=FileInfo.PATH_SEPARATOR;
			}
			
			Date createDt=new Date();
			
			db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.Files.toString() + " (path, type, size, idaVersion, blkSize, keyHash, "
					+ "createDT, lastModDT, lastAccDT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", new DBParameter[]{
				new DBParameter(path, Types.VARCHAR),
				new DBParameter(FileInfo.Type.FILE.value(), Types.INTEGER),
				new DBParameter(size, Types.NUMERIC),
				new DBParameter(version, Types.INTEGER),
				new DBParameter(blkSize, Types.NUMERIC),
				new DBParameter(keyHash, (keyHash==null ? Types.NULL : Types.VARCHAR)),
				new DBParameter(createDt.getTime(), Types.NUMERIC),
				new DBParameter(createDt.getTime(), Types.NUMERIC),
				new DBParameter(createDt.getTime(), Types.NUMERIC)
			});
			
			//slice recovery and segments not implemented in PVFS
			if(fileSlices!=null && fileSlices.length>0){
				DBParameter paramSlices[]=new DBParameter[]{
					new DBParameter(path, Types.VARCHAR),
					new DBParameter(null, Types.INTEGER),
					new DBParameter(null, Types.VARCHAR),
					new DBParameter(null, Types.VARCHAR),
					new DBParameter(null, Types.NUMERIC),
					new DBParameter(null, Types.VARCHAR)
				};
				
				for(FileSliceInfo fsi: fileSlices){
					paramSlices[1].value=fsi.getSliceSeq();
					paramSlices[2].value=fsi.getSliceName();
					paramSlices[3].value=fsi.getServerId();
					paramSlices[4].value=fsi.getLength();
					paramSlices[5].value=(fsi.getSliceChecksum()==null?Types.NULL:fsi.getSliceChecksum());
					
					db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.Slices.toString() + " (path, sliceSeq, sliceName, svrID, length, checksum) "
							+ "VALUES (?, ?, ?, ?, ?, ?)", paramSlices);
				}
			}
			
			//update the last upd date of parent directory
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", new DBParameter[]{
				new DBParameter(createDt.getTime(), Types.NUMERIC),
				new DBParameter(parentDirectoryPath, Types.VARCHAR)
			});
			
			umi.useMemory(size);
			
			db.commitTransaction();
			
			return createDt;
		}catch(Exception ex){
			db.rollbackTransaction();
			throw ex;
		}
	}
	
	//Description:	Method to check if the file/folder exist
	//Input:		path	-	The absolute path to the file/folder
	//Output:		True of the file/folder record exist; false otherwise
	public boolean isFileExist(String path) throws Exception{
		return ((Integer)db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
				new DBParameter(path, Types.VARCHAR))>0);
	}
	
	//Description:	Method to move/rename the selected file/folder
	//Input:		path		-	The absolute path to the file/folder to move/rename, including the 
	//								file/folder name
	//				new_path	-	The NEW absolute path to the file/folder to move/rename to,
	//								including the new/folder name. If the file/folder is to reside in
	//								another folder other than the root, then there must be a check to
	//								to determine if the parent folder record exist
	//Output:		The last modified date of the file/folder
	public Date moveFileInfo(String path, String new_path)throws Exception{
		if(path.equals(FileInfo.PATH_SEPARATOR) || new_path.equals(FileInfo.PATH_SEPARATOR))
			throw new IllegalArgumentException("Cannot move or rename root directory.");
		
		FileInfo.Type type=getFileType(path);
		if(type==null) throw new NoSuchFieldException("Existing path is not found.");
		
		if(getFileType(new_path)!=null) 
			throw new IllegalArgumentException("Cannot move or rename to " + new_path + ": There is an existing file with the same name.");
		
		String new_parentDirectoryPath=null, parentDirectoryPath=null;
		
		if(path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0)
			parentDirectoryPath=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
		else parentDirectoryPath=FileInfo.PATH_SEPARATOR;
		
		if(new_path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0){
			new_parentDirectoryPath=new_path.substring(0, new_path.lastIndexOf(FileInfo.PATH_SEPARATOR));
			
			//if the directory is not added to the root, need to find if parent directory exists
			FileInfo.Type tmp=getFileType(new_parentDirectoryPath);
			if(tmp==null) throw new NoSuchFieldException("Parent path not found.");
			else if(tmp!=FileInfo.Type.DIRECTORY)
				throw new IllegalArgumentException("Parent path is not a directory.");
		}else
			new_parentDirectoryPath=FileInfo.PATH_SEPARATOR;
		
		db.startTransaction();
		
		try{
			Date lastModDt=new Date();
			
			if(type==FileInfo.Type.DIRECTORY) moveDirectory(path, new_path);
			else moveFile(path, new_path);
			
			DBParameter params[]=new DBParameter[]{
					new DBParameter(lastModDt.getTime(), Types.NUMERIC),
					new DBParameter(new_path, Types.VARCHAR)
				};		
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastAccDT=? WHERE path=?", params);
			
			params[1].value=new_parentDirectoryPath;		
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", params);
			
			params[1].value=parentDirectoryPath;		
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", params);
			
			db.commitTransaction();
			
			return lastModDt;
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	private void moveDirectory(String path, String new_path) throws Exception{
		DBParameter params[]=new DBParameter[]{
				new DBParameter(path+FileInfo.PATH_SEPARATOR+"%", Types.VARCHAR),
				new DBParameter(path+FileInfo.PATH_SEPARATOR+"%"+FileInfo.PATH_SEPARATOR+"%", Types.VARCHAR),
				new DBParameter(FileInfo.Type.FILE.value(), Types.INTEGER)
		};
		
		ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn, path FROM " + SubscriberMetadataInfo.Tables.Files.toString()
				+ " WHERE path LIKE ? AND path NOT LIKE ? AND type=? ORDER BY path ASC", params);
		
		try{
			if(rs.next()){
				String parent;
				StringBuilder sql=new StringBuilder();
				
				do{
					if(rs.getString(1)==null) continue;
					
					if(((new Date()).getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
						throw new RejectedExecutionException("File within the directory is locked.");
					else{
						//Decrement the lock on the parent folders, all the way up to the root folder
						parent=rs.getString(3).substring(0, rs.getString(3).lastIndexOf(FileInfo.PATH_SEPARATOR));
						if(parent.length()>0){
							sql.setLength(0);
							sql.append("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lockCnt=lockCnt-1 WHERE path IN (");
							do{
								sql.append("'" + parent + "',");
								parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
							}while(parent.length()>0);
							sql.replace(sql.length()-1, sql.length(), ")");
							
							db.execute(sql.toString());
						}
					}
				}while(rs.next());
			}
		}finally{ rs.close();}
		
		params[2].value=FileInfo.Type.DIRECTORY.value();
		if((Integer)db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path LIKE ? AND path NOT LIKE ? AND type=? AND lockCnt>0", params) > 0)
			throw new RejectedExecutionException("One or more directory is locked.");
		
		DBParameter params1[]=new DBParameter[]{params[0], params[1]};
		//check if the path of sub files or directories existed in the new path 
		if((Integer)db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path LIKE ? AND path NOT LIKE ? "
				+ "AND '" + new_path + "' || SUBSTR(path, "+(path.length()+1)+") IN (SELECT path FROM " + SubscriberMetadataInfo.Tables.Files.toString() + ")", params1) > 0)
			throw new IllegalArgumentException("One or more directory/file already exist in the new path.");
		
		String sql=" SET path='" +new_path+"' || SUBSTR(path, " + (path.length()+1) + ") WHERE (path LIKE ? AND path NOT LIKE ?)";
		
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Slices.toString() + sql, params1);
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + sql, params1);
		
		params1[0].value=new_path;
		params1[1].value=path;
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET path=? WHERE path=?", params1);
	}
	
	private void moveFile(String path, String new_path) throws Exception{
		DBParameter param=new DBParameter(path, Types.VARCHAR); 
		ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
				param);
		
		try{
			rs.next();
			if(rs.getString(1)!=null){
				if(((new Date()).getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
					throw new RejectedExecutionException("File is locked.");
			}
		}finally{ rs.close(); }
		
		DBParameter[] params=new DBParameter[]{
			new DBParameter(new_path, Types.VARCHAR), param
		};
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lockedBy=null, lockedOn=null, path=? WHERE path=?", params);
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Slices.toString() + " SET path=? WHERE path=?", params);
	}
	
	//Description:	Method to delete the selected folder record. The folder to delete must not
	//				contain any sub files/folders
	//Input:		path	-	The absolute path of the folder, including the folder name
	public void deleteDirectoryInfo(String path) throws Exception{
		if(path.equals(FileInfo.PATH_SEPARATOR))
			throw new IllegalArgumentException("Cannot delete root directory.");
		
		FileInfo.Type type=getFileType(path);
		if(type==null) return;
		else if(type==FileInfo.Type.FILE) 
			throw new IllegalArgumentException("Object is not a directory.");
		
		db.startTransaction();
		
		try{
			//check if there r files inside
			Integer tmp=(Integer)db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path LIKE ? AND path NOT LIKE ?", 
						new DBParameter[]{
							new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "_" : FileInfo.PATH_SEPARATOR)+"%", Types.VARCHAR),
							new DBParameter(path+(path.equals(FileInfo.PATH_SEPARATOR) ? "" : FileInfo.PATH_SEPARATOR)+"%"+FileInfo.PATH_SEPARATOR+"%", Types.VARCHAR)
						});
			if(tmp==null) {
				db.rollbackTransaction();
				return;
			}
			
			if(tmp>0) throw new IllegalArgumentException("Directory is not empty.");
			
			tmp=(Integer) db.queryScalar("SELECT lockCnt FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
					new DBParameter(path, Types.VARCHAR));
			if(tmp==null) {
				db.rollbackTransaction();
				return;
			}
			
			if(tmp>0) throw new RejectedExecutionException("Directory is locked.");
			
			deleteFileRecord(path);
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	private void deleteFileRecord(String path) throws Exception{
		DBParameter param=new DBParameter(path, Types.VARCHAR);
		
		db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.Files.toString()+" WHERE path=?", param);
		
		param.value=(path.lastIndexOf(FileInfo.PATH_SEPARATOR)>0 ? 
				path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR))
				: FileInfo.PATH_SEPARATOR);
		
		db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lastModDT=? WHERE path=?", 
				new DBParameter[]{ new DBParameter(new Date().getTime(), Types.NUMERIC), param});
	}
	
	//Description:	Method to delete the selected file record
	//Input:		path	-	 The absolute path of the file, including the file name
	public void deleteFileInfo(String path) throws Exception{
		FileInfo.Type type=getFileType(path);
		if(type==null) return;
		else if(type==FileInfo.Type.DIRECTORY) 
			throw new IllegalArgumentException("Object is not a file.");
		
		db.startTransaction();
		
		try{
			DBParameter param=new DBParameter(path, Types.VARCHAR);
			
			ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn, size FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
					param);
			
			long size;
			
			try{
				rs.next();
				if(rs.getString(1)!=null){
					if(((new Date()).getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
						throw new RejectedExecutionException("File is locked.");
				}
				
				size=rs.getLong(3);
			}finally{ rs.close(); }
			
			deleteFileRecord(path);
			
			//only remove the active file slices cos the non active ones will be removed by the service (that resides in the mobile) later on
			db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.Slices.toString() + " WHERE path=? AND active=1", param);
			
			umi.freeMemory(size);
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	//Description:	Method to lock the file record for writing. When it is lock by 1 user,
	//				no other user can access the file for writing (Exception will be thrown if
	//				the user were to invoke this method)
	//Input:		path	-	The absolute path of the file, including the file name
	//				user	-	The user that attempts to lock the file
	public void lockFileInfo(String path, String user) throws Exception{
		FileInfo.Type type=getFileType(path);
		if(type==null) 
			throw new NoSuchFieldException("Existing file is not found.");
		else if(type==FileInfo.Type.DIRECTORY) 
			throw new IllegalArgumentException("Object is not a file.");
		
		db.startTransaction();
		
		try{
			DBParameter param=new DBParameter(path, Types.VARCHAR);
			
			ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
					param);
			
			boolean toLockFolder=true;
			
			try{
				rs.next();
				if(rs.getString(1)!=null && !rs.getString(1).equalsIgnoreCase(user)){
					if(((new Date()).getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
						throw new RejectedExecutionException("File is already locked by another user");
				}else if(rs.getString(1)!=null && rs.getString(1).equalsIgnoreCase(user))
					toLockFolder=false; //previously locked by user already, mean the method is invoked for refreshing the lock, so no need to lock the folder again
			}finally{ rs.close(); }
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lockedBy=?, lockedOn=? WHERE path=?", new DBParameter[]{
				new DBParameter(user, Types.VARCHAR),
				new DBParameter(new Date().getTime(), Types.NUMERIC), param
			});
			
			if(!toLockFolder){
				db.commitTransaction();
				return;
			}
			
			//Increment the lock on the parent folders, all the way up to the root folder
			String parent=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
			if(parent.length()==0){
				db.commitTransaction();
				return;
			}
			
			StringBuilder sql=new StringBuilder();
			sql.append("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lockCnt=lockCnt+1 WHERE path IN (");
			while(parent.length()>0){
				sql.append("'" + parent + "',");
				parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
			}
			sql.replace(sql.length()-1, sql.length(), ")");
			
			db.execute(sql.toString());
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	//Description:	Method to unlock the file record so that other users may lock it for writing. 
	//				If the method is invoked by user other than the user that lock the file in
	//				the first place, an exception will be thrown
	//Input:		path	- 	The absolute path of the file, including the file name
	//				user	- 	The user that locks the file record initially
	public void unlockFileInfo(String path, String user) throws Exception{
		FileInfo.Type type=getFileType(path);
		if(type==null) 
			throw new NoSuchFieldException("Existing file is not found.");
		else if(type==FileInfo.Type.DIRECTORY) 
			throw new IllegalArgumentException("Object is not a file.");
		
		db.startTransaction();
		
		try{
			DBParameter param=new DBParameter(path, Types.VARCHAR);
			
			ResultSet rs=(ResultSet) db.query("SELECT lockedBy, lockedOn FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
					param);
			
			try{
				rs.next();
				
				if(rs.getString(1)==null){
					db.rollbackTransaction();
					return;
				}
				
				if(!rs.getString(1).equalsIgnoreCase(user)){
					if(((new Date()).getTime()-rs.getLong(2))<FILE_LOCK_INTERVAL*1000)
						throw new RejectedExecutionException("File is not locked by the current user.");
				}
			}finally{ rs.close(); }
			
			db.execute("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString()+" SET lockedBy=null, lockedOn=null WHERE path=?", param);
			
			//Decrement the lock on the parent folders, all the way up to the root folder
			String parent=path.substring(0, path.lastIndexOf(FileInfo.PATH_SEPARATOR));
			if(parent.length()==0){
				db.commitTransaction();
				return;
			}
			
			StringBuilder sql=new StringBuilder();
			sql.append("UPDATE " + SubscriberMetadataInfo.Tables.Files.toString() + " SET lockCnt=lockCnt-1 WHERE (lockCnt!=0 OR (lockCnt-1)!=0) AND path IN (");
			while(parent.length()>0){
				sql.append("'" + parent + "',");
				parent=parent.substring(0, parent.lastIndexOf(FileInfo.PATH_SEPARATOR));
			}
			sql.replace(sql.length()-1, sql.length(), ")");

			db.execute(sql.toString());
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	private FileInfo.Type getFileType(String path) throws Exception{
		Integer type=(Integer) db.queryScalar("SELECT type FROM " + SubscriberMetadataInfo.Tables.Files.toString() + " WHERE path=?", 
				new DBParameter(path, Types.VARCHAR));
		
		if(type==null) return null;
		
		return FileInfo.Type.valueOf(type.intValue());
	}
}
