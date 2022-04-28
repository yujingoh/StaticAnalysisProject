package sg.edu.nyp.sit.pvfs.proxy.metadata;


import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class MetadataTest {
	private static String basePath=null;
	private static String DBPath=null;
	private static String testDBPath=null;
	
	private static sg.edu.nyp.sit.pvfs.proxy.Main main=null;
	private static int port=6010;
	
	private static String acctId="VIC", acctKey="123456789";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=System.getProperty("user.dir");
		DBPath=basePath+"/filestore";
		testDBPath=basePath+"/proxy/test/sg/edu/nyp/sit/pvfs/proxy/metadata/test.db";
		
		java.io.File f=new java.io.File(DBPath+"/"+acctId);
		if(f.exists()) f.delete();
		
		main=new sg.edu.nyp.sit.pvfs.proxy.Main(basePath+"/resource/ProxyConfig_unitTest.properties");
		main.start(port, DBPath);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		main.shutdown();
		
		java.io.File f=new java.io.File(DBPath+"/"+acctId);
		if(f.exists()) f.delete();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws Exception{
		String token=sendWebRequestWithStringResponse("http://localhost:" + port+ "/subscriber/initRemote?"
				+RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
				+"&"+RestletProxyQueryPropName.Subscriber.KEY.value()+"="+acctKey);
		System.out.println("GET TOKEN: " + token);
		
		//create the query string with signature for use later
		String query=RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.Subscriber.DT.value()+"=1"
			+"&"+RestletProxyQueryPropName.Metadata.MEMORY.value()+"=1024000"
			+"&"+RestletProxyQueryPropName.Metadata.CURR_IDA_VERSION.value()+"=1"
			+"&"+RestletProxyQueryPropName.Metadata.DRIVE_NAME.value()+"=virtual";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		System.out.print("\nSEND METADATA...");
		FileInputStream in=new FileInputStream(testDBPath);
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		md.reset();
		byte[] tmp=new byte[1024];
		int len;
		while((len=in.read(tmp))!=-1){
			out.write(tmp, 0, len);
			md.update(tmp, 0, len);
		}
		out.close();
		String calDigest=Resources.convertToHex(md.digest());
		
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/metadata/save?" + query, out.toByteArray())!=HttpURLConnection.HTTP_OK)
			fail("Error in sending metadata");
		
		out=null;
		System.out.print("DONE");
		
		System.out.print("\nGET METADATA...");
		md.reset();
		String getDigest=Resources.convertToHex(md.digest(sendWebRequestWithByteResponse("http://localhost:" + port+ "/metadata/get?" + query)));
		if(!getDigest.equals(calDigest))
			fail("Error in getting metadata");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE DELETE...");
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/delRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote session delete");
		System.out.print("DONE");
	}
	
	private int sendWebRequestNoResponse(String url) throws Exception{
		HttpURLConnection conn=null;
		
		try{
			URL u = new URL(url);
			conn=(HttpURLConnection)u.openConnection();
			
			return conn.getResponseCode();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private int sendWebRequestNoResponse(String url, byte[] data) throws Exception{
		HttpURLConnection conn=null;
		
		try{
			URL u = new URL(url);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoOutput(true);
			
			OutputStream out=conn.getOutputStream();
			out.write(data);
			out.close();
			
			return conn.getResponseCode();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private byte[] sendWebRequestWithByteResponse(String url) throws Exception{
		HttpURLConnection conn=null;
		
		try{
			URL u = new URL(url);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			int resp=conn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception("Error encountered with send web request: " + resp + " " + conn.getResponseMessage());
			
			InputStream in=conn.getInputStream();
			ByteArrayOutputStream out=new ByteArrayOutputStream();
			byte[] data=new byte[1024];
			int len;
			while((len=in.read(data))!=-1)
				out.write(data, 0, len);
			out.close();
			in.close();
			
			return out.toByteArray();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private String sendWebRequestWithStringResponse(String url) throws Exception{
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
