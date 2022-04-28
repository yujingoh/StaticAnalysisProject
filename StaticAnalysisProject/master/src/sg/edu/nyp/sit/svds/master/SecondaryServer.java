package sg.edu.nyp.sit.svds.master;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.file.FileAction;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.master.persistence.MasterImage;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;

public class SecondaryServer {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(SecondaryServer.class);

	private final String host;
	private final String connector;
	
	public SecondaryServer(String host, String connector){
		this.host=host;
		this.connector=connector;
	}
	
	public synchronized boolean checkpointMaster(){
		//even with multiple instances executing, this will be executed in order
		String baseUrl=connector+"://" + host + "/master";
		URL fsUrl;
		HttpURLConnection fsConn=null;
		
		try{
			//ask master to roll trans log
			fsUrl = new URL(baseUrl + "/checkpoint");
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				throw new Exception("Error occurred when checkpointing: " + fsConn.getResponseMessage());
		}catch(Exception ex){
			LOG.error(ex);

			return false;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
			
			fsConn=null;
		}	
			
		try{	
			//get the image file
			File fImg=getFile(baseUrl+"/get_img", "svds", "img");

			//get log file
			File fTransLog=getFile(baseUrl+"/get_trans_log", "fTrans", "log");
			File fNsLog=getFile(baseUrl+"/get_ns_log", "nsTrans", "log");

			//update the image with the log file
			MasterImage mi=new MasterImage(fImg);
			TransLogger transLog=new TransLogger(fTransLog);
			NamespaceLogger nsLog=new NamespaceLogger(fNsLog);
			Map<String, NamespaceInfo> lst_namespaces=Collections.synchronizedMap(new HashMap<String, NamespaceInfo>());
			Map<String, SortedMap<String, FileInfo>> lst_files=Collections.synchronizedMap(new HashMap<String, SortedMap<String, FileInfo>>());
			Map<String, FileSliceServerInfo> lst_sliceServers=Collections.synchronizedMap(new HashMap<String, FileSliceServerInfo>());
			
			//read the file entries
			mi.readNamespaces(lst_namespaces, lst_sliceServers);
			mi.readDirFiles(lst_files);
			//update the files in memory with transaction from the edit log
			//IMPT to read the namespace before the file entries because new namespaces have to be
			//created so as to have available "memory" for the new/update file(s) to use/free up the "memory"
			NamespaceAction na=new NamespaceAction(lst_namespaces, lst_sliceServers, null);
			FileAction fa=new FileAction(na, lst_files, null, null, null);
			nsLog.readNamespaceLog(na);
			transLog.readFileDirLog(fa);
			//overwrite the image file with the updated transactions
			mi.updateImage(lst_namespaces, lst_sliceServers, lst_files);
			//delete the edit log after applying the transactions 
			transLog.deleteLog();
			nsLog.deleteLog();

			//send the updated image file back
			fsUrl = new URL(baseUrl + "/upd_img");
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoOutput(true);

			OutputStream out=fsConn.getOutputStream();
			FileInputStream in = new FileInputStream(fImg);
			byte data[]=new byte[Resources.DEF_BUFFER_SIZE];
			int len=0;
			while((len=in.read(data))!=-1){
				out.write(data,0,len);
				out.flush();
			}
			data=null;
			out.close();
			in.close();

			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				throw new Exception("Error occurred when checkpointing: " + fsConn.getResponseMessage());

			//delete the image file
			mi.deleteImg();

			return true;
		}catch(Exception ex){
			LOG.error(ex);

			return false;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	private File getFile(String url, String fileName, String fileExt) throws Exception{
		HttpURLConnection fsConn=null;
		
		try{
			URL fsUrl=new URL(url);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			fsConn.setDoInput(true);

			File fImg=File.createTempFile(fileName, "."+fileExt);
			FileOutputStream out = new FileOutputStream(fImg);
			InputStream r=fsConn.getInputStream();

			byte data[]=new byte[Resources.DEF_BUFFER_SIZE];
			int len=0;
			while((len=r.read(data))!=-1){
				out.write(data,0,len);
				out.flush();
			}
			data=null;
			out.close();
			r.close();
			
			return fImg;
		}catch(Exception ex){
			throw ex;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	public long getLogSize(){
		HttpURLConnection fsConn=null;
		
		try{
			URL fsUrl = new URL(connector+"://" + host + "/master/check_size");
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);
			
			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				return -1;
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
			long size=Long.parseLong(reader.readLine());
			reader.close();

			return size;
		}catch(Exception ex){
			LOG.error(ex);
			return -1;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
}
