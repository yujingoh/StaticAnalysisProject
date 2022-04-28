package sg.edu.nyp.sit.svds.filestore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.TimerTask;
import java.util.Date;

public class PingBackMaster extends TimerTask {
	public static final long serialVersionUID = 2L;
	
	private String masterHost, id, namespaces, connector, fsHost;
	
	private static final Log LOG = LogFactory.getLog(PingBackMaster.class);
	
	public PingBackMaster(String masterHost, String connector, String id, String namespaces, 
			String fsHost){
		this.masterHost=masterHost;
		this.id=id;
		this.namespaces=namespaces;
		this.fsHost=fsHost;
		this.connector=connector;
	}
	
	public void run(){
		LOG.info("Ping back master at " + masterHost + " registered as " +  id);
		
		HttpURLConnection fsConn=null;

		try{
			String strUrl = connector+"://"+masterHost+"/namespace/alive?" 
			+ RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(id, "UTF-8");
		
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();

			/*
			if(fsConn.getResponseCode()==HttpURLConnection.HTTP_NOT_FOUND){
				//previously has problem registering, register again
				registerFileSliceServer();
			}
			*/ 
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());
			}
		}catch(Exception ex){
			LOG.warn((new Date()).toString()+"\tWARNING: Problem with ping back master.");
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	public boolean registerFileSliceServer(Properties p){
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = connector+"://"+masterHost+"/namespace/register?" 
				+ RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(namespaces, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(id, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"="+URLEncoder.encode(fsHost, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_TYPE.value()+"=" + FileSliceServerInfo.Type.RESTLET.value()
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value()+"=" + SliceStoreProperties.getString(SliceStoreProperties.PropName.REQ_VERIFY)
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_MODE.value()+"="+FileIOMode.STREAM.value();
			LOG.debug(strUrl);

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);
			if(p!=null && p.size()>0){
				fsConn.setDoOutput(true);
				
				OutputStream out=fsConn.getOutputStream();
				p.store(out, null);
				out.close();
			}
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());
			}
			
			if(SliceStoreProperties.getBool(SliceStoreProperties.PropName.REQ_VERIFY)){
				BufferedReader in = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
				String key=in.readLine();
				in.close();

				SliceStoreProperties.set(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+id, key);
			}else
				SliceStoreProperties.remove(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+id);
			
			return true;
		}catch(Exception ex){
			LOG.warn("Problem registering file slice server. Error -> " + ex.getMessage());
			//ex.printStackTrace();
			return false;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
}
