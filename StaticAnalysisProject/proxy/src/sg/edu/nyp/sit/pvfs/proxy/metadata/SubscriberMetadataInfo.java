package sg.edu.nyp.sit.pvfs.proxy.metadata;

public class SubscriberMetadataInfo {
	private String DBPath=null;
	private long usedMem=0L;
	private int currIdaVersion=0;
	private String driveName=null;
	private boolean sessionEnded=false;
	
	private FileAction fa=null;
	private SliceStoresAction sa=null;
	
	public enum Tables {
		Files,
		Slices,
		//Segments,
		SliceStores,
		SliceStoresProps,
		IdaInfo
	}
	
	public SubscriberMetadataInfo(String DBPath, long memUsed, int currIdaVersion, String driveName) throws Exception{
		this.DBPath=DBPath;
		this.usedMem=memUsed;
		this.currIdaVersion=currIdaVersion;
		this.driveName=driveName;
		this.fa=new FileAction(this);
		this.sa=new SliceStoresAction(this);
	}
	
	public FileAction getFileAction(){
		return fa;
	}
	
	public SliceStoresAction getSliceStoresAction(){
		return sa;
	}
	
	public String getDBPath(){
		return DBPath;
	}
	
	public void setDriveName(String driveName){
		this.driveName=driveName;
	}
	
	public String getDriveName(){
		return driveName;
	}
	
	public void setCurrIdaVersion(int version){
		currIdaVersion=version;
	}
	
	public int getCurrIdaVersion(){
		return currIdaVersion;
	}
	
	public synchronized void useMemory(long bytes){
		usedMem+=bytes;
	}
	
	public synchronized void freeMemory(long bytes){
		usedMem-=bytes;
	}
	
	public synchronized long getMemoryUsed(){
		return usedMem;
	}
	
	public synchronized void endSession(){
		sessionEnded=true;
		fa.closeDB();
		sa.closeDB();
	}
	
	public synchronized boolean isSessionEnded(){
		return sessionEnded;
	}
}
