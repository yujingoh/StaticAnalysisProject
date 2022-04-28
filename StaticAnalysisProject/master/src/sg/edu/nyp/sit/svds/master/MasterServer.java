package sg.edu.nyp.sit.svds.master;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;

import sg.edu.nyp.sit.svds.master.file.FileAction;
import sg.edu.nyp.sit.svds.master.filestore.FileSliceStoreFactory;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.master.persistence.MasterImage;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;

public class MasterServer {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(MasterServer.class);
	
	private MasterImage mi=null;
	private TransLogger transLog=null;
	private NamespaceLogger namespaceLog=null;

	private Map<String, SortedMap<String, FileInfo>> files=null;
	private Map<String, NamespaceInfo> namespaces=null;
	private Map<String, FileSliceServerInfo> sliceServers=null;
	
	public MasterServer(MasterImage mi, TransLogger transLog, NamespaceLogger namespaceLog){
		this.mi=mi;
		this.transLog=transLog;
		this.namespaceLog=namespaceLog;
	}
	
	public synchronized void startUp(Map<String, NamespaceInfo> lst_namespaces, 
			Map<String, FileSliceServerInfo> lst_sliceServers,
			Map<String, SortedMap<String, FileInfo>> lst_files) throws Exception{
		this.files=lst_files;
		this.namespaces=lst_namespaces;
		this.sliceServers=lst_sliceServers;

		//read the file entries
		Thread tFile=new Thread(new ReadFiles());
		tFile.start();

		//read the previous available file servers and check them if they are alive
		Thread tNamespace=new Thread(new ReadNamespaces());
		tNamespace.start();

		while(tFile.isAlive() || tNamespace.isAlive()){
			//since both have finish, wait for both to finish first
			Thread.yield();
		}
		
		//update the files in memory with transaction from the edit log
		//IMPT to read the namespace before the file entries because new namespaces have to be
		//created so as to have available "memory" for the new/update file(s) to use/free up the "memory"
		NamespaceAction na=new NamespaceAction(namespaces, sliceServers, null);
		namespaceLog.readNamespaceLog(na);
		transLog.readFileDirLog(new FileAction(na, files, null, null, null));
		
		//check if the slice servers are alive
		checkFileSliceServers(sliceServers.values());

		//overwrite the image file with the updated transactions
		mi.updateImage(namespaces, sliceServers, files);
		
		//delete the edit log after applying the transactions 
		transLog.deleteLog();
		namespaceLog.deleteLog();
	}
	
	private class ReadFiles implements Runnable{
		public void run(){
			try{
				mi.readDirFiles(files);
			}catch(Exception ex){
				LOG.error(ex);
			}
		}
	}
	
	private void checkFileSliceServers(Collection<FileSliceServerInfo> lst){
		//for each server, checks that it's alive
		for(FileSliceServerInfo fss: lst){
			LOG.info("Checking slice server " + fss.getServerId());
			try{
				if(!FileSliceStoreFactory.getInstance(fss.getType()).isAlive(fss))
					fss.setStatus(FileSliceServerInfo.Status.DEAD);
			}catch(Exception ex){
				ex.printStackTrace();
				LOG.warn((new Date()).toString()+"\tWARNING: Cannot connect to file slice server - " 
						+ fss.getServerHost());
				fss.setStatus(FileSliceServerInfo.Status.DEAD);
			}
		}
	}
	
	private class ReadNamespaces implements Runnable{
		public void run(){
			try{
				mi.readNamespaces(namespaces, sliceServers);
				
				//List<String> remSvr=new ArrayList<String>();
				//List<String> remNS=new ArrayList<String>();

				/*
				//for each server, checks that it's alive
				for(NamespaceInfo n: namespaces.values()){
					for(FileSliceServerInfo fss: n.getServers().values()){
						if(fss.getType()==FileSliceServerInfo.TYPE_RESTLET){
							try{
								HttpURLConnection fsConn=(HttpURLConnection)(new URL("http://" + fss.getServerHost() + "/status?info="+Resources.STATUS_ALIVE)).openConnection();
								
								if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
									remSvr.add(fss.getServerId());
									//n.removeFileSliceServer(fss.getServerId());
								}
								
								fsConn.disconnect();
								fsConn=null;
							}catch(Exception ex){
								LOG.warn((new Date()).toString()+"\tWARNING: Cannot connect to file slice server - " 
										+ fss.getServerHost() + ". Registration will be removed.");
								remSvr.add(fss.getServerId());
								//n.removeFileSliceServer(fss.getServerId());
							}
						}else{
							//codes to check if other type of server is alive
						}
					}
					if(remSvr.size()>0){
						for(String svr: remSvr)
							n.removeFileSliceServer(svr);
						
						remSvr.clear();
					}
					
					//if the namespace does not contains any more file slice servers, just remove from
					//list so that it does not get written to the image file later
					if(n.getServers().size()==0){
						namespaces.get(n.getNamespace()).setServers(null);
						remNS.add(n.getNamespace());
						//namespaces.remove(n.getNamespace());
					}
				}
				
				if(remNS.size()>0){
					for(String ns: remNS)
						namespaces.remove(ns);
					
					remNS.clear();
				}

				remSvr=null;
				remNS=null;
				*/
			}catch(Exception ex){
				LOG.error(ex);
			}
		}
	}
}


