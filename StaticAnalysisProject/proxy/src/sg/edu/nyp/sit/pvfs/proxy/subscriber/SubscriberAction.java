package sg.edu.nyp.sit.pvfs.proxy.subscriber;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.RejectedExecutionException;

import sg.edu.nyp.sit.pvfs.proxy.ProxyProperties;
import sg.edu.nyp.sit.pvfs.proxy.metadata.MetadataAction;
import sg.edu.nyp.sit.pvfs.proxy.persistence.DBFactory;
import sg.edu.nyp.sit.pvfs.proxy.persistence.DBParameter;
import sg.edu.nyp.sit.pvfs.proxy.persistence.IDBManager;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.User;

public class SubscriberAction {
	private IDBManager db;
	private MetadataAction ma=null;
	
	public SubscriberAction(MetadataAction ma){
		db=DBFactory.getInstance();
		this.ma=ma;
	}
	
	private String keyHash(String key, String salt) throws Exception{
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		return Resources.convertToHex(md.digest((key+salt).getBytes()));
	}
	
	public void registerSubscriber(String id, String key, String name, String email) throws Exception{
		if((Long)db.queryScalar("SELECT COUNT(*) FROM Subscribers WHERE UPPER(ID)=?", 
				new DBParameter(id.toUpperCase(), Types.VARCHAR)) > 0)
			throw new IllegalArgumentException("ID already exist.");
		
		String salt=Resources.generateRandomValue(ProxyProperties.getString(ProxyProperties.PropName.SALT_RANDOM_ALGO), 
				ProxyProperties.getInt(ProxyProperties.PropName.SALT_RANDOM_SIZE));
		key=keyHash(key, salt);
		
		db.execute("INSERT INTO Subscribers(ID, `Key`, Salt, Name, Email, Role) VALUES(?,?,?,?,?,?)", 
				new DBParameter[]{
					new DBParameter(id, Types.VARCHAR),
					new DBParameter(key, Types.VARCHAR),
					new DBParameter(salt, Types.VARCHAR),
					new DBParameter(name, Types.VARCHAR),
					new DBParameter(email, Types.VARCHAR),
					new DBParameter("USR", Types.VARCHAR)
		});
	}
	
	public User getSubscriber(String id) throws Exception{
		ResultSet rs=(ResultSet)db.query("SELECT ID, Name, Email FROM Subscribers WHERE UPPER(ID)=?",
				new DBParameter(id, Types.VARCHAR));
		
		try{
			if(!rs.first()) return null;
			
			User usr=new User(rs.getString(1));
			usr.setName(rs.getString(2));
			usr.setEmail(rs.getString(3));
			
			return usr;
		}finally{rs.close();}
	}

	public List<User> getAllSubscribers() throws Exception{
		ResultSet rs=(ResultSet)db.query("SELECT ID, Name, Email FROM Subscribers");
		
		List<User> usrs=null;
		try{
			if(!rs.first()) return null;
			
			usrs=new ArrayList<User>();
			User usr;
			do{
				usr=new User(rs.getString(1));
				usr.setName(rs.getString(2));
				usr.setEmail(rs.getString(3));
				
				usrs.add(usr);
			}while(rs.next());
		}finally{ rs.close(); }
		
		return usrs;
	}
	
	public void removeSubscriber(String id) throws Exception{
		DBParameter param=new DBParameter(id.toUpperCase(), Types.VARCHAR);
		
		if((Long)db.queryScalar("SELECT COUNT(*) FROM SubscriberSession WHERE UPPER(ID)=?", 
				param) > 0)
			throw new RejectedExecutionException("Existing remote session. Unable to remove user.");
		
		db.execute("DELETE FROM Subscribers WHERE UPPER(ID)=?", param);
	}
	
