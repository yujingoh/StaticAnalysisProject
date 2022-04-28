package sg.edu.nyp.sit.svds.master;

import static org.junit.Assert.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.filestore.SliceStoreProperties;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;
import sg.edu.nyp.sit.svds.metadata.User;

public class FileChangeModeTest {
	private static final Log LOG = LogFactory.getLog(FileChangeModeTest.class);
			
	private static String basePath=null;
	private static String masterFilePath=null;
	private int masterFilePort=9010, masterNSPort=9011;
	private String masterFileHost="localhost:"+masterFilePort, masterNSHost="localhost:"+masterNSPort;
	private String masterNSConnector="http", masterFileConnector="http";
	private String namespace="urn:sit.nyp.edu.sg";
	
	private static String fsvr1_path=null, fsvr2_path=null;
	private String fsvr1_id="TESTFS1", fsvr2_id="TESTFS2";
	
	private sg.edu.nyp.sit.svds.master.Main msvr=null;
	private sg.edu.nyp.sit.svds.filestore.Main fsvr1=null;
	private sg.edu.nyp.sit.svds.filestore.Main fsvr2=null;
	
	private User user=new User("moeif_usrtest", "p@ssw0rd");
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=masterFilePath=System.getProperty("user.dir");
		
		fsvr1_path=basePath+"/filestore/storage/fsvr1";
		fsvr2_path=basePath+"/filestore/storage/fsvr2";
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
	
	private void delSliceStorage(String path){
		//create the directory if necessary
		File f=new File(path);
		if(f.exists()){
			//deletes the files inside
			for(File i: f.listFiles()){
				i.delete();
			}
		}
		f.delete();
	}

	@Before
	public void setUp() throws Exception {
		deleteFiles();
		delSliceStorage(fsvr1_path);
		delSliceStorage(fsvr2_path);
		
		File f=new File(fsvr1_path);
		f.mkdir();
		f=null;
		
		f=new File(fsvr2_path);
		f.mkdir();
		f=null;
		
		msvr = new sg.edu.nyp.sit.svds.master.Main(basePath+"/resource/IDAProp.properties", 
				basePath+"/resource/MasterConfig_unitTest.properties"); 
		MasterProperties.set("master.directory", masterFilePath);
		MasterProperties.set("master.file.port", masterFilePort);
		MasterProperties.set("master.namespace.port", masterNSPort);
		//do not turn on 2 way ssl
		MasterProperties.set("master.namespace.ssl.clientauth", "off");
		MasterProperties.set("master.maintainence.ssl.clientauth", "off");
		msvr.startupMain();
		
		if(MasterProperties.getString("master.file.ssl").equalsIgnoreCase("on")){
			masterFileConnector="https";
			masterFileHost=MasterProperties.getString("master.file.ssl.address")+":"+masterFilePort;
		}
		if(MasterProperties.getString("master.namespace.ssl").equalsIgnoreCase("on")){
			masterNSConnector="https";
			masterNSHost=MasterProperties.getString("master.namespace.ssl.address")+":"+masterNSPort;
		}
		
		if(masterNSConnector.equalsIgnoreCase("https") || masterFileConnector.equalsIgnoreCase("https")){
			System.setProperty(Resources.TRUST_STORE, Resources.findFile(MasterProperties.getString("ssl.truststore")));
			System.setProperty(Resources.TRUST_STORE_PWD, MasterProperties.getString("ssl.truststorepwd"));
			System.setProperty(Resources.TRUST_STORE_TYPE, MasterProperties.getString("ssl.truststoretype"));
		}
		
		fsvr1=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_noverify_unitTest.properties");
		fsvr2=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_noverify_unitTest.properties");
		
		SliceStoreProperties.set("master.address", masterNSHost);
		SliceStoreProperties.set("slicestore.namespace", namespace);
		
		fsvr1.startup(fsvr1_id, 8010, 8011, "localhost", fsvr1_path, 0);
		changeFSMode(fsvr1_id, "localhost", 8010, FileIOMode.STREAM);

		fsvr2.startup(fsvr2_id, 8020, 8021, "localhost", fsvr2_path, 0);
		changeFSMode(fsvr2_id, "localhost", 8020, FileIOMode.NON_STREAM);
	}
	
	@After
	public void tearDown() throws Exception {
		//sleep for 5 seconds so as to wait for all request to be completed
		//at either the master or slice server side
		Thread.sleep(1000*5);
		
		if(fsvr1!=null) fsvr1.shutdown();
		if(fsvr2!=null) fsvr2.shutdown();
		if(msvr!=null) msvr.shutdown();
		
		deleteFiles();
		
		delSliceStorage(fsvr1_path);
		delSliceStorage(fsvr2_path);
	}
	
