package sg.edu.nyp.sit.svds.master.file;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;

public class MasterFileAuthenticationFactory {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(MasterFileAuthenticationFactory.class);
	private static final String AUTHENTICATION_CLASS_PROP="client.file.authentication";
	private static IMasterFileAuthentication INSTANCE=null;
	
	public static IMasterFileAuthentication getInstance(){
		String clsName=MasterProperties.getString(AUTHENTICATION_CLASS_PROP);
		if(clsName.equalsIgnoreCase("NONE"))
			return null;
		
		if(INSTANCE !=null)
			return INSTANCE;
		
		try{
			@SuppressWarnings("rawtypes")
			Class cls=Class.forName(clsName);
			INSTANCE=(IMasterFileAuthentication)cls.newInstance();
			return INSTANCE;
		}catch(Exception ex){
			LOG.error(ex);
			return null;
		}
	}
}
