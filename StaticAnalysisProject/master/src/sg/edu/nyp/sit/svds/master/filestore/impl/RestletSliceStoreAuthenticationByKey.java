package sg.edu.nyp.sit.svds.master.filestore.impl;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Date;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.filestore.IRestletSliceStoreAuthentication;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletFileSliceServerQueryPropName;

public class RestletSliceStoreAuthenticationByKey implements IRestletSliceStoreAuthentication {
	public static final long serialVersionUID = 2L;
	
	private static final String SIGNATURE_QUERY_PROP="s";
	private static final String SHARED_SIGNATURE_QUERY_PROP="ss";
	
	public RestletSliceStoreAuthenticationByKey(){
		
	}
	
	@Override
	public Object generateAuthentication(FileSliceServerInfo fs, Object o)
			throws Exception {
		if(fs.getKey()==null)
			return "";
		
		String query=(String)o;
		
		return "&"+SIGNATURE_QUERY_PROP+"="+Resources.convertToHex(
				(MessageDigest.getInstance(Resources.HASH_ALGO)).digest((query+fs.getKey()).getBytes()));
	}

	@Override
	public Object generateSharedAuthentication(FileSliceServerInfo fs,
			Date expiry, String name) throws Exception {
		
		//System.out.println("key:" + fs.getKey());
		
		String query=EXP_QUERYPROP+"="+expiry.getTime()+"&"+RestletFileSliceServerQueryPropName.Slice.NAME.value()
			+"="+URLEncoder.encode(name, "UTF-8");
		
		return query+"&"+SHARED_SIGNATURE_QUERY_PROP+"="+Resources.convertToHex(
				(MessageDigest.getInstance(Resources.HASH_ALGO)).digest((query+
						(fs.getKey()==null?"":fs.getKey())).getBytes()));
	}
}
