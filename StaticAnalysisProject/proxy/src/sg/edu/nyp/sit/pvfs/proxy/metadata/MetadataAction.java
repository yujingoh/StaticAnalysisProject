package sg.edu.nyp.sit.pvfs.proxy.metadata;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import sg.edu.nyp.sit.svds.Resources;

public class MetadataAction {
	private String path=null;
	private Map<String, SubscriberMetadataInfo> lst_usr;
	
	public MetadataAction(String path, Map<String, SubscriberMetadataInfo> lst_usr){
		this.path=path;
		this.lst_usr=lst_usr;
	}
	
	public boolean isSubscriberDBExist(String name){
		java.io.File f=new java.io.File(path+"/"+name);
		return f.exists();
	}
	
	public void endSubsriberDB(String name){
		if(lst_usr!=null && lst_usr.containsKey(name)) {
			lst_usr.get(name).endSession();
		}
	}
	
	public void delSubscriberDB(String name){
		if(lst_usr!=null && lst_usr.containsKey(name)) {
			lst_usr.get(name).endSession();
			lst_usr.remove(name);
		}
		
		java.io.File f=new java.io.File(path+"/"+name);
		if(f.exists()) f.delete();
	}
	
	public void saveSubscriberDB(String name, long memory, int currIdaVersion, String driveName, 
			InputStream in) throws Exception{
		java.io.File f=new java.io.File(path+"/"+name);
		if(f.exists()) throw new IOException("File already exist");
		
		f.createNewFile();
		
		FileOutputStream out=new FileOutputStream(f);
		byte[] data=new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		while((len=in.read(data))!=-1){
			out.write(data, 0, len);
		}
		data=null;
		in.close();
		out.close();
		
		if(lst_usr!=null){
			lst_usr.put(name, new SubscriberMetadataInfo(path+"/"+name, memory, currIdaVersion, driveName));
		}
	}
	
	public InputStream getSubscriberDB(String name) throws Exception{
		java.io.File f=new java.io.File(path+"/"+name);
		if(!f.exists()) return null;
		
		return new FileInputStream(f);
	}
}
