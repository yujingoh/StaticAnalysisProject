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
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import sg.edu.nyp.sit.pvfs.proxy.Main;
import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;

public class FileTest {
	private static String basePath=null;
	private static String DBPath=null;
	private static String testDBPath=null;
	
	private static sg.edu.nyp.sit.pvfs.proxy.Main main=null;
	private static int port=6010;
	
	private static String acctId="VIC", acctKey="123456789";
	
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
		if(refreshDir()!=HttpURLConnection.HTTP_OK)
			fail("Error in refresh directory");
		
		int resp=listDir();
		if(resp!=HttpURLConnection.HTTP_OK && resp!=HttpURLConnection.HTTP_NO_CONTENT)
			fail("Error in list directory");
		
		String dirPath=FileInfo.PATH_SEPARATOR+"abc";
		if(addDir(dirPath)!=HttpURLConnection.HTTP_OK)
			fail("Error in add directory");
		
		String filePath=FileInfo.PATH_SEPARATOR+"test.txt";
		FileSliceInfo[] slices=generateSlices(2);
		if (addFile(filePath, slices)!=HttpURLConnection.HTTP_OK)
			fail("Error in add file");

		if(lockFile(filePath, true)!=HttpURLConnection.HTTP_OK)
			fail("Error in lock file");

		slices=new FileSliceInfo[]{slices[0]};
		if(updateFile(filePath, slices)!=HttpURLConnection.HTTP_OK)
			fail("Error in update file");
		
		if(lockFile(filePath, false)!=HttpURLConnection.HTTP_OK)
			fail("Error in unlock file");
		
		if(accessFile(filePath)!=HttpURLConnection.HTTP_OK)
			fail("Error in access file");
		
		getFile(filePath, slices);

		if(moveFile(filePath, dirPath+filePath)!=HttpURLConnection.HTTP_OK)
			fail("Error in move file");
		filePath=dirPath+filePath;
		
		if(deleteFile(filePath, true)!=HttpURLConnection.HTTP_OK)
			fail("Error in delete file");
		
		if(deleteFile(dirPath, false)!=HttpURLConnection.HTTP_OK)
			fail("Error in delete directory");
		
		//end remote session
		System.out.print("\nREMOTE END...");
		String query=RestletProxyQueryPropName.Subscriber.ID.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.Subscriber.DT.value()+"=1";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		if(sendWebRequestNoResponse("http://localhost:" + port+ "/subscriber/endRemote?" + query)!=HttpURLConnection.HTTP_OK)
			fail("Error in remote session end");
		
