package sg.edu.nyp.sit.svds.master.filestore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class S3SliceStoreRegistration {
	public static final long serialVersionUID = 2L;
	
	private enum PropName{
		CNT("s3.cnt"),
		ID("s3.id"),
		ACCT ("s3.accesskey"),
		KEY ("s3.secretaccesskey"),
		REGION("s3.region"),
		CONTAINER("s3.bucket"),
		NAMESPACE ("s3.namespace");
		
		private String name;
		PropName(String name){ this.name=name; }
		public String value(){ return name; }
	}
	
	private static String masterUrl=null;
	private static String masterProtocol=null;
	
	//problem with the SDK, sometimes during connection will return peer unverified error, so just skip the connectSliceStore
	//and go straight to register with master server
	public static boolean skipConnect=false;
	
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
		S3SliceStoreRegistration.masterUrl=masterUrl;
		S3SliceStoreRegistration.masterProtocol=masterProtocol;
		
		if(!stores.containsKey(PropName.CNT.value()))
			throw new NullPointerException("Slice store count is missing");
		
		int cnt=Integer.parseInt(stores.get(PropName.CNT.value()).toString());
		String id, acct, key, ns, region, container;
		Properties p=new Properties();
		for(int i=0; i<cnt; i++){
			id=stores.get(PropName.ID.value()+i).toString();
			acct=stores.get(PropName.ACCT.value()+i).toString();
			key=stores.get(PropName.KEY.value()+i).toString();
			region=stores.get(PropName.REGION.value()+i).toString();
			container=stores.get(PropName.CONTAINER.value()+i).toString();
			ns=stores.get(PropName.NAMESPACE.value()+i).toString();
			
			if(id==null || acct==null || key==null || ns==null || container==null
					|| region==null || id.length()==0 || acct.length()==0 || key.length()==0
					|| ns.length()==0 || container.length()==0 || region.length()==0){
				System.out.println("Missing information for slice store " + i + ". Registration is skipped");
				continue;
			}
			
			container=container.toLowerCase();
			
			p.clear();
			p.put(FileSliceServerInfo.PropName.KEYID.value(), acct);
			p.put(FileSliceServerInfo.PropName.KEY.value(), key);
			p.put(PropName.CONTAINER.value(), container);
			
			if(!skipConnect && !connectSliceStore(container, p))
				continue;
			
			registerSliceStore(id, region, ns, p);
		}
	}
	
	private static boolean connectSliceStore(String containerName, Properties props){
		try{
			System.out.println(props.get(
					FileSliceServerInfo.PropName.KEYID.value()).toString());
			System.out.println(props.get(
					FileSliceServerInfo.PropName.KEY.value()).toString());
			AmazonS3Client s3=new AmazonS3Client(new BasicAWSCredentials(props.get(
					FileSliceServerInfo.PropName.KEYID.value()).toString(), 
					props.get(FileSliceServerInfo.PropName.KEY.value()).toString()));
			
			//bucket must be created beforehand because buckets are global, hard to check
			//if bucket belongs to owner
			if(!s3.doesBucketExist(containerName)){
				return false;
			}
			
			return true;
		}catch(Exception ex){
			System.out.println("Error connecting to server " + containerName + ".");
			ex.printStackTrace();
			return false;
		}
	}
	
	private static void registerSliceStore(String id, String region, String ns, Properties p){
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterProtocol+"://"+masterUrl+"/namespace/register?" 
				+ RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(ns, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(id, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"=" + URLEncoder.encode(region, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_TYPE.value()+"=" + FileSliceServerInfo.Type.S3.value()
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value()+"=off"
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_MODE.value()+"="+FileIOMode.NON_STREAM.value();

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
