package sg.edu.nyp.sit.pvfs.proxy;

import java.security.MessageDigest;
import java.sql.Types;
import java.util.Hashtable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.pvfs.proxy.persistence.DBFactory;
import sg.edu.nyp.sit.pvfs.proxy.persistence.DBParameter;
import sg.edu.nyp.sit.pvfs.proxy.persistence.IDBManager;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class RequestAuthentication {
	private static final Log LOG = LogFactory.getLog(RequestAuthentication.class);
			
	private IDBManager db;
	
	private Hashtable<String, String> TOKENS=new Hashtable<String, String>();
	
	public RequestAuthentication(){
		db=DBFactory.getInstance();
	}
	
	public int checkSignature(String id, String query){
		if(id==null || id.length()==0) return 0;
		
		int signPos=query.indexOf("&"+RestletProxyQueryPropName.SIGNATURE+"=");
		if(signPos==-1) return 0;
		String signature=query.substring(signPos+RestletProxyQueryPropName.SIGNATURE.length()+2);
		query=query.substring(0, signPos);
		
		MessageDigest md;
		try{  md=MessageDigest.getInstance(Resources.HASH_ALGO); 
			String calSignature=Resources.convertToHex(md.digest((query+getToken(id.toUpperCase())).getBytes()));
			//System.out.println(query + "=" + calSignature + "="+signature);
			return (calSignature.equals(signature) ? 1 : 0);
		} catch(NullPointerException ex) { 
			LOG.debug("No remote session available");
			return -1;
		} catch(Exception ex){ 
			ex.printStackTrace();
			return 0; 
		}
	}
	
	private String getToken(String id) throws Exception{
		if(TOKENS.containsKey(id)) return TOKENS.get(id);
		
		String token=(String) db.queryScalar("SELECT Token FROM SubscriberSession WHERE UPPER(ID)=?", new DBParameter(id, Types.VARCHAR));
		if(token==null) throw new NullPointerException("No remote session available.");
		
		TOKENS.put(id, token);
		return token;
	}
	
	public void removeToken(String id){
		TOKENS.remove(id);
	}
}
