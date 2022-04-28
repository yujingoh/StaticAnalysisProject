package sg.edu.nyp.sit.pvfs.proxy.subscriber;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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


public class SubscriberTest {
	private static String basePath=null;
	private static String DBPath=null;
	
	private static sg.edu.nyp.sit.pvfs.proxy.Main main=null;
	private static int port=6010;
	
	private static String acctId="VIC", acctKey="123456789";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=System.getProperty("user.dir");
		DBPath=basePath+"/filestore";
		
		java.io.File f=new java.io.File(DBPath+"/"+acctId);
		if(f.exists()) f.delete();
		
		main=new sg.edu.nyp.sit.pvfs.proxy.Main(basePath+"/resource/ProxyConfig_unitTest.properties");
		main.start(port, DBPath);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		main.shutdown();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testRemote() throws Exception{
		String token=sendWebRequestWithResponse("http://localhost:" + port+ "/subscriber/initRemote?"
				+RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
				+"&"+RestletProxyQueryPropName.Subscriber.KEY.value()+"="+acctKey);
		System.out.println("GET TOKEN: " + token);
		
		//create the query string with signature for use later
		String query=RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.Subscriber.DT.value()+"=1";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		md=null;
		
		System.out.print("REMOTE AUTH...");
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/authRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote authentication");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE CHECK...");
		String ret=sendWebRequestWithResponse("http://localhost:" + port+ "/subscriber/chkRemote?" + query);
		System.out.print("..." + ret + "...");
		if(!ret.equals("2")) fail("Error in remote checking after authentication");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE END...");
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/endRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote session end");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE CHECK...");
		ret=sendWebRequestWithResponse("http://localhost:" + port+ "/subscriber/chkRemote?" + query);
		System.out.print("..." + ret + "...");
		if(!ret.equals("0")) fail("Error in remote checking after session ended");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE DELETE...");
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/delRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote session delete");
		System.out.print("DONE");
		
		System.out.print("\nREMOTE CHECK...");
		ret=sendWebRequestWithResponse("http://localhost:" + port+ "/subscriber/chkRemote?" + query);
		System.out.print("..." + ret + "...");
		if(!ret.equals("-1")) fail("Error in remote checking after session deleted");
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
	
	private String sendWebRequestWithResponse(String url) throws Exception{
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
