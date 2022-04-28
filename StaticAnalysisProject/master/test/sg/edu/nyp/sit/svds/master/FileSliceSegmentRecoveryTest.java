package sg.edu.nyp.sit.svds.master;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import sg.edu.nyp.sit.svds.*;
import sg.edu.nyp.sit.svds.filestore.SliceStoreProperties;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class FileSliceSegmentRecoveryTest {
	private static String basePath=null;
	private static String masterFilePath=null;
	private static String namespace="urn:sit.nyp.edu.sg";
	
	private static String fsvrId="TESTFS1";
	private static int fsPort=8010;
	private static int masterFilePort=9010, masterNSPort=9011;
	private static String masterFileHost="localhost:"+masterFilePort, masterNSHost="localhost:"+masterNSPort;
	private static String masterFileConnector="http", masterNSConnector="http";
	
	private static sg.edu.nyp.sit.svds.master.Main master=null;
	private static sg.edu.nyp.sit.svds.filestore.Main fsvr=null;
	
	private int segSize=3, retryLimit=10;;
	private int blkSize=1024;
	private String key="abc", sliceChecksum=null;
	private File[] fsegments=new File[segSize];
	private int[] fsegmentsSize=new int[segSize];
	private String sampleData="File recovery test with multiple segments."; //no line breaks!
	private String fileName=FileInfo.PATH_SEPARATOR+"test.txt";
	private String segPath=null, segNamePrefix="seg";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		basePath=masterFilePath=System	.getProperty("user.dir");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		clearSlices(basePath+"/filestore/storage");
	}

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
	
	private static void clearSlices(String path){
		java.io.File root=new java.io.File(path);
		for(java.io.File f:root.listFiles()){
			if(f.isDirectory())
				continue;
			
			f.delete();
		}
	}
	
	@Before
	public void setUp() throws Exception {
		deleteFiles();
		
		master = new sg.edu.nyp.sit.svds.master.Main(basePath+"/resource/IDAProp.properties",
				basePath+"/resource/MasterConfig_unitTest.properties");
		MasterProperties.set("master.directory", masterFilePath);
		MasterProperties.set("master.file.port", masterFilePort);
		MasterProperties.set("master.namespace.port", masterNSPort);
		//do not turn on 2 way ssl
		MasterProperties.set("master.namespace.ssl.clientauth", "off");
		MasterProperties.set("master.maintainence.ssl.clientauth", "off");
		master.startupMain();
		
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
		
		segPath=basePath+"/filestore/storage/" ;
	}

	@After
	public void tearDown() throws Exception {
		//delete the segments if they exist
		for(int i=0; i<segSize; i++){
			File f=new File(segPath+ segNamePrefix +i);
			if(f.exists())
				f.delete();
			f=new File(segPath+ segNamePrefix +i+".chk");
			if(f.exists())
				f.delete();
		}
		
		//sleep for 5 seconds so as to wait for all request to be completed
		//at either the master or slice server side
		Thread.sleep(1000*5);
		
		if(fsvr!=null)fsvr.shutdown();
		if(master!=null) master.shutdown();
		
		deleteFiles();
	}

	@Test
	public void segmentRecoveryTestAuthentication() throws Exception{
		fsvr=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_verify_unitTest.properties");
		SliceStoreProperties.set("master.address", masterNSHost);
		SliceStoreProperties.set("slicestore.namespace", namespace);
		fsvr.startup(fsvrId, fsPort, fsPort+1, "localhost", segPath, 0);

		//checks that there is key in the properties
		if(!SliceStoreProperties.exist(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+fsvrId))
			fail("Slice store key is not generated.");
		
		segmentRecoveryTest(true);
	}
	
	@Test
	public void segmentRecoveryTestNoAuthentication() throws Exception{
		fsvr=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_noverify_unitTest.properties");
		SliceStoreProperties.set("master.address", masterNSHost);
		SliceStoreProperties.set("slicestore.namespace", namespace);
		fsvr.startup(fsvrId, fsPort, fsPort+1, "localhost", segPath, 0);
		
		//check that there is no key
		if(SliceStoreProperties.exist(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+fsvrId))
			fail("Slice store key still exist.");
		
		segmentRecoveryTest(false);
	}
	
	@Test
	public void segmentNonStreamingRecoveryTest() throws Exception{
		fsvr=new sg.edu.nyp.sit.svds.filestore.Main(basePath+"/resource/SliceStoreConfig_noverify_unitTest.properties");
		SliceStoreProperties.set("master.address", masterNSHost);
		SliceStoreProperties.set("slicestore.namespace", namespace);
		fsvr.startup(fsvrId, fsPort, fsPort+1, "localhost", segPath, 0);
		
		changeFSMode(FileIOMode.NON_STREAM);
		
		segmentRecoveryTest(false);
	}
	
	private void segmentRecoveryTest(boolean reqVerification) throws Exception{
		createSampleFile(reqVerification);
		
		createFileRec(reqVerification);
		
		Thread.sleep(1000*5);
		
		updFileRec(reqVerification);
		
		//get file info from server see if it return the slice info as well as setting the
		//recovery flag to false. then check to see if the slice is really consolidated
		int cnt=0;
		boolean passed=true;
		do{
			if(!passed)
				Thread.sleep(1000 * 10);
			
			FileInfo fi=getFileRec();
			passed=true;
			
			if(fi!=null){
				for(FileSliceInfo fsi: fi.getSlices()){
					if(fsi.isSliceRecovery()){
						System.out.println("Slice still in recovery.");
						passed=false;
						continue;
					}
				}
			}
			
			cnt++;
		}while(!passed && cnt<retryLimit);
		
		if(!passed)
			fail("Fail to recovery slice.");
		
		File f=new File(segPath+segNamePrefix+"0");
		if(!f.exists())
			fail("Slice does not exist.");
		
		BufferedReader in
		   = new BufferedReader(new FileReader(f));
		String data=in.readLine();
		in.close();
		
		System.out.println("Recovered data: " + data);
		System.out.println("Verified data: " + sampleData);
		if(!data.equals(sampleData))
			fail("Incorrect slice recovery.");
	}
	
	private static void changeFSMode(FileIOMode mode) throws Exception{
		//only change the file store support mode
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = masterNSConnector+"://"+masterNSHost+"/namespace/register?" 
				+ RestletMasterQueryPropName.Namespace.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"=" + URLEncoder.encode(fsvrId, "UTF-8")
				+ "&"+RestletMasterQueryPropName.Namespace.SVR_HOST.value()+"=localhost:" + fsPort 
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
				
				SliceStoreProperties.set(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+fsvrId, key);
			}else
				SliceStoreProperties.remove(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+fsvrId);
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	private void createSampleFile(boolean reqVerification) throws Exception{
		if(sampleData.length()<segSize)
			throw new Exception("Sample data is not long enough to distribute among segments.");
		
		//create the slices and segments
		int noChars=sampleData.length()/segSize;

		//first seg is the base slice
		File f=null, fChk=null;
		SliceDigest md=null;
		String data;
		FileOutputStream out;
		for(int i=0; i<segSize; i++){
			f=new File(segPath+ segNamePrefix +i);
			if(f.exists())
				f.delete();
			
			if(reqVerification){
				fChk=new File(segPath+ segNamePrefix +i+".chk");
				if(fChk.exists())
					fChk.delete();
				md=new SliceDigest(new SliceDigestInfo(blkSize, key));
			}
			
			f.createNewFile();
			if(i*noChars<sampleData.length()){
				out=new FileOutputStream(f);
				if(i*noChars+noChars<sampleData.length()){
					if(i==segSize-1){
						data=sampleData.substring(i*noChars);
					}else{
						data=sampleData.substring(i*noChars, i*noChars+noChars);
					}
				}else
					data=sampleData.substring(i*noChars);
				
				out.write(data.getBytes());
				fsegmentsSize[i]=data.getBytes().length;
				if(reqVerification) md.update(data.getBytes());
				out.close();
			}
			
			if(reqVerification){
				md.finalizeDigest();
				if(i==0)
					sliceChecksum=Resources.convertToHex(md.getSliceChecksum());
				out=new FileOutputStream(fChk);
				for(byte[] h: md.getBlkHashes())
					out.write(h);
				out.close();
			}
			
			fsegments[i]=f;
		}
	}
	
	private FileInfo getFileRec() throws Exception{
		FileInfo fi=null;
		
		HttpURLConnection fsConn=null;
		
		String strUrl = masterFileConnector+"://" + masterFileHost + "/file/get?" 
		+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
		+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(fileName, "UTF-8");
		
		URL fsUrl = new URL(strUrl);
		fsConn=(HttpURLConnection)fsUrl.openConnection();
		
		fsConn.setDoInput(true);
		
		int resp=fsConn.getResponseCode();
		if(resp!=HttpURLConnection.HTTP_OK)
			return null;
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
		Properties data=new Properties();
		data.load(reader);
		reader.close();
		
		fsConn.disconnect();
		
		if(data.size()<4)
			return null;
		
		if(FileInfo.Type.valueOf(Integer.parseInt(data.get(FileInfo.PropName.TYPE.value()).toString()))!=FileInfo.Type.FILE)
			return null;
		
		//only get necessary data
		fi=new FileInfo(namespace, fileName, FileInfo.Type.FILE);
		int sliceCnt=Integer.parseInt(data.get(FileSliceInfo.PropName.COUNT.value()).toString());
		FileSliceInfo fsi;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		for(int cnt=0; cnt<sliceCnt; cnt++){
			if(!data.containsKey(FileSliceInfo.PropName.NAME.value()+cnt) || 
				!data.containsKey(FileSliceInfo.PropName.SVR.value()+cnt) ||
				!data.containsKey(FileSliceInfo.PropName.SEQ.value()+cnt) ||
				!data.containsKey(FileSliceInfo.PropName.LEN.value()+cnt) ||
				!data.containsKey(FileSliceInfo.PropName.SEG_RECOVERY.value()+cnt))
				return null;
			
			fsi=new FileSliceInfo(data.get(FileSliceInfo.PropName.NAME.value()+cnt).toString(),
					data.get(FileSliceInfo.PropName.SVR.value()+cnt).toString(),
					Long.parseLong(data.get(FileSliceInfo.PropName.LEN.value()+cnt).toString()), null,
					Integer.parseInt(data.get(FileSliceInfo.PropName.SEQ.value()+cnt).toString()));
			
			fsi.setSliceRecovery(data.get(FileSliceInfo.PropName.SEG_RECOVERY.value()+cnt).toString().equals("0") ? false : true);
			
			slices.add(fsi);
		}
		fi.setSlices(slices);
		
		return fi;
	}
	
	private void updFileRec(boolean reqVerification) throws Exception{
		HttpURLConnection fsConn=null;
		String updData="123";
		SliceDigest md=null;
		
		FileSliceInfo seg=new FileSliceInfo(segNamePrefix+segSize, fsvrId, sampleData.length(), updData.getBytes().length,
				new Date().getTime());
		File f=new File(segPath+ segNamePrefix +segSize);
		if(f.exists())
			f.delete();
		f.createNewFile();
		FileOutputStream o=new FileOutputStream(f);
		o.write(updData.getBytes());
		o.flush();
		o.close();
		
		if(reqVerification){
			md=new SliceDigest(new SliceDigestInfo(blkSize, key));
			md.update(updData.getBytes());
			md.finalizeDigest();
			f=new File(segPath+ segNamePrefix +segSize+".chk");
			if(f.exists())
				f.delete();
			f.createNewFile();
			o=new FileOutputStream(f);
			for(byte[] h: md.getBlkHashes()){
				o.write(h);
			}
			o.flush();
			o.close();
		}
		
		sampleData=sampleData+updData;
		segSize++;
		
		String strUrl = masterFileConnector+"://" + masterFileHost + "/file/update?" 
		+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
		+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(fileName, "UTF-8")
		+ "&"+RestletMasterQueryPropName.File.SIZE.value()+"=100"
		+ "&"+RestletMasterQueryPropName.File.USER.value()+"=victoria"
		+ "&"+RestletMasterQueryPropName.File.SEQ.value()+"=1"
		+(!reqVerification?"&"+RestletMasterQueryPropName.File.FILE_BLKSIZE.value()+"=0"
				:"&"+RestletMasterQueryPropName.File.FILE_BLKSIZE.value()+"="+blkSize 
				+ "&"+RestletMasterQueryPropName.File.FILE_KEYHASH.value()+"="+key);

		URL fsUrl = new URL(strUrl);
		fsConn=(HttpURLConnection)fsUrl.openConnection();

		fsConn.setDoOutput(true);

		OutputStream out=fsConn.getOutputStream();

		out.write((FileSliceInfo.PropName.COUNT.value()+"=1\n").getBytes());
		out.write((FileSliceInfo.PropName.NAME.value()+"0="+segNamePrefix+"0\n").getBytes());
		out.write((FileSliceInfo.PropName.SEQ.value()+"0=0\n").getBytes());
		//place in dummy length cos value will not be used due to slice in recovery 
		out.write((FileSliceInfo.PropName.LEN.value()+"0=0\n").getBytes());
		out.write((FileSliceInfo.PropName.SVR.value()+"0="+Resources.encodeKeyValue(fsvrId)+"\n").getBytes());
		if(reqVerification)out.write((FileSliceInfo.PropName.CHECKSUM.value()+"0="+Resources.encodeKeyValue(sliceChecksum)+"\n").getBytes());
		out.write((FileSliceInfo.PropName.SEG_CNT.value()+"0=1\n").getBytes());

		String segPrefix=FileSliceInfo.PropName.SEG.value()+"0_";
		out.write((segPrefix+FileSliceInfo.PropName.NAME.value()+"0="+seg.getSliceName()+"\n").getBytes());
		out.write((segPrefix+FileSliceInfo.PropName.SVR.value()+"0="+Resources.encodeKeyValue(seg.getServerId())+"\n").getBytes());
		out.write((segPrefix+FileSliceInfo.PropName.SEG_LEN.value()+"0="+updData.getBytes().length+"\n").getBytes());
		out.write((segPrefix+FileSliceInfo.PropName.SEG_OFFSET.value()+"0="+seg.getOffset()+"\n").getBytes());
		
		out.flush();
		out.close();

		System.out.println("Update status: " + fsConn.getResponseCode()+ " "+fsConn.getResponseMessage());
		
		fsConn.disconnect();
	}
	
	private void createFileRec(boolean reqVerification) throws Exception{
		HttpURLConnection fsConn=null;
		
		String strUrl = masterFileConnector+"://" + masterFileHost + "/file/add?" 
		+ RestletMasterQueryPropName.File.NAMESPACE.value()+"=" + URLEncoder.encode(namespace, "UTF-8")
		+ "&"+RestletMasterQueryPropName.File.SEQ.value()+"=0"
		+ "&"+RestletMasterQueryPropName.File.OWNER.value()+"=" + URLEncoder.encode("victoria", "UTF-8")
		+ "&"+RestletMasterQueryPropName.File.TYPE.value()+"=" + FileInfo.Type.FILE.value()
		+ "&"+RestletMasterQueryPropName.File.PATH.value()+"=" + URLEncoder.encode(fileName, "UTF-8") 
		+ "&"+RestletMasterQueryPropName.File.SIZE.value()+"=100"
		+ "&"+RestletMasterQueryPropName.File.IDA_VERSION.value()+"=1"
		+(!reqVerification?"":"&"+RestletMasterQueryPropName.File.FILE_BLKSIZE.value()+"="+blkSize 
				+ "&"+RestletMasterQueryPropName.File.FILE_KEYHASH.value()+"="+key);
		
		URL fsUrl = new URL(strUrl);
		fsConn=(HttpURLConnection)fsUrl.openConnection();
		
		fsConn.setDoOutput(true);

		OutputStream out=fsConn.getOutputStream();

		out.write((FileSliceInfo.PropName.COUNT.value()+"=1\n").getBytes());
		out.write((FileSliceInfo.PropName.NAME.value()+"0="+segNamePrefix+"0\n").getBytes());
		out.write((FileSliceInfo.PropName.SEQ.value()+"0=0\n").getBytes());
		out.write((FileSliceInfo.PropName.LEN.value()+"0="+fsegmentsSize[0]+"\n").getBytes());
		out.write((FileSliceInfo.PropName.SVR.value()+"0="+Resources.encodeKeyValue(fsvrId)+"\n").getBytes());
		if(reqVerification)out.write((FileSliceInfo.PropName.CHECKSUM.value()+"0="+Resources.encodeKeyValue(sliceChecksum)+"\n").getBytes());
		out.write((FileSliceInfo.PropName.SEG_CNT.value()+"0="+(segSize-1)+"\n").getBytes());
		
		String segPrefix=FileSliceInfo.PropName.SEG.value()+"0_";
		for(int s=0; s<(segSize-1); s++){
			out.write((segPrefix+FileSliceInfo.PropName.NAME.value()+s+"=seg"+(s+1)+"\n").getBytes());
			out.write((segPrefix+FileSliceInfo.PropName.SVR.value()+s+"="+Resources.encodeKeyValue(fsvrId)+"\n").getBytes());
			out.write((segPrefix+FileSliceInfo.PropName.SEG_OFFSET.value()+s+"="+((s+1)*(sampleData.length()/segSize))+"\n").getBytes());
			out.write((segPrefix+FileSliceInfo.PropName.SEG_LEN.value()+s+"="+fsegmentsSize[s+1]+"\n").getBytes());
		}

		out.flush();
		out.close();

		System.out.println("Add status: " + fsConn.getResponseCode()+ " "+fsConn.getResponseMessage());
		
		fsConn.disconnect();
	}
}