	@Test
	public void fileChangeModeResumeTest() throws Exception{
		//create slices with no checksum. *ensure the slice data is longer so the change mode 
		//won't finish before the request is made
		String sliceData=(new Date()).getTime()+"";
		StringBuilder str=new StringBuilder();
		for(int i=0; i<100; i++)
			str.append(sliceData);
		sliceData=str.toString();
		String sliceSvr[]=new String[]{fsvr1_id, fsvr2_id, fsvr2_id};
		String slicePath[]=new String[]{fsvr1_path, fsvr2_path, fsvr2_path};
		java.io.File f;
		FileOutputStream out;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		for(int i=0; i<3; i++){
			f=new java.io.File(slicePath[i]+"/"+i);
			f.createNewFile();
			
			out=new FileOutputStream(f);
			out.write(sliceData.getBytes());
			out.close();
			out=null;

			slices.add(new FileSliceInfo(i+"", sliceSvr[i], sliceData.getBytes().length, null, i));
		}
		
		FileInfo fi=new FileInfo(FileInfo.PATH_SEPARATOR+"spChgMode", namespace, FileInfo.Type.FILE);
		fi.setOwner(user);
		fi.setFileSize(100);
		fi.setIdaVersion(1);
		fi.setLockBy(user);
		fi.setSlices(slices);
		fi.setBlkSize(0);
		
		//add the file
		addFileInfo(fi);
		
		//request to change mode, slice 2 and 3 should move from fsvr2 to fsvr1
		chgFileMode(fi,FileIOMode.STREAM);
		
		//shut down master server
		msvr.shutdown();
		LOG.debug("Master shutdown.");
		
		//check that there are still some slice at original location
		int cnt=0;
		for (int i=1; i<3; i++){
			f=new java.io.File(fsvr2_path+"/"+i);
			if(f.exists())
				cnt++;
		}
		LOG.debug(cnt + " slices are at original location.");
		if(cnt==0)
			fail("No slices are found at original location.");
		
		//start up master server
		msvr=new sg.edu.nyp.sit.svds.master.Main(basePath+"/resource/IDAProp.properties",
				basePath+"/resource/MasterConfig_unitTest.properties");
		MasterProperties.set("master.directory", masterFilePath);
		MasterProperties.set("master.file.port", masterFilePort);
		MasterProperties.set("master.namespace.port", masterNSPort);
		//do not turn on 2 way ssl
		MasterProperties.set("master.namespace.ssl.clientauth", "off");
		MasterProperties.set("master.maintainence.ssl.clientauth", "off");
		msvr.startupMain();
		
		//wait for a while to check if the change mode is complete
		Thread.sleep(10*1000);
		
		//check if the slice has been moved to another slice store location
		for(int i=1; i<3; i++){
			f=new java.io.File((slicePath[i].equals(fsvr1_path)?fsvr2_path:fsvr1_path)+"/"+i);
			if(!f.exists())
				fail("Slice " + i + " does not exist new at location.");
		}
	}
	
	@Test
	public void fileChangeModeNormalTest() throws Exception{
		//create slices in different slice server
		String sliceData[]=new String[]{"slice0", "slice1", "slice2"};
		byte sliceChecksum[][]=new byte[3][];
		String keyHash="abc";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		for(int i=0; i<3; i++){
			sliceChecksum[i]=md.digest((sliceData[i]+keyHash).getBytes());
			md.reset();
		}
		md=null;
		String sliceSvr[]=new String[]{fsvr1_id, fsvr2_id, fsvr2_id};
		String slicePath[]=new String[]{fsvr1_path, fsvr2_path, fsvr2_path};
		java.io.File f, fChk;
		FileOutputStream out;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		for(int i=0; i<3; i++){
			f=new java.io.File(slicePath[i]+"/"+i);
			f.createNewFile();
			
			fChk=new java.io.File(slicePath[i]+"/"+i+".chk");
			fChk.createNewFile();
			
			out=new FileOutputStream(f);
			out.write(sliceData[i].getBytes());
			out.close();
			out=null;
			
			out=new FileOutputStream(fChk);
			out.write(sliceChecksum[i]);
			out.close();
			out=null;

			slices.add(new FileSliceInfo(i+"", sliceSvr[i], sliceData[i].getBytes().length, Resources.convertToHex(sliceChecksum[i]), i));
		}
		
		FileInfo fi=new FileInfo(FileInfo.PATH_SEPARATOR+"nChgMode", namespace, FileInfo.Type.FILE);
		fi.setOwner(user);
		fi.setFileSize(100);
		fi.setIdaVersion(1);
		fi.setLockBy(user);
		fi.setSlices(slices);
		fi.setBlkSize(1024);
		fi.setKeyHash(keyHash);
		
		//add the file
		addFileInfo(fi);
		
		//request to change mode, slice 2 and 3 should move from fsvr2 to fsvr1
		chgFileMode(fi,FileIOMode.STREAM);
		
		//wait for a while to check if the change mode is complete
		Thread.sleep(10*1000);
		
		//check if the slice has been moved to another slice store location
		for(int i=1; i<3; i++){
			f=new java.io.File((slicePath[i].equals(fsvr1_path)?fsvr2_path:fsvr1_path)+"/"+i);
			if(!f.exists())
				fail("Slice " + i + " does not exist new at location.");
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
			if(!in.readLine().equals(sliceData[i]))
				fail("Slice " + i + " contents does not match.");
			
			f=new java.io.File((slicePath[i].equals(fsvr1_path)?fsvr2_path:fsvr1_path)+"/"+i+".chk");
			if(!f.exists())
				fail("Slice " + i + " checksum does not exist at new location.");
		}
	}
	
