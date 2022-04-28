package sg.edu.nyp.sit.svds.filestore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SliceStoreClientAuthenticationFactory {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(SliceStoreClientAuthenticationFactory.class);
	private static ISliceStoreClientAuthentication INSTANCE=null;
	
	private static final String REQ_VERIFY_CLS_PROP="request.verification.impl";
	
	public static ISliceStoreClientAuthentication getInstance(){
		if (INSTANCE != null) 
			return INSTANCE;
		
		try{
			@SuppressWarnings("rawtypes")
			Class cls=Class.forName(SliceStoreProperties.getString(REQ_VERIFY_CLS_PROP));
			INSTANCE=(ISliceStoreClientAuthentication)cls.newInstance();
		return INSTANCE;
		}catch(Exception ex){
			LOG.error(ex);
			return null;
		}
	}
}
