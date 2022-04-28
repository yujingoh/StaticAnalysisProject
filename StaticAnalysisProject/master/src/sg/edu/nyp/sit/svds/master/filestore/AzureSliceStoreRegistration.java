package sg.edu.nyp.sit.svds.master.filestore;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

import com.microsoft.windowsazure.services.core.storage.*;
import com.microsoft.windowsazure.services.blob.client.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class AzureSliceStoreRegistration {
	public static final long serialVersionUID = 3L;
	
	private enum PropName{
		CNT("azure.cnt"),
		ID("azure.id"),
		ACCT ("azure.storageaccount"),
		KEY ("azure.secretaccesskey"),
		URL ("azure.url"),
		NAMESPACE ("azure.namespace"),
		USE_DEV ("azure.usedevelopment");
		
		private String name;
		PropName(String name){ this.name=name; }
		public String value(){ return name; }
	}
	
	private static String masterUrl=null;
	private static String masterProtocol=null;
	
	public static void main(String args[]) throws Exception{
		if(args.length<3 || args[0].trim().length()==0 || args[1].trim().length()==0
				|| args[2].trim().length()==0)
			throw new NullPointerException("Missing master server protocol and/or URL, registration file location");
		
		File f=new File(args[0].trim());
		if(!f.exists())
			throw new NullPointerException("Registration file does not exist.");
		
		if(args[2].trim().equalsIgnoreCase("https")){
			if(args.length-3<3){
				System.out.println("WARNING: Secure connection to master application is indicated but no certificate key store is passed in.\n"
						+"Ensure the certificate is installed in the computer or else registration might fail.");
			}else{
				String keystore=args[3].trim();
				String keystorepwd=args[4].trim();
				String keystoretype=args[5].trim();
				
				System.setProperty(Resources.TRUST_STORE, Resources.findFile(keystore));
				System.setProperty(Resources.TRUST_STORE_PWD, keystorepwd);
				System.setProperty(Resources.TRUST_STORE_TYPE, keystoretype);
			}
		}
		
		Properties stores=new Properties();
		FileInputStream in=new FileInputStream(f);
		stores.load(in);
		in.close();
		in=null;
		
		register(args[2].trim(), args[1].trim(), stores);
	}
	
	public static void register(String masterProtocol, String masterUrl, Properties stores) throws Exception{
		AzureSliceStoreRegistration.masterUrl=masterUrl;
		AzureSliceStoreRegistration.masterProtocol=masterProtocol;
		
		if(!stores.containsKey(PropName.CNT.value()))
			throw new NullPointerException("Slice store count is missing");
		
		int cnt=Integer.parseInt(stores.get(PropName.CNT.value()).toString());
		String id, url, acct, key, ns, useDev;
		Properties p=new Properties();
		for(int i=0; i<cnt; i++){
			id=stores.get(PropName.ID.value()+i).toString();
			url=stores.get(PropName.URL.value()+i).toString();
			acct=stores.get(PropName.ACCT.value()+i).toString();
			key=stores.get(PropName.KEY.value()+i).toString();
			ns=stores.get(PropName.NAMESPACE.value()+i).toString();
			useDev="0";
			
			if(id==null || url==null || acct==null || key==null || ns==null 
					|| id.length()==0 || url.length()==0 || acct.length()==0 || key.length()==0
					|| ns.length()==0){
				System.out.println("Missing information for slice store " + i + ". Registration is skipped");
				continue;
			}
			
			if(stores.containsKey(PropName.USE_DEV.value()+i) && 
					stores.get(PropName.USE_DEV.value()+i).toString().equals("1"))
				useDev="1";
			
			p.clear();
			p.put(FileSliceServerInfo.PropName.KEYID.value(), acct);
			p.put(FileSliceServerInfo.PropName.KEY.value(), key);
			p.put(PropName.USE_DEV.value(), useDev);
			
			if(!connectSliceStore(url, id, p))
				continue;
			
			registerSliceStore(id, ns, url, p);
		}
	}
	
	private static boolean connectSliceStore(String url, String containerName, Properties props){
		//boolean useDevelopmentUrl=false;
		//if(props.get(PropName.USE_DEV.value()).equals("1"))
		//	useDevelopmentUrl=true;
		
		containerName=containerName.toLowerCase();
		
		try{
			CloudStorageAccount cloudAccount=new CloudStorageAccount(
					new StorageCredentialsAccountAndKey(
							props.get(FileSliceServerInfo.PropName.KEYID.value()).toString(), 
							props.get(FileSliceServerInfo.PropName.KEY.value()).toString()));
			CloudBlobClient blobStorage=cloudAccount.createCloudBlobClient();
			blobStorage.setRetryPolicyFactory(new RetryLinearRetry(1000*5,5));
			
			//serverId becomes the container name
			CloudBlobContainer blobContainer=blobStorage.getContainerReference(containerName);
			blobContainer.createIfNotExist();

			return true;
		}catch(Exception ex){
			System.out.println("Error connecting to server " + containerName + ". " + ex.getMessage());
			ex.printStackTrace();
			return false;
		}
	}
	
	private static void registerSliceStore(String id, String ns, String url, Properties p){
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterProtocol+"://"+masterUrl+"/namespace/register?" 
				+ RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(ns, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(id, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"=" + URLEncoder.encode(url, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_TYPE.value()+"=" + FileSliceServerInfo.Type.AZURE.value()
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value()+"=off"
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_MODE.value()+"="+FileIOMode.STREAM.value();

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoOutput(true);
			
			OutputStream out=fsConn.getOutputStream();
			p.store(out, null);
			out.flush();
			out.close();
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				System.out.println("Error registering server " + id + ". Error code: " 
						+ fsConn.getResponseCode() + ". Error msg: " + fsConn.getResponseMessage());
				return;
			}
			
			System.out.println("Register server " + id + " OK.");
		}catch(Exception ex){
			System.out.println("Error registering server " + id + ".");
			ex.printStackTrace();
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
}
