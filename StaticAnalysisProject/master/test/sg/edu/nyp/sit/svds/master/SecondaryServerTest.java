package sg.edu.nyp.sit.svds.master;

import static org.junit.Assert.*;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import org.junit.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.SecondaryServer;

public class SecondaryServerTest {
	private static String basePath=null;
	private static String masterFilePath=null;
	private static String testFilePath=null;
	
	private static SecondaryServer ss=null;
	
	private static sg.edu.nyp.sit.svds.master.Main msvr=null;
	
	private static void deleteFiles(){
		File f=new File(masterFilePath+"/svds.img");
		if(f.exists())
			f.delete();
		
		f=new File(masterFilePath+"/svdsTrans.log");
		if(f.exists())
			f.delete();
		
		f=new File(masterFilePath+"/namespaceTrans.log");
		if(f.exists())
			f.delete();
	}
	
	@BeforeClass
	public static void init() throws Exception {
		basePath=masterFilePath=System.getProperty("user.dir");
		testFilePath=basePath+"/master/test/sg/edu/nyp/sit/svds/master";
		
		deleteFiles();
		
		msvr=new sg.edu.nyp.sit.svds.master.Main(basePath+"/resource/IDAProp.properties",
				basePath+"/resource/MasterConfig_unitTest.properties");
		MasterProperties.set("master.directory", masterFilePath);
		MasterProperties.set("master.maintainence.port", 9012);
		//do not turn on 2 way ssl
		MasterProperties.set("master.namespace.ssl.clientauth", "off");
		MasterProperties.set("master.maintainence.ssl.clientauth", "off");
		msvr.startupMain();
		
		String connector="http";
		String masterHost="localhost:9012";
		if(MasterProperties.getString("master.maintainence.ssl").equalsIgnoreCase("on")){
			connector="https";
			masterHost=MasterProperties.getString("master.maintainence.ssl.address")+":9012";
			
			System.setProperty(Resources.TRUST_STORE, Resources.findFile(MasterProperties.getString("ssl.truststore")));
			System.setProperty(Resources.TRUST_STORE_PWD, MasterProperties.getString("ssl.truststorepwd"));
			System.setProperty(Resources.TRUST_STORE_TYPE, MasterProperties.getString("ssl.truststoretype"));
		}
		
		ss=new SecondaryServer(masterHost, connector);
	}
	
	@AfterClass
	public static void shutdown() throws Exception{
		//sleep for 5 seconds so as to wait for all request to be completed
		//at either the master or slice server side
		Thread.sleep(1000*5);
		
		if(msvr!=null) msvr.shutdown();
	}
	
	@Before
	public void setUp()throws Exception {
		//copy the test files over
		File f=new File(masterFilePath + "/svds.img");
		if(f.exists())
			f.delete();
		
		byte[] data=new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		
		f.createNewFile();
		FileOutputStream out=new FileOutputStream(f);
		FileInputStream in=new FileInputStream(testFilePath+"/svds_testBefore.img");
		while((len=in.read(data))!=-1)
			out.write(data, 0,len);
		out.flush();
		out.close();
		in.close();
		
		f=new File(masterFilePath + "/svdsTrans.log");
		if(f.exists())
			f.delete();
		
		f.createNewFile();
		out=new FileOutputStream(f);
		in=new FileInputStream(testFilePath+"/svdsTrans_test");
		while((len=in.read(data))!=-1)
			out.write(data, 0,len);
		out.flush();
		out.close();
		in.close();
		
		f=new File(masterFilePath + "/namespaceTrans.log");
		if(f.exists())
			f.delete();
		
		f.createNewFile();
		out=new FileOutputStream(f);
		in=new FileInputStream(testFilePath+"/svdsNsTrans_test");
		while((len=in.read(data))!=-1)
			out.write(data, 0,len);
		out.flush();
		out.close();
		in.close();
	}
	
	@After
	public void tearDown(){
		//delete all traces of trans log and image file
		deleteFiles();
	}
	
	@Test
	public void testGetLogSize()throws Exception{
		long testSize=ss.getLogSize();
		long actualSize=(new File(testFilePath+"/svdsTrans_test")).length();

		System.out.println("Test log size: " + testSize);
		System.out.println("Actual log size: " + actualSize);
		
		assertEquals("Log size is incorrect.", actualSize, testSize);
	}

	@Test
	public void testCheckpointImg() throws Exception{
		boolean success=ss.checkpointMaster();

		if(!success)
			fail("Checkpointing image failed.");
		
		//further check the contents of the checkpointed image
		String testChecksum=getFileChecksum(masterFilePath+"/svds.img");
		String actualChecksum=getFileChecksum(testFilePath+"/svds_testAfter.img");

		assertEquals("Checkpointed image contents failed.", actualChecksum, testChecksum);
	}
	
	private String getFileChecksum(String filePath) throws Exception{
		MessageDigest md = MessageDigest.getInstance(Resources.HASH_ALGO);
		InputStream r=new DigestInputStream(new FileInputStream(new File(filePath)), md);
		
		while(r.read()!=-1){}
		r.close();
		
		return Resources.convertToHex(md.digest());
	}
}
