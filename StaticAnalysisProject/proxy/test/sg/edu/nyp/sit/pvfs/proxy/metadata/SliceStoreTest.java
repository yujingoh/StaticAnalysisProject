package sg.edu.nyp.sit.pvfs.proxy.metadata;

import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import sg.edu.nyp.sit.pvfs.proxy.Main;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class SliceStoreTest {
	private static String basePath=null;
	private static String DBPath=null;
	private static String testDBPath=null;
	
	private static sg.edu.nyp.sit.pvfs.proxy.Main main=null;
	private static int port=6010;
	
	private static String acctId="VIC", acctKey="123456789";
	private static String driveName="virtual";
	private static long memUsed=1024000;
	
	private static String token=null;

	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=System.getProperty("user.dir");
		DBPath=basePath+"/filestore";
		testDBPath=basePath+"/proxy/test/sg/edu/nyp/sit/pvfs/proxy/metadata/test.db";
		
		java.io.File f=new java.io.File(DBPath+"/"+acctId);
		if(f.exists()) f.delete();
		
		//copy db over 
		f.createNewFile();
		FileOutputStream out=new FileOutputStream(f);
		FileInputStream in=new FileInputStream(new java.io.File(testDBPath));
		byte[] tmp=new byte[1024];
		int len;
		while((len=in.read(tmp))!=-1)
			out.write(tmp, 0, len);
		in.close();
		out.close();
		
		main=new sg.edu.nyp.sit.pvfs.proxy.Main(basePath+"/resource/ProxyConfig_unitTest.properties");
		main.start(port, DBPath);
		
		token=sendWebRequestWithStringResponse("http://localhost:" + port+ "/subscriber/initRemote?"
				+RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
				+"&"+RestletProxyQueryPropName.Subscriber.KEY.value()+"="+acctKey);
		System.out.println("GET TOKEN: " + token);
		
		Field fd=Main.class.getDeclaredField("lst_usr");
		fd.setAccessible(true);
		Map<String, SubscriberMetadataInfo> lst=(Map<String, SubscriberMetadataInfo>) fd.get(main);
		lst.put(acctId, new SubscriberMetadataInfo(DBPath+"/"+acctId, 1024000, 1, "virtual"));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.print("\nREMOTE DELETE...");
		String query=RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.Subscriber.DT.value()+"=1";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/delRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote session delete");
		System.out.print("DONE");
		
		main.shutdown();
		
		java.io.File f=new java.io.File(DBPath+"/"+acctId);
		if(f.exists()) f.delete();
	}
	
	@Test
	public void test() throws Exception{
		getDrivename();
		getMemory();
		
		registerSliceStore();
		availableSliceStores();
		getSliceStore();
		
		removeSliceStore();
	}
	
	private void removeSliceStore() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId
		+"&"+RestletProxyQueryPropName.SliceStore.SVR_ID.value()+"=abcfs";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/slicestore/remove?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error removing slice store.");
	}
	
	private void getSliceStore() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_ID.value()+"=abcfs";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/slicestore/get?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			int resp=conn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				fail("Error get slice store.");
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);
			
			if(!data.get(FileSliceServerInfo.PropName.HOST.value()).toString().equals("localhost") ||
					Integer.parseInt(data.get(FileSliceServerInfo.PropName.TYPE.value()).toString())!=FileSliceServerInfo.Type.RESTLET.value() ||
					Integer.parseInt(data.get(FileSliceServerInfo.PropName.MODE.value()).toString())!=FileIOMode.STREAM.value() ||
					Integer.parseInt(data.get(FileSliceServerInfo.PropName.STATUS.value()).toString())!=FileSliceServerInfo.Status.ACTIVE.value() ||
					!data.get(FileSliceServerInfo.PropName.KEYID.value()).toString().equals("usr") ||
					!data.get(FileSliceServerInfo.PropName.KEY.value()).toString().equals("password"))
				fail("Slice store information does not match");
			
			if(!data.containsKey(FileSliceServerInfo.PropName.OPT.value()+".status.address") ||
					!data.containsKey(FileSliceServerInfo.PropName.OPT.value()+".stauts.ssl"))
				fail("Slice store properties does not match.");
			
			if(!data.get(FileSliceServerInfo.PropName.OPT.value()+".status.address").toString().equals("localhost") ||
					!data.get(FileSliceServerInfo.PropName.OPT.value()+".stauts.ssl").toString().equals("off"))
				fail("Slice store properties value does not match");
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private void availableSliceStores() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId;
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/slicestore/available?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			int resp=conn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				fail("Error get available slice stores.");
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);
			
			boolean isFound=false;
			for(int i=0; i<Integer.parseInt(data.get(FileSliceServerInfo.PropName.COUNT.value()).toString()); i++){
				if(data.get(FileSliceServerInfo.PropName.ID.value()+i).toString().equals("abcfs") &&
						Integer.parseInt(data.get(FileSliceServerInfo.PropName.STATUS.value()+i).toString())==FileSliceServerInfo.Status.ACTIVE.value()){
					isFound=true;
					break;
				}
			}
			
			if(!isFound)
				fail("Registered slice store cannot be found.");
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private void registerSliceStore() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_ID.value()+"=abcfs"
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_HOST.value()+"=localhost"
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_TYPE.value()+"="+FileSliceServerInfo.Type.RESTLET.value()
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_MODE.value()+"="+FileIOMode.STREAM.value()
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_KEY_ID.value()+"=usr"
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_KEY.value()+"=password"
			+"&"+RestletProxyQueryPropName.SliceStore.SVR_STATUS.value()+"="+FileSliceServerInfo.Status.ACTIVE.value();
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/slicestore/register?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoOutput(true);
			
			OutputStream out=conn.getOutputStream();
			out.write(("status.address=localhost\n").getBytes());
			out.write(("stauts.ssl=off\n").getBytes());
			out.close();
			
			if(conn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				fail("Error register slice store");
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private void getMemory() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId;
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		if(Long.parseLong(sendWebRequestWithStringResponse("http://localhost:" + port+ "/slicestore/mem?" + query))!=memUsed)
			fail("Memory does not match.");
	}
	
	private void getDrivename() throws Exception{
		String query=RestletProxyQueryPropName.SliceStore.SUBSCRIBER.value()+"="+acctId;
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		if(!sendWebRequestWithStringResponse("http://localhost:" + port+ "/slicestore/get_ns?" + query).equals(driveName))
			fail("Drive name does not match.");
	}
	
	private static int sendWebRequestNoResponse(String url) throws Exception{
		HttpURLConnection conn=null;
		
		try{
			URL u = new URL(url);
			conn=(HttpURLConnection)u.openConnection();
			
			return conn.getResponseCode();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private static String sendWebRequestWithStringResponse(String url) throws Exception{
		HttpURLConnection conn=null;
		
		try{
			URL u = new URL(url);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			int resp=conn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception("Error encountered with send web request: " + resp + " " + conn.getResponseMessage());
			
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String tmp=in.readLine();
			in.close();
			
			return tmp;
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
}