	public void updateSubscriber(String id, String key, String name, String email) throws Exception{
		if((Long)db.queryScalar("SELECT COUNT(*) FROM Subscribers WHERE UPPER(ID)=?", 
				new DBParameter(id.toUpperCase(), Types.VARCHAR)) == 0)
			throw new IllegalArgumentException("ID does not exist.");
		
		
		DBParameter[] params=new DBParameter[(key==null?3:5)];
		params[0]=new DBParameter(name, Types.VARCHAR);
		params[1]=new DBParameter(email, Types.VARCHAR);
		if(key!=null){
			String salt=Resources.generateRandomValue(ProxyProperties.getString(ProxyProperties.PropName.SALT_RANDOM_ALGO), 
					ProxyProperties.getInt(ProxyProperties.PropName.SALT_RANDOM_SIZE));
			key=keyHash(key, salt);
			
			params[2]=new DBParameter(key, Types.VARCHAR);
			params[3]=new DBParameter(salt, Types.VARCHAR);			
		}
		params[(key==null?2:4)]=new DBParameter(id.toUpperCase(), Types.VARCHAR);
		
		db.execute("UPDATE Subscribers SET Name=?, email=?" + (key==null?" " : ", `Key`=?, Salt=? ") + " WHERE UPPER(ID)=?", 
				params);
	}
	
	
	public boolean isValidSubscriber(String id, String key) throws Exception{
		String salt, keyHash;
		ResultSet rs=(ResultSet)db.query("SELECT salt, `Key` FROM Subscribers WHERE UPPER(ID)=? AND Role='USR'", 
				new DBParameter(id, Types.VARCHAR));
		try{
			if(!rs.next()) return false;
			
			salt=rs.getString(1);
			keyHash=rs.getString(2);
		}finally{ rs.close(); }

		return (keyHash(key, salt).equals(keyHash));
	}
	
	public String createRemoteSession(String id) throws Exception{
		if((Long)db.queryScalar("SELECT COUNT(*) FROM SubscriberSession WHERE UPPER(ID)=?", 
				new DBParameter(id, Types.VARCHAR))>0)
			throw new IllegalStateException("Remote session has already been created");
		
		String token=generateToken();
		
		db.execute("INSERT INTO SubscriberSession(ID, Token) VALUES (?, ?)", 
			new DBParameter[]{
				new DBParameter(id, Types.VARCHAR),
				new DBParameter(token, Types.VARCHAR)});
		
		return token;
	}
	
	//-1 Remote session record does not exist
	//0 Remote session record exist and sessionEnded=true
	//1 Remote session record exist and sessionEnded=false and metadata file exist
	//2 Remote session record exist and sessionEnded=false and metadata file does not exist
	public int chkRemoteSession(String id) throws Exception{
		Boolean isSessionEnded=(Boolean)db.queryScalar("SELECT SessionEnded FROM SubscriberSession WHERE UPPER(ID)=?", 
				new DBParameter(id, Types.VARCHAR));
		if(isSessionEnded==null) return -1;
		
		if(isSessionEnded.booleanValue()){
			if(ma!=null) ma.endSubsriberDB(id);
			
			return 0;
		}
		
		if(ma!=null && ma.isSubscriberDBExist(id)) return 1;
		else return 2;
	}
	
	public void endRemoteSession(String id) throws Exception{
		db.execute("UPDATE SubscriberSession SET SessionEnded=1 WHERE UPPER(ID)=?", 
				new DBParameter(id, Types.VARCHAR));
		
		if(ma!=null) ma.endSubsriberDB(id);
	}
	
	public void delRemoteSession(String id) throws Exception{
		db.execute("DELETE FROM SubscriberSession WHERE UPPER(ID)=?", 
				new DBParameter(id, Types.VARCHAR));
		
		if(ma!=null) ma.delSubscriberDB(id);
	}
	
	private static final String TOKEN_STORE="0123456789QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm";
	private static final int TOKEN_LEN=10;
	private String generateToken(){
		Random r=new Random();
		String token="";
		
		for(int i=0; i<TOKEN_LEN; i++){
			token+=TOKEN_STORE.charAt(r.nextInt(TOKEN_STORE.length()));
		}
		
		return token;
	}
}
