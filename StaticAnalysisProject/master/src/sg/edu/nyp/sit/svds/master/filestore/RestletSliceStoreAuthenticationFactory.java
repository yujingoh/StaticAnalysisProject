package sg.edu.nyp.sit.svds.master.filestore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;

public class RestletSliceStoreAuthenticationFactory {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(RestletSliceStoreAuthenticationFactory.class);
	private static IRestletSliceStoreAuthentication INSTANCE=null;
	
	private static final String AUTHENTICATION_CLASS_PROP="slicestore.restlet.authentication";
	
	public static IRestletSliceStoreAuthentication getInstance(){
		if(INSTANCE !=null)
			return INSTANCE;
		
		try{
			@SuppressWarnings("rawtypes")
			Class cls=Class.forName(MasterProperties.getString(AUTHENTICATION_CLASS_PROP));
			INSTANCE=(IRestletSliceStoreAuthentication)cls.newInstance();
			return INSTANCE;
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			return null;
		}
	}
}
