package sg.edu.nyp.sit.svds.master.namespace;

import java.util.Date;
import java.util.Map;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.filestore.FileSliceStoreFactory;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;

public class FileSliceServerCheck extends TimerTask {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(FileSliceServerCheck.class);
	private Map<String, FileSliceServerInfo> lst=null;
	
	public FileSliceServerCheck(Map<String, FileSliceServerInfo> lst){
		this.lst=lst;
	}
	
	public void run(){
		LOG.info("File slice servers status check.");
		long currTime=(new Date()).getTime();
		long maxIntervalAllow = MasterProperties.getLong(MasterProperties.PropName.SLICESTORE_CHECK_INTERVAL)*1000;	//15 mins
		
		for(FileSliceServerInfo fss: lst.values()){
			if(fss.getType()==FileSliceServerInfo.Type.RESTLET){
				if((currTime - fss.getLastChecked().getTime()) > maxIntervalAllow){
					LOG.warn((new Date()).toString() + "\tWARNING: File slice server " + fss.getServerHost() + " has not responded within time limit.");
					fss.setStatus(FileSliceServerInfo.Status.DEAD);
				}
			}else{
				boolean alive;
				try{
					alive=FileSliceStoreFactory.getInstance(fss.getType()).isAlive(fss);
				}catch(Exception ex){
					alive=false;
				}
				
				if(!alive) LOG.warn((new Date()).toString() + "\tWARNING: File slice server " + fss.getServerHost() + " is not responding.");
				fss.setStatus((alive?FileSliceServerInfo.Status.ACTIVE:FileSliceServerInfo.Status.DEAD));
			}
		}
	}
}
