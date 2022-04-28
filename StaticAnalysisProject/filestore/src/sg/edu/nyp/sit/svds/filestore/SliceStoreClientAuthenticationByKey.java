package sg.edu.nyp.sit.svds.filestore;

import java.security.MessageDigest;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.RestletFileSliceServerQueryPropName;

public class SliceStoreClientAuthenticationByKey implements
		ISliceStoreClientAuthentication {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(SliceStoreClientAuthenticationByKey.class);
	private static final String SIGNATURE_QUERY_PROP="s";
	private static final String SHARED_SIGNATURE_QUERY_PROP="ss";
	
	@Override
	public boolean authenticateClient(Object o) throws Exception {
		//note that query must be in url encoded form
		String query=(String) o;
		int pos=query.lastIndexOf("|");
		if(pos==-1)
			throw new NoSuchFieldException("Missing server ID in input.");
		String serverId=query.substring(pos+1);
		query=query.substring(0, pos);
		
		//System.out.println(query);
		
		int signPos;
		if((signPos=query.indexOf("&"+SIGNATURE_QUERY_PROP+"="))!=-1){
			//System.out.println("full signature");
			return authFull(query, serverId, signPos+1);
		}else if((signPos=query.indexOf("&"+SHARED_SIGNATURE_QUERY_PROP+"="))!=-1){
			//System.out.println("shared signature");
			return authShared(query, serverId, signPos+1);
		}else if(SliceStoreProperties.getBool(SliceStoreProperties.PropName.REQ_VERIFY)){
			//System.out.println("no signature");
			return false; //signature value does not exist, return false
		}else
			return true; //no verification needed
	}
	
	private boolean authShared(String query, String serverId, int signPos) {
		try{
			//System.out.println(query);
			if(query.indexOf(EXP_QUERYPROP+"=")==-1 || query.indexOf(RestletFileSliceServerQueryPropName.Slice.NAME.value()+"=")==-1){
				//System.out.println("missing fields");
				return false;
			}
			
			int signPosEnd=query.indexOf("&", signPos);
			String signature=(signPosEnd!=-1 ? query.substring(signPos+SHARED_SIGNATURE_QUERY_PROP.length()+1, signPosEnd)
					: query.substring(signPos+SHARED_SIGNATURE_QUERY_PROP.length()+1));
			//System.out.println(signature);
			
			int startPos=query.indexOf(EXP_QUERYPROP+"=");
			int endPos=query.indexOf("&", startPos);
			
			long expTime=Long.parseLong((endPos!=-1? query.substring(startPos+EXP_QUERYPROP.length()+1, endPos) 
					:  query.substring(startPos+EXP_QUERYPROP.length()+1)));
			
			//check if the expiry date time has passed
			if(expTime<(new Date()).getTime()){
				//System.out.println("time has passed");
				return false;
			}
			
			String chkQuery=(endPos!=-1? query.substring(startPos, endPos) :  query.substring(startPos));
			//System.out.println(chkQuery);
			
			startPos=query.indexOf(RestletFileSliceServerQueryPropName.Slice.NAME.value()+"=");
			endPos=query.indexOf("&", startPos);
			
			chkQuery+="&"+(endPos!=-1? query.substring(startPos, endPos) :  query.substring(startPos));
			//System.out.println(chkQuery);
			
			String key=SliceStoreProperties.getString(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+serverId);
			//System.out.println("key:" + key);
			String calSignature=Resources.convertToHex(MessageDigest.getInstance(Resources.HASH_ALGO)
					.digest((chkQuery+(key==null?"":key)).getBytes()));
			//System.out.println(calSignature);
			
			return calSignature.equals(signature);
		}catch(Exception ex){
			LOG.error(ex);
			return false;
		}
	}
	
	private boolean authFull(String query, String serverId, int signPos) {
		try{
			String signature=null;
			if(signPos==0){
				//signature value at the front
				signature=query.substring(SIGNATURE_QUERY_PROP.length()+1
						, query.indexOf("&"));
				query=query.substring(query.indexOf("&"));
			}else{
				//signature value at the end
				signature=query.substring(signPos+SIGNATURE_QUERY_PROP.length()+1);
				query=query.substring(0, signPos-1);
			}
			
			//System.out.println("@fs " + serverId + " key: " + SliceStoreProperties.getString(SliceStoreProperties.PropName.SLICESTORE_KEY.value()
			//				+"."+serverId));
			
			MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
			String calSignature=Resources.convertToHex(md.digest((query
					+SliceStoreProperties.getString(SliceStoreProperties.PropName.SLICESTORE_KEY.value()
							+"."+serverId)).getBytes()));
			
			//System.out.println(serverId + " client query: " + query + "\n"+"cal sign: " + calSignature + "\nget sign: " + signature);
			
			return calSignature.equals(signature);
		}catch(Exception ex){
			LOG.error(ex);
			return false;
		}
	}

}