		//attempt to send file request, should fail
		if(refreshDir()!=HttpURLConnection.HTTP_FORBIDDEN)
			fail("Should not be able to service any more request after session ended");
	}
	
	private int deleteFile(String path, boolean isFile) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8")
			+"&"+RestletProxyQueryPropName.File.TYPE.value()+"="+(isFile?FileInfo.Type.FILE.value():FileInfo.Type.DIRECTORY.value());
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/delete?" + query);
	}
	
	private int moveFile(String path, String new_path) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+"&"+RestletProxyQueryPropName.File.OLD_PATH.value()+"="+URLEncoder.encode(path, "UTF-8")
			+"&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(new_path, "UTF-8");
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/move?" + query);
	}
	
	private void getFile(String path, FileSliceInfo[] verifySlices) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8");
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/file/get?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);
			
			if(Integer.parseInt(data.get(FileSliceInfo.PropName.COUNT.value()).toString())!=verifySlices.length)
				fail("No of file slice does not match.");
			
			for(int cnt=0; cnt<verifySlices.length; cnt++){
				if(!data.get(FileSliceInfo.PropName.NAME.value()+cnt).toString().equals(verifySlices[cnt].getSliceName()) 
					|| Integer.parseInt(data.get(FileSliceInfo.PropName.SEQ.value()+cnt).toString())!=verifySlices[cnt].getSliceSeq()
					|| Long.parseLong(data.get(FileSliceInfo.PropName.LEN.value()+cnt).toString())!=verifySlices[cnt].getLength()
					|| !data.get(FileSliceInfo.PropName.SVR.value()+cnt).toString().equals(verifySlices[cnt].getServerId()))
					fail("Slice information does not match");
			}
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private int accessFile(String path) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8");
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/access?" + query);
	}
	
	private int updateFile(String path, FileSliceInfo[] slices) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8")
			+"&"+RestletProxyQueryPropName.File.SIZE.value()+"=512"
			+ "&"+RestletProxyQueryPropName.File.FILE_BLKSIZE.value()+"=0"
			+"&"+RestletProxyQueryPropName.File.USER.value()+"=localhost";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/file/update?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoOutput(true);
			
			OutputStream out=conn.getOutputStream();
			out.write((FileSliceInfo.PropName.COUNT.value()+"="+slices.length+"\n").getBytes());
			for(int cnt=0; cnt<slices.length; cnt++){
				out.write((FileSliceInfo.PropName.NAME.value()+cnt+"="+slices[cnt].getSliceName()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SVR.value()+cnt+"="+slices[cnt].getServerId()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SEQ.value()+cnt+"="+slices[cnt].getSliceSeq()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.LEN.value()+cnt+"="+slices[cnt].getLength()+"\n").getBytes());
			}
			out.close();
			
			return conn.getResponseCode();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private int lockFile(String path, boolean lock) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&"+RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8")
			+"&"+RestletProxyQueryPropName.File.USER.value()+"=localhost";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/"+(lock?"lock":"unlock")+"?" + query);
	}
	
	private int addFile(String path, FileSliceInfo[] slices) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&" + RestletProxyQueryPropName.File.TYPE.value()+"="+FileInfo.Type.FILE.value()
			+ "&" + RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8")
			+ "&"+RestletProxyQueryPropName.File.SIZE.value()+"=1024"
			+ "&"+RestletProxyQueryPropName.File.IDA_VERSION.value()+"=1"
			+ "&"+RestletProxyQueryPropName.File.FILE_BLKSIZE.value()+"=0";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/file/add?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoOutput(true);
			
			OutputStream out=conn.getOutputStream();
			out.write((FileSliceInfo.PropName.COUNT.value()+"="+slices.length+"\n").getBytes());
			for(int cnt=0; cnt<slices.length; cnt++){
				out.write((FileSliceInfo.PropName.NAME.value()+cnt+"="+slices[cnt].getSliceName()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SVR.value()+cnt+"="+slices[cnt].getServerId()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.SEQ.value()+cnt+"="+slices[cnt].getSliceSeq()+"\n").getBytes());
				out.write((FileSliceInfo.PropName.LEN.value()+cnt+"="+slices[cnt].getLength()+"\n").getBytes());
			}
			out.close();
			
			return conn.getResponseCode();
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private int addDir(String path) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&" + RestletProxyQueryPropName.File.TYPE.value()+"="+FileInfo.Type.DIRECTORY.value()
			+ "&" + RestletProxyQueryPropName.File.PATH.value()+"="+URLEncoder.encode(path, "UTF-8");
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/add?" + query);
	}
	
	private FileSliceInfo[] generateSlices(int num) throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&" + RestletProxyQueryPropName.File.SIZE.value() + "=" + num
			+ "&" + RestletProxyQueryPropName.File.MODE.value() + "=" + FileIOMode.NON_STREAM.value();
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		HttpURLConnection conn=null;
		try{
			URL u = new URL("http://localhost:" + port+ "/file/generate?" + query);
			conn=(HttpURLConnection)u.openConnection();
			conn.setDoInput(true);
			
			int resp=conn.getResponseCode();
			if(resp!=HttpURLConnection.HTTP_OK)
				fail("Error generate file slice info.");
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			data.list(System.out);

			FileSliceInfo slices[]=new FileSliceInfo[num];
			
			for(int cnt=0; cnt<num; cnt++){
				slices[cnt]=new FileSliceInfo(data.get(FileSliceInfo.PropName.NAME.value()+cnt).toString(),
						data.get(FileSliceInfo.PropName.SVR.value()+cnt).toString());
				slices[cnt].setSliceSeq(cnt);
			}
			
			return slices;
		}finally{
			if(conn!=null) conn.disconnect();
		}
	}
	
	private int listDir() throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&" + RestletProxyQueryPropName.File.PATH.value() + "=" + URLEncoder.encode(FileInfo.PATH_SEPARATOR, "UTF-8");
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/list?" + query);
	}
	
	private int refreshDir() throws Exception{
		String query=RestletProxyQueryPropName.File.SUBSCRIBER.value()+"="+acctId
			+ "&" + RestletProxyQueryPropName.File.PATH.value() + "=" + URLEncoder.encode(FileInfo.PATH_SEPARATOR, "UTF-8")
			+ "&" + RestletProxyQueryPropName.File.LAST_CHECK.value() + "=0";
		MessageDigest md=MessageDigest.getInstance(Resources.HASH_ALGO);
		query+="&"+RestletProxyQueryPropName.SIGNATURE+"="+Resources.convertToHex(md.digest((query+token).getBytes()));
		
		return sendWebRequestNoResponse("http://localhost:" + port+ "/file/refresh?" + query);
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