	private void chgFileMode(FileInfo rec, FileIOMode mode) throws Exception{
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterFileConnector+"://" + masterFileHost + "/file/chgmode?" 
				+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(rec.getNamespace(), "UTF-8")
				+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(rec.getFullPath(), "UTF-8")
				+ "&"+RestletMasterQueryPropName.File.MODE.value()+"="+mode.value()
				+ "&"+RestletMasterQueryPropName.File.USER.value()+"=" + URLEncoder.encode(rec.getLockBy().getId(), "UTF-8")
				+ "&uid="+URLEncoder.encode(user.getId(), "UTF-8")
				+ "&pwd="+URLEncoder.encode(user.getPwd(), "UTF-8");
			
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				fail("Error changing file mode. Error code: " + fsConn.getResponseCode());
			}
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	private void addFileInfo(FileInfo rec) throws Exception{
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterFileConnector+"://" + masterFileHost + "/file/add?" 
			+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(rec.getNamespace(), "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.SEQ.value()+"=" + (new Date()).getTime()
			+ "&"+RestletMasterQueryPropName.File.OWNER.value()+"=" + URLEncoder.encode(rec.getOwner().getId(), "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.TYPE.value()+"=" + rec.getType().value()
			+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(rec.getFullPath(), "UTF-8")
			+ "&"+RestletMasterQueryPropName.File.SIZE.value()+"=" + rec.getFileSize()
			+ "&"+RestletMasterQueryPropName.File.IDA_VERSION.value()+"=" + rec.getIdaVersion()
			+ "&uid="+URLEncoder.encode(user.getId(), "UTF-8")
			+ "&pwd="+URLEncoder.encode(user.getPwd(), "UTF-8")
			+(!rec.verifyChecksum()?"":"&"+RestletMasterQueryPropName.File.FILE_BLKSIZE.value()+"=" + rec.getBlkSize()
				+ "&"+RestletMasterQueryPropName.File.FILE_KEYHASH.value()+"=" + URLEncoder.encode(rec.getKeyHash(), "UTF-8"));
		
			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);
			fsConn.setDoOutput(true);

			OutputStream out=fsConn.getOutputStream();

			out.write((FileSliceInfo.PropName.COUNT.value()+"="+rec.getSlices().size()+"\n").getBytes());
			int cnt=0;
			for(FileSliceInfo i: rec.getSlices()){
				out.write((FileSliceInfo.PropName.NAME.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceName())+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SEQ.value()+cnt+"="+i.getSliceSeq()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SVR.value()+cnt+"="+Resources.encodeKeyValue(i.getServerId())+"\n").getBytes());
				out.write((FileSliceInfo.PropName.LEN.value()+cnt+"="+i.getLength()+"\n").getBytes());
				if(rec.verifyChecksum()) out.write((FileSliceInfo.PropName.CHECKSUM.value()+cnt+"="+Resources.encodeKeyValue(i.getSliceChecksum())+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SEG_CNT.value()+cnt+"=0\n").getBytes());

				out.flush();
				cnt++;
			}

			out.close();
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				fail("Unable to add file. Error code: " + fsConn.getResponseCode());
				return;
			}
			
			BufferedReader in = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
			String strDt=in.readLine();
			in.close();
			
			rec.setCreationDate(new Date(Long.parseLong(strDt)));
			rec.setLastModifiedDate(new Date(Long.parseLong(strDt)));
			rec.setLastAccessedDate(new Date(Long.parseLong(strDt)));
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}

	
	private void changeFSMode(String id, String host, int port, FileIOMode mode) throws Exception{
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterNSConnector+"://"+masterNSHost+"/namespace/register?" 
				+ RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(id, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"="+host+":" + port 
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_TYPE.value()+"=" + FileSliceServerInfo.Type.RESTLET.value()
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_REQ_VERIFY.value()+"=" + SliceStoreProperties.getString(SliceStoreProperties.PropName.REQ_VERIFY)
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_MODE.value()+"="+mode.value();

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);
			
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
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
}
