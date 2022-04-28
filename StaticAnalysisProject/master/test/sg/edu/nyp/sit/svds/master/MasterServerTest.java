package sg.edu.nyp.sit.svds.master;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import org.junit.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.filestore.SliceStoreProperties;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class MasterServerTest {	
	private static String basePath=null;
	private static String masterFilePath=null;
	private int masterNSPort=9011, masterFilePort=9010;
	private String masterNSUrl="localhost:"+masterNSPort, masterFileUrl="localhost:"+masterFilePort; //default is localhost
	private String namespace="urn:sit.nyp.edu.sg";
	
	private String fsId="TESTFS1";
	private String masterNSConnector="http", masterFileConnector="http"; //default is connect using http
	private int fsPort=8010;
	
	private Properties props=null;
	
	private sg.edu.nyp.sit.svds.master.Main master=null;
	private sg.edu.nyp.sit.svds.filestore.Main fsvr=null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=masterFilePath=System.getProperty("user.dir");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		
	}
	
	private void deleteFiles(){
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

	@Before
	public void setUp() throws Exception {
		deleteFiles();
		
		master = new sg.edu.nyp.sit.svds.master.Main(basePath+"/resource/IDAProp.properties",
				basePath+"/resource/MasterConfig_unitTest.properties"); 
		MasterProperties.set("master.directory", masterFilePath);
		MasterProperties.set("master.namespace.port", masterNSPort);
		MasterProperties.set("master.file.port", masterFilePort);
		//do not turn on 2 way ssl
		MasterProperties.set("master.namespace.ssl.clientauth", "off");
		MasterProperties.set("master.maintainence.ssl.clientauth", "off");
		
		props=new Properties();
		props.put("prop1", "abc");
		props.put("prop2", "def");
	}

	@After
	public void tearDown() throws Exception {
		//sleep for 5 seconds so as to wait for all request to be completed
		//at either the master or slice server side
		Thread.sleep(1000*5);
		
		if(master!=null) master.shutdown();
		if(fsvr!=null) fsvr.shutdown();
		
		deleteFiles();
	}

	@Test
	public void masterTest() throws Exception{	
		//1. start up master server
		master.startupMain();

		if(MasterProperties.getString("master.namespace.ssl").equalsIgnoreCase("on")){
			masterNSUrl=MasterProperties.getString("master.namespace.ssl.address")+":"+masterNSPort;
			masterNSConnector="https";
		}
		if(MasterProperties.getString("master.file.ssl").equalsIgnoreCase("on")){
			masterFileUrl=MasterProperties.getString("master.file.ssl.address")+":"+masterFilePort;
			masterFileConnector="https";
		}
		
		fsvr=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_verify_unitTest.properties");
		SliceStoreProperties.set("master.address", masterNSUrl);
		SliceStoreProperties.set("slicestore.namespace", namespace);
		fsvr.startup(fsId, fsPort, fsPort+1, "localhost", basePath, 0);
		
		//check if the master is start up in ssl, if yes then set truststore
		if(masterNSConnector.equalsIgnoreCase("https") || masterFileConnector.equalsIgnoreCase("https")){
			System.setProperty(Resources.TRUST_STORE, Resources.findFile(MasterProperties.getString("ssl.truststore")));
			System.setProperty(Resources.TRUST_STORE_PWD, MasterProperties.getString("ssl.truststorepwd"));
			System.setProperty(Resources.TRUST_STORE_TYPE, MasterProperties.getString("ssl.truststoretype"));
		}
		
		addFSProps();
		
		//1a. check that the slice store has a key
		if(!SliceStoreProperties.exist(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+fsId))
			fail("Slice store key is not assigned.");
		
		//2a. pump in dummy records through master server
		String data[][]={{FileInfo.PATH_SEPARATOR+"f1.txt", FileInfo.Type.FILE.toString()},
				{FileInfo.PATH_SEPARATOR+"d1", FileInfo.Type.DIRECTORY.toString()},
				{FileInfo.PATH_SEPARATOR+"d1"+FileInfo.PATH_SEPARATOR+"f1.txt", FileInfo.Type.FILE.toString()},
				{FileInfo.PATH_SEPARATOR+"f1.jpg", FileInfo.Type.FILE.toString()}};
		for(int i=0; i<data.length; i++)
			addDummyFileRecord(data[i][0], data[i][1]);
		
		//3. shut down master server
		master.shutdown();
		
		//4. check existence of log file
		File f=new File(masterFilePath+"/svdsTrans.log");
		if(!f.exists())
			fail("File log file should exist.");
		f=null;
		f=new File(masterFilePath+"/namespaceTrans.log");
		if(!f.exists())
			fail("Namespace log file should exist.");
		f=null;
		
		//5. start up master server again
		master.startupMain();

		//6a. random check existence of previous pumped records
		//we test a percentage of the pumped records 0-100
		int tPercent=20;
		int testCnt=(data.length*tPercent/100)+1;
		
		Random r=new Random();
		int index;
		for(int i=0; i<testCnt; i++){
			index=r.nextInt(data.length);
			isFileRecordExist(data[index][0], data[index][1]);
		}
		
		//6b. check if props can be retrieved
		checkPropsExist();
	}
	
	private void checkPropsExist(){
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl=masterFileConnector+"://" + masterFileUrl + "/file/generate?"
			+ RestletMasterQueryPropName.File.NAMESPACE.value()+"="+URLEncoder.encode(namespace, "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.SIZE.value()+"=1"
			+ "&"+RestletMasterQueryPropName.File.MODE.value()+"="+FileIOMode.BOTH.value();
			
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			fsConn.setDoInput(true);
			
			int resp=fsConn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception(resp+":"+fsConn.getResponseMessage());
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);
			
			for(Object key:props.keySet()){
				assertTrue("Slice server prop not found.", data.containsKey(FileSliceServerInfo.PropName.OPT.value()+"0."+key.toString()));
				assertTrue("Slice server prop value does not match.", 
						data.get(FileSliceServerInfo.PropName.OPT.value()+"0."+key.toString()).toString().equals(props.get(key).toString()));
			}
		}catch(Exception ex){
			ex.printStackTrace();
			fail("Error check props. " + ex.getMessage());
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}		
	
	private void addFSProps(){
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl=masterNSConnector+"://" + masterNSUrl + "/namespace/register?"
			+RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
			+"&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"="+URLEncoder.encode(fsId, "UTF-8")
			+"&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"="+URLEncoder.encode("localhost:"+fsPort, "UTF-8")
			+"&"+RestletMasterQueryPropName.Namespace.SVR_TYPE.value()+"="+FileSliceServerInfo.Type.RESTLET.value()
			+"&"+RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value()+"=on"
			+"&"+RestletMasterQueryPropName.Namespace.SVR_MODE.value()+"="+FileIOMode.STREAM.value();
			
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			fsConn.setDoOutput(true);
			
			OutputStream out=fsConn.getOutputStream();
			props.store(out, null);
			out.flush();
			out.close();
			
			int resp=fsConn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception(resp+":"+fsConn.getResponseMessage());
		}catch(Exception ex){
			ex.printStackTrace();
			fail("Error adding props. " + ex.getMessage());
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	private void isFileRecordExist(String filename, String typeStr){
		HttpURLConnection fsConn=null;
		
		try{
			FileInfo.Type type=FileInfo.Type.valueOf(typeStr);
			
			String strUrl = masterFileConnector+"://" + masterFileUrl + "/file/get?" 
			+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(filename, "UTF-8");
			
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			fsConn.setDoInput(true);
			
			int resp=fsConn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception(resp+":"+fsConn.getResponseMessage());
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);
			
			if(data.size()<=4)
				fail("File " + filename + "  does not exist.");
			
			if(type!=FileInfo.Type.valueOf(Integer.parseInt(data.get(FileInfo.PropName.TYPE.value()).toString())))
				fail("File " + filename + " is not the correct type.");
			
			//check that the slice store key exist
			if(type==FileInfo.Type.FILE){
				if(data.get(FileSliceServerInfo.PropName.KEY.value()+"0").toString().length()==0)
					fail("Slice store key is not refreshed.");
			}
		}catch(Exception ex){
			ex.printStackTrace();
			fail("Error checking dummy file records. " + ex.getMessage());
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	private void addDummyFileRecord(String filename, String typeStr){
		HttpURLConnection fsConn=null;
		
		try{
			Date now=new Date();
			FileInfo.Type type=FileInfo.Type.valueOf(typeStr);
			
			String strUrl = masterFileConnector+"://" + masterFileUrl + "/file/add?" 
			+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode("urn:sit.nyp.edu.sg", "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.SEQ.value()+"=" + now.getTime()
			+ "&"+RestletMasterQueryPropName.File.OWNER.value()+"=" + URLEncoder.encode("victoria", "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.TYPE.value()+"=" + type.value()
			+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(filename, "UTF-8")
			+(type==FileInfo.Type.DIRECTORY?"":"&"+RestletMasterQueryPropName.File.SIZE.value()+"=100"
					+ "&"+RestletMasterQueryPropName.File.IDA_VERSION.value()+"=1"
					+ "&"+RestletMasterQueryPropName.File.FILE_BLKSIZE.value()+"=102400"
					+ "&"+RestletMasterQueryPropName.File.FILE_KEYHASH.value()+"=123");
		
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			if(type==FileInfo.Type.FILE){
				fsConn.setDoOutput(true);
				
				OutputStream out=fsConn.getOutputStream();
				out.write((FileSliceInfo.PropName.COUNT.value()+"=1\n").getBytes());
				out.write((FileSliceInfo.PropName.SEQ.value()+"0=0\n").getBytes());
				out.write((FileSliceInfo.PropName.NAME.value()+"0=slice0\n").getBytes());
				out.write((FileSliceInfo.PropName.SVR.value()+"0="+fsId+"\n").getBytes());
				out.write((FileSliceInfo.PropName.LEN.value()+"0=100\n").getBytes());
				out.write((FileSliceInfo.PropName.SEG_CNT.value()+"0=0\n").getBytes());
				out.flush();
				out.close();
			}
			
			int resp=fsConn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				throw new Exception(resp+":"+fsConn.getResponseMessage());
		}catch(Exception ex){
			ex.printStackTrace();
			fail("Error adding dummy file records. " + ex.getMessage());
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
}
