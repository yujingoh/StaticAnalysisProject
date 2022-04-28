package sg.edu.nyp.sit.svds.master.file;

import java.net.URLDecoder;
import java.util.Properties;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;

public class MasterFileAuthenticationByLDAP implements
		IMasterFileAuthentication {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(MasterFileAuthenticationByLDAP.class);
	
	private static final String UID_PROP="uid";
	private static final String PWD_PROP="pwd";
	
	//debug attribute
	private boolean skipAuthentication=false;
	
	@Override
	public boolean authenticateClient(Object o) throws Exception {
		if(skipAuthentication)
			return true;
		
		String query=(String)o;
		String uid=null, pwd=null;
		for(String n: query.split("&")){
			if(n.startsWith(UID_PROP+"="))
				uid=URLDecoder.decode(n.substring(UID_PROP.length()+1), "UTF-8");
			else if(n.startsWith(PWD_PROP+"="))
				pwd=URLDecoder.decode(n.substring(PWD_PROP.length()+1), "UTF-8");
		}
		
		if(uid==null || pwd==null)
			throw new NoSuchFieldException("User ID and/or password not found.");
		
		//LOG.debug(uid+ " " + pwd);
		
		Properties prop = new Properties();   
	    prop.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");   
	    prop.put(Context.PROVIDER_URL, "ldap"+
	    		(MasterProperties.getString("client.file.authentication.ldap.ssl").equalsIgnoreCase("on") ?
	    				"s": "") +"://" + MasterProperties.getString("client.file.authentication.ldap.server")); 
	    prop.put(Context.SECURITY_AUTHENTICATION, "simple"); 
	    prop.put(Context.SECURITY_PRINCIPAL, "cn="+uid+"," 
	    		+ MasterProperties.getString("client.file.authentication.ldap.domain"));   
	    prop.put(Context.SECURITY_CREDENTIALS, pwd);   
	    
	    LdapContext ctx = null;
	    try {   
	      ctx = new InitialLdapContext(prop, null);   
	      return true;
	    } 
	    catch(AuthenticationException ex){
	    	return false;
	    }
	    catch (NamingException ex) {   
	      ex.printStackTrace(); 
	      LOG.error(ex);
	      return false;
	    } finally{
	    	if(ctx!=null)
	    		ctx.close();
	    }
	}
}
