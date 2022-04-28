package sg.edu.nyp.sit.svds.master.namespace;

import java.security.SecureRandom;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.exception.SVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger.LogEntry;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;

public class NamespaceAction {
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(NamespaceAction.class);
	
	private Map<String, NamespaceInfo> lst_namespaces=null;
	private Map<String, FileSliceServerInfo> lst_sliceServers=null;
	
	private NamespaceLogger namespaceLog=null;
	
	public NamespaceAction(Map<String, NamespaceInfo> lst_namespaces, 
			Map<String, FileSliceServerInfo> lst_sliceServers, NamespaceLogger namespaceLog){
		this.lst_namespaces=lst_namespaces;
		this.lst_sliceServers=lst_sliceServers;
		this.namespaceLog=namespaceLog;
	}
	
	//for viewer restlet ONLY
	public Map<String, NamespaceInfo> getAllNamespaces(){
		return Collections.unmodifiableMap(this.lst_namespaces);
	}
	
	public String getFileSliceServerKey(String serverId) throws NoSuchFieldException{
		serverId=serverId.toUpperCase();
		
		if(!lst_sliceServers.containsKey(serverId))
			throw new NoSuchFieldException("No file slice server found.");
		
		return lst_sliceServers.get(serverId).getKey();
	}
	
	public void checkFileSliceServer(String serverId) throws NoSuchFieldException{
		serverId=serverId.toUpperCase();
		
		if(!lst_sliceServers.containsKey(serverId))
			throw new NoSuchFieldException("File slice server is not registered.");
		
		FileSliceServerInfo fss=lst_sliceServers.get(serverId);
		
		fss.setLastChecked(new Date());
		fss.setStatus(FileSliceServerInfo.Status.ACTIVE);
		
		/*
		boolean isFound=false;
		
		for(NamespaceInfo namespace: lst_namespaces.values()){
			if(!namespace.getServers().containsKey(serverId))
				continue;
			
			FileSliceServerInfo fss=namespace.getServers().get(serverId);
			
			fss.setLastChecked(new Date());
			fss.setStatus(FileSliceServerInfo.STATUS_ACTIVE);

			namespace.addFileSliceServer(fss);
			isFound=true;
			
			fss=null;
		}
		
		if(!isFound)
			throw new NoSuchFieldException("File slice server is not registered.");
		*/
	}
	
	public List<FileSliceServerInfo> getFileSliceServers(String namespace, FileIOMode pref) throws NoSuchFieldException{
		NamespaceInfo info=lst_namespaces.get(namespace);
		if(info==null){
			throw new NoSuchFieldException("Namespace could not be found.");
		}
		
		List<FileSliceServerInfo> avaServers=new ArrayList<FileSliceServerInfo>();
		FileSliceServerInfo fsi;
		for(String i: info.getServers()){
			fsi=lst_sliceServers.get(i);
			if(fsi.getStatus()==FileSliceServerInfo.Status.ACTIVE
					&& (pref==FileIOMode.BOTH || fsi.getMode()==FileIOMode.STREAM 
							|| fsi.getMode()==pref)){
				LOG.debug("FS " + fsi.getServerId() + " selected.");
				avaServers.add(lst_sliceServers.get(i));
			}
		}
		
		return avaServers;
	}
	
	public boolean isNamespaceExist(String namespace){
		return lst_namespaces.containsKey(namespace);
	}
	
	public void updateFileSliceServer(String serverId, String ipAddr, FileSliceServerInfo.Type type, 
			FileIOMode mode, Properties props, boolean toLog) throws Exception{
		if(!lst_sliceServers.containsKey(serverId))
			throw new NoSuchFieldException("Slice server not found.");
		
		FileSliceServerInfo fss=lst_sliceServers.get(serverId);
		
		fss.setServerHost(ipAddr);
		fss.setMode(mode);
		fss.setType(type);
		
		//for azure and s3
		if(props.containsKey(FileSliceServerInfo.PropName.KEYID.value())){
			fss.setKeyId(props.get(FileSliceServerInfo.PropName.KEYID.value()).toString());
			props.remove(FileSliceServerInfo.PropName.KEYID.value());
		}else fss.setKeyId(null);
		
		if(props.containsKey(FileSliceServerInfo.PropName.KEY.value())){
			fss.setKey(props.get(FileSliceServerInfo.PropName.KEY.value()).toString());
			props.remove(FileSliceServerInfo.PropName.KEY.value());
		}else fss.setKey(null);
		
		fss.setAllProperties(props);
		
		if(toLog && namespaceLog!=null)
			namespaceLog.sliceServerLog(LogEntry.SLICE_SVR_UPD, fss);
	}
	
