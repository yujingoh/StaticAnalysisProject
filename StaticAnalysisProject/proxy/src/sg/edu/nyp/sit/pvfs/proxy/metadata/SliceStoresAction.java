package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import sg.edu.nyp.sit.pvfs.proxy.persistence.DBParameter;
import sg.edu.nyp.sit.pvfs.proxy.persistence.SQLiteDBManager;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo.Status;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo.Type;

public class SliceStoresAction {
	private SQLiteDBManager db;
	@SuppressWarnings("unused")
	private SubscriberMetadataInfo umi;
	
	public SliceStoresAction(SubscriberMetadataInfo umi) throws Exception{
		this.umi=umi;
		db=new SQLiteDBManager(umi.getDBPath());
	}
	
	public void closeDB(){
		db.close();
	}
	
	public List<FileSliceServerInfo> getSliceStoresBasic() throws Exception{
		ResultSet rs=(ResultSet)db.query("SELECT svrID, active FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString() 
				+ " ORDER BY svrID");
		
		try{
			if(!rs.next()) return null;
			
			List<FileSliceServerInfo> stores=new ArrayList<FileSliceServerInfo>();
			FileSliceServerInfo fssi;
			
			do{
				fssi=new FileSliceServerInfo(rs.getString(1));
				fssi.setStatus(Status.valueOf(rs.getInt(2)));
				
				stores.add(fssi);
			}while(rs.next());
			
			return stores;
		}finally{ rs.close(); }
	}
	
	public FileSliceServerInfo getSliceStore(String id) throws Exception{
		DBParameter param=new DBParameter(id.toUpperCase(), Types.VARCHAR);
		
		ResultSet rs=(ResultSet)db.query("SELECT svrID, host, type, mode, keyId, key, active FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString()
				+ " WHERE UPPER(svrID)=?", param);
		
		FileSliceServerInfo fssi;
		try{
			if(!rs.next()) return null;
			
			fssi=new FileSliceServerInfo(rs.getString(1));
			fssi.setServerHost(rs.getString(2));
			fssi.setType(Type.valueOf(rs.getInt(3)));
			fssi.setMode(FileIOMode.valueOf(rs.getInt(4)));
			fssi.setKeyId(rs.getString(5));
			fssi.setKey(rs.getString(6));
			fssi.setStatus(Status.valueOf(rs.getInt(7)));
		}finally{ rs.close(); }
		
		rs=(ResultSet) db.query("SELECT name, value FROM " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() 
				+ " WHERE UPPER(svrID)=?", param);
		
		try{
			if(!rs.next()) return fssi;
			
			do{
				fssi.setProperty(rs.getString(1), rs.getString(2));
			}while(rs.next());
			
			return fssi;
		}finally{ rs.close(); }
	}
	
	public int removeSliceStore(String svrId) throws Exception{
		DBParameter param=new DBParameter(svrId.toUpperCase(), Types.VARCHAR);

		Integer cnt=(Integer)db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.Slices.toString()
				+ " WHERE UPPER(svrID)=?", param);
		
		if(cnt==null) return -1;
		if(cnt>0) return 0;
		
		db.startTransaction();
		
		try{
			db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString() + " WHERE UPPER(svrID)=?", param);
			db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() + " WHERE UPPER(svrID)=?", param);
			
			db.commitTransaction();
			
			return 1;
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	public void registerSliceStore(String svrId, String host, FileSliceServerInfo.Type type, 
			FileIOMode mode, String keyId, String key, FileSliceServerInfo.Status status, Properties props)
		throws Exception{
		db.startTransaction();
		
		try{
			if(isSliceStoreExist(svrId)){
				DBParameter param=new DBParameter(svrId.toUpperCase(), Types.VARCHAR);
				db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString() + " WHERE UPPER(svrID)=?", param);
				db.execute("DELETE FROM " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() + " WHERE UPPER(svrID)=?", param);
			}
			
			db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.SliceStores.toString()
					+ "(svrID, host, type, mode, keyId, key, active) VALUES(?, ?, ?, ?, ?, ?, ?)", new DBParameter[]{
				new DBParameter(svrId, Types.VARCHAR),
				new DBParameter(host, Types.VARCHAR),
				new DBParameter(type.value(), Types.VARCHAR),
				new DBParameter(mode.value(), Types.INTEGER),
				new DBParameter(keyId, (keyId==null ? Types.NULL : Types.VARCHAR)),
				new DBParameter(key, (key==null ? Types.NULL : Types.VARCHAR)),
				new DBParameter(status.value(), Types.VARCHAR)
			});
			
			//System.out.println("register slice store");
			
			DBParameter[] params=new DBParameter[]{
					new DBParameter(svrId, Types.VARCHAR),
					new DBParameter(null, Types.VARCHAR),
					new DBParameter(null, Types.VARCHAR)
			};
			
			for(@SuppressWarnings("rawtypes") Map.Entry k: props.entrySet()){
				params[1].value=k.getKey().toString();
				params[2].value=k.getValue().toString();
				
				db.execute("INSERT INTO " + SubscriberMetadataInfo.Tables.SliceStoresProps.toString() 
						+ "(svrID, name, value) VALUES (?, ?, ?)", params);
			}
			
			db.commitTransaction();
		}catch(Exception ex){
			db.rollbackTransaction();
			
			throw ex;
		}
	}
	
	private boolean isSliceStoreExist(String id) throws Exception{
		Integer cnt=(Integer) db.queryScalar("SELECT COUNT(*) FROM " + SubscriberMetadataInfo.Tables.SliceStores.toString() 
				+ " WHERE UPPER(svrID)=?", new DBParameter(id.toUpperCase(), Types.VARCHAR));
		
		if(cnt==null) throw new Exception("Either table does not exist or error in condition.");
		
		return (cnt==0? false: true);
	}
}
