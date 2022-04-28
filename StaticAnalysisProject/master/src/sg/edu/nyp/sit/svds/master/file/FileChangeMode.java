package sg.edu.nyp.sit.svds.master.file;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.SliceDigest;
import sg.edu.nyp.sit.svds.exception.CorruptedSVDSException;
import sg.edu.nyp.sit.svds.exception.LockedSVDSException;
import sg.edu.nyp.sit.svds.exception.NotFoundSVDSException;
import sg.edu.nyp.sit.svds.exception.SVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.filestore.FileSliceStoreFactory;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class FileChangeMode {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(FileChangeMode.class);
	
	private NamespaceAction na=null;
	private FileAction fa=null;
	private TransLogger transLog=null;
	
	private static final int threadPoolSize=10;
	private final ExecutorService pool;
	
	public FileChangeMode(NamespaceAction na, FileAction fa, TransLogger transLog){
		
		this.na=na;
		this.fa=fa;
		this.transLog=transLog;
		
		//starts a new pool to perform change mode
		pool = Executors.newFixedThreadPool(threadPoolSize);
	}
	
	public void addFile(FileInfo fi, FileIOMode mode) throws RejectedExecutionException, LockedSVDSException{
		if(fi==null || (mode!=FileIOMode.STREAM && mode!=FileIOMode.NON_STREAM))
			throw new RejectedExecutionException("Empty or invalid values.");
		
		try{
			pool.execute(new ChangeMode(fi, mode));
		}catch(LockedSVDSException lex){
			LOG.error(lex);
			
			throw lex;
		}catch(RejectedExecutionException rex){
			//unlock file account
			try{fa.unlockFileInfo(fi.getNamespace(), fi.getFullPath());}
			catch(Exception ex){LOG.error(ex);}
			
			LOG.error(rex);
			
			throw rex;
		}
	}
	
	public void stop(){
		//pool.shutdown();
		pool.shutdownNow();
	}
	
	private class ChangeMode implements Runnable {
		private FileInfo fi;
		private FileIOMode mode;
		
		public ChangeMode(FileInfo fi, FileIOMode mode) throws LockedSVDSException{
			this.fi=fi;
			this.mode=mode;
			
			//attempt to lock the file using the system account
			try{fa.lockFileInfo(fi.getNamespace(), fi.getFullPath(), "SYSTEM", false);}
			catch(Exception ex){
				throw new LockedSVDSException(ex);
			}
			
			fi.setChgMode(mode);
			
			if(transLog!=null){ 
				try{transLog.fileLog(TransLogger.LogEntry.FILE_CHG_MODE, fi, null);}
				catch(Exception ex){
					LOG.error(ex);
				}
			}
		}
		
		public void run(){	
			boolean markCompleted=true;
			SliceDigest md=null;
			
			try{
				if(MasterProperties.exist("debug.delay")){
					//System.out.println("sleeping...");
					Thread.sleep(MasterProperties.getInt("debug.delay")*1000);
				}
				
				if(fi.verifyChecksum()){
					md=new SliceDigest(new SliceDigestInfo(fi.getBlkSize(), fi.getKeyHash()));
				}
				
				//find out how many slices are incompatible
				FileSliceServerInfo fssi;
				List<FileSliceInfo> oriSlices=new ArrayList<FileSliceInfo>();
				for(FileSliceInfo fsi: fi.getSlices()){
					//System.out.println("slen: " + fsi.getLength());
					fssi=na.resolveFileSliceServer(fsi.getServerId());
					if(fssi.getMode()==mode)
						continue;

					//System.out.println("Slice " + fsi.getSliceName() + " needs to be transfered.");
					oriSlices.add(fsi);
				}

				if(oriSlices.size()>0){
					//get the new slices from slice servers that support the new mode
					List<FileSliceInfo> newSlices=fa.genFileInfo(fi.getNamespace(), oriSlices.size(), 
							mode, new ArrayList<FileSliceServerInfo>());
					
					if(newSlices==null || newSlices.size()<oriSlices.size())
						throw new SVDSException("Not enough slices generated.");
					
					FileSliceInfo fsi, nfsi;
					for(int i=0; i<oriSlices.size(); i++){
						fsi=oriSlices.get(i);
						nfsi=newSlices.get(i);
						
						//if an error occurs with a slice transfer allow to continue
						if(!transferSlice(fsi, nfsi, md))
							continue;
	
						if(transLog!=null)
							transLog.fileSliceLog(TransLogger.LogEntry.FILE_SLICE_MV, fsi, fi.getNamespace(), 
									fi.getFullPath(), null);
					}
				}
			}catch(InterruptedException ex){
				markCompleted=false;
			}catch(Exception ex){
				LOG.error(ex);
			}finally{
				if(!markCompleted)
					return;
				
				//System.out.println("Change mode done!");
				fi.setChgMode(FileIOMode.NONE);
				
				try{ 
					fa.unlockFileInfo(fi.getNamespace(), fi.getFullPath());
					
					if(transLog!=null)
						transLog.fileLog(TransLogger.LogEntry.FILE_CHG_MODE, fi, null);
				}
				catch(Exception uex){LOG.error(uex);}
			}
		}
		
		private boolean transferSlice(FileSliceInfo oldSlice, FileSliceInfo newSlice,
				SliceDigest md){
			FileSliceServerInfo old_fs=null, new_fs=null;
			IFileSliceStore old_fss=null, new_fss=null;
			
			try{
				//System.out.println("Start transfer slice " + oldSlice.getSliceSeq());
				old_fs=na.resolveFileSliceServer(oldSlice.getServerId());
				new_fs=na.resolveFileSliceServer(newSlice.getServerId());
				
				if(old_fs==null || new_fs==null)
					throw new NotFoundSVDSException("Cannot find slice server information.");
				
				old_fss=FileSliceStoreFactory.getInstance(old_fs.getType());
				new_fss=FileSliceStoreFactory.getInstance(new_fs.getType());
				
				byte[] data=null;
				if((data=old_fss.retrieve(old_fs, oldSlice.getSliceName(), (md==null? 0:md.getBlkSize())))==null)
						throw new NotFoundSVDSException("Error getting slice.");
				
				if(oldSlice.getSliceChecksum()!=null){
					md.reset();
					if(data.length<Resources.HASH_BIN_LEN)
						throw new CorruptedSVDSException("Error getting slice.");
				
					String getChecksum=Resources.convertToHex(data, 0, Resources.HASH_BIN_LEN);
					if(!getChecksum.equals(oldSlice.getSliceChecksum())){
						//System.out.println("slice checksum: " + oldSlice.getSliceChecksum() + "\nget checksum: " + getChecksum);
						throw new CorruptedSVDSException("Slice checksum does not match.");
					}
					
					//System.out.println("data len: " + data.length + ", slice len: " + (int)oldSlice.getLength());
					
					md.update(data, Resources.HASH_BIN_LEN, (int)oldSlice.getLength());
					md.finalizeDigest();
					
					md.getSliceDigestInfo().setChecksum(md.getSliceChecksum());
					
					//System.out.println("cal checksum: " + md.getSliceDigestInfo().getChecksum());
					//System.out.println("get checksum: " + getChecksum);
					
					if(!md.getSliceDigestInfo().getChecksum().equals(getChecksum))
						throw new CorruptedSVDSException("Slice checksum does not match.");
				}
				
				new_fss.store(new_fs, oldSlice.getSliceName(), data, (md==null?0:Resources.HASH_BIN_LEN), 
						(int)oldSlice.getLength(), (md==null?null:md.getSliceDigestInfo()));
				
				//change the location of the slice
				oldSlice.setServerId(newSlice.getServerId());
				
				//System.out.println("Transfer slice " + oldSlice.getSliceSeq() + " done.");
			}catch(Exception ex){
				ex.printStackTrace();
				LOG.error(ex);
				return false;
			}
			
			//delete the slice at old slice server, if delete failed, still continue
			try{ old_fss.delete(old_fs, oldSlice.getSliceName()); }catch(Exception ex){LOG.error(ex);}
			
			return true;
		}
	}
}