	public String registerNamespaces(String[] namespaces, long[] avaMems, String serverId, String ipAddr, 
			FileSliceServerInfo.Type type, boolean toLog, boolean reqVerification, FileIOMode mode, Properties props) throws Exception{

		long avaMem=0;
		String namespace=null;
		serverId=serverId.toUpperCase();
		
		FileSliceServerInfo fss=null;
		if(lst_sliceServers.containsKey(serverId)){
			fss=lst_sliceServers.get(serverId);
			checkFileSliceServer(serverId);
		}else
			fss=new FileSliceServerInfo(serverId);
		
		fss.setType(type);
		fss.setMode(mode);
		fss.setServerHost(ipAddr);
		
		//for azure and s3
		if(props!=null && props.containsKey(FileSliceServerInfo.PropName.KEYID.value())){
			fss.setKeyId(props.get(FileSliceServerInfo.PropName.KEYID.value()).toString());
			props.remove(FileSliceServerInfo.PropName.KEYID.value());
		}else fss.setKeyId(null);
		
		if(props!=null && props.containsKey(FileSliceServerInfo.PropName.KEY.value())){
			fss.setKey(props.get(FileSliceServerInfo.PropName.KEY.value()).toString());
			props.remove(FileSliceServerInfo.PropName.KEY.value());
		}else fss.setKey(null);
		
		if(props!=null) {
			fss.setAllProperties(props);
		}
		
		if(toLog && namespaceLog!=null)
			namespaceLog.namespaceLog(NamespaceLogger.LogEntry.NAMESP_REG, 
				fss, namespaces, avaMems);

		LOG.debug(serverId + " req key: " +reqVerification);
		
		if(type==FileSliceServerInfo.Type.RESTLET){
			if(reqVerification){
				//SecureRandom seRand=null;
				//try{
				//	seRand=SecureRandom.getInstance(MasterProperties.getString(MasterProperties.PropName.SLICESTORE_RANDOM_ALGO));
				//}catch(Exception ex){
				//	LOG.error(ex);
				//	throw new SVDSException(ex);
				//}
				
				//byte randValue[]=new byte[MasterProperties.getInt(MasterProperties.PropName.SLICESTORE_RANDOM_SIZE)];
				//seRand.nextBytes(randValue);
				//fss.setKey(Resources.convertToHex(randValue));
				try{
					fss.setKey(Resources.generateRandomValue(MasterProperties.getString(MasterProperties.PropName.SLICESTORE_RANDOM_ALGO), 
							MasterProperties.getInt(MasterProperties.PropName.SLICESTORE_RANDOM_SIZE)));
				}catch(Exception ex){
					LOG.error(ex);
					throw new SVDSException(ex);
				}
			}else
				fss.setKey(null);
		}
		
		LOG.debug("FS key @ reg namespace: " + fss.getKey());
		
		for(int i=0; i<namespaces.length; i++){
			namespace=namespaces[i];
			if(namespace==null||namespace.length()==0)
				continue;
			
			NamespaceInfo info=lst_namespaces.get(namespace);
			
			//if namespace does not exist, create new one
			if(info==null){
				avaMem=(avaMems!=null ? avaMems[i] : MasterProperties.getLong(MasterProperties.PropName.DEF_NAMESPACE_MEM));
				info=new NamespaceInfo(namespace,avaMem,0);
			}

			info.addFileSliceServer(serverId);
			fss.addRegisteredNamespace(namespace);
			
			lst_namespaces.put(namespace, info);
		}
		
		lst_sliceServers.put(serverId, fss);
		
		return fss.getKey();
	}
	
	public void removeNamespaces(String[] namespaces, String serverId, boolean toLog) throws Exception{
		FileSliceServerInfo fss=lst_sliceServers.get(serverId);
		
		if(toLog && namespaceLog!=null)
			namespaceLog.namespaceLog(NamespaceLogger.LogEntry.NAMESP_REM, 
					fss, namespaces, null);
		
		serverId=serverId.toUpperCase();
		
		for(String namespace: namespaces){
			if(namespace==null||namespace.length()==0)
				continue;
			
			if(!lst_namespaces.containsKey(namespace) || !(lst_namespaces.get(namespace)).isFileSliceServerExist(serverId))
				continue;
			
			lst_namespaces.get(namespace).removeFileSliceServer(serverId);
			fss.removeRegisteredNamespace(namespace);
		}
		
		/*
		//don't remove cos may have file slices that resides on it
		if(fss.getRegisteredNamespaces().size()<=0){
			lst_sliceServers.remove(serverId);
		}
		*/
	}
	
	public FileSliceServerInfo resolveFileSliceServer(String serverId){
		return lst_sliceServers.get(serverId.toUpperCase());
	}
	
	public void useNamespaceMemory(String namespace, long bytes){
		NamespaceInfo info=lst_namespaces.get(namespace);
		if(info==null)
			return;
		
		info.useMemory(bytes);
	}
	
	public void freeNamespaceMemory(String namespace, long bytes){
		NamespaceInfo info=lst_namespaces.get(namespace);
		if(info==null)
			return;
		
		info.freeMemory(bytes);
	}
	
	public NamespaceInfo getNamespaceMemory(String namespace){
		return lst_namespaces.get(namespace);
	}
}
