package sg.edu.nyp.sit.svds.master.file;

import java.io.*;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.SliceDigest;
import sg.edu.nyp.sit.svds.master.filestore.FileSliceStoreFactory;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class FileSliceSegmentRecovery {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(FileSliceSegmentRecovery.class);
			
	private NamespaceAction na=null;
	private TransLogger transLog=null;
	
	private static final long recoveryTimeLimit=60*60*1000; //1 hr
	private static final int recoveryCntLimit=10;
	private static final long recoveryTimeInterval=5*1000; //5 seconds
	
	private static final int recoveryThreadPoolSize=10;
	private final ExecutorService pool;
	
	public FileSliceSegmentRecovery(NamespaceAction na,	TransLogger transLog){
		if(na==null)
			throw new NullPointerException("Namespace list is required.");
			
		this.na=na;
		this.transLog=transLog;
		
		//starts a new pool to perform recovery
		pool = Executors.newFixedThreadPool(recoveryThreadPoolSize);
	}
	
	public void addSlice(FileSliceInfo fsi, FileInfo fi) throws RejectedExecutionException{
		if(fsi==null || fi==null)
			throw new RejectedExecutionException("Values cannot be empty.");
		
		pool.execute(new Recovery(new FileSliceSegment(fsi, fi)));
	}
	
	//for invoke by recovery thread, used when server of main slice does not support streaming
	//then entire main slice has to be downloaded first; in the event of failure, recovery attempt
	//again and instead of retrieving entire main slice which waste bandwidth, pass in the previous
	//downloaded main slice
	private void addSlice(FileSliceInfo fsi, FileInfo fi, java.io.File tmpFile){
		pool.execute(new Recovery(new FileSliceSegment(fsi, fi, tmpFile)));
	}

	public void stop(){
		//pool.shutdown();
		pool.shutdownNow();
	}
	
	private class FileSliceSegment{
		FileSliceInfo fsi=null;
		FileInfo fi=null;
		long recoveryStartTime=0;
		int recoveryCnt=0;
		java.io.File tmpFile=null;
		
		public FileSliceSegment(FileSliceInfo fsi, FileInfo fi){
			this.fsi=fsi;
			this.fi=fi;
			recoveryStartTime=(new Date()).getTime();
		}
		
		public FileSliceSegment(FileSliceInfo fsi, FileInfo fi, java.io.File tmpFile){
			this.fsi=fsi;
			this.fi=fi;
			this.tmpFile=tmpFile;
			recoveryStartTime=(new Date()).getTime();
		}
	}
	
	private class Recovery implements Runnable {
		private FileSliceSegment seg=null;
		private SliceDigestInfo mdInfo=null;
		
		public Recovery(FileSliceSegment seg){
			this.seg=seg;
			if(this.seg.fi.verifyChecksum()){
				mdInfo=new SliceDigestInfo(this.seg.fi.getBlkSize(), this.seg.fi.getKeyHash());
			}
		}
		
		private boolean verifyChecksum(byte[] in, int dataLen) throws Exception{
			if(in.length<Resources.HASH_BIN_LEN)
				return false;
			
			String checksum=Resources.convertToHex(in, 0, Resources.HASH_BIN_LEN);
			
			SliceDigest md=new SliceDigest(new SliceDigestInfo(this.seg.fi.getBlkSize(), this.seg.fi.getKeyHash()));
			md.update(in, Resources.HASH_BIN_LEN, dataLen);
			md.finalizeDigest();
			
			return checksum.equals(Resources.convertToHex(md.getSliceChecksum()));
		}
		
		private boolean nonStreamingRecovery(FileSliceServerInfo main_ss) throws Exception{
			IFileSliceStore main_fss=FileSliceStoreFactory.getInstance(main_ss.getType());

			SliceDigest msd=null;
			String getChecksum=null, calChecksum=null;
			
			byte[] tmp=new byte[Resources.DEF_BUFFER_SIZE];
			int len, offset;
			
			LOG.debug("Non streaming recovery req verification: " + seg.fi.verifyChecksum());

			if(seg.tmpFile==null){
				byte[] in=main_fss.retrieve(main_ss, seg.fsi.getSliceName(), seg.fi.getBlkSize());
				//System.out.println("Retrieve main slice size:" + in.length);
				
				if(seg.fi.verifyChecksum()){
					if(in.length<Resources.HASH_BIN_LEN)
						return false;
					
					//1st few bytes is the combined hash returned
					getChecksum=Resources.convertToHex(in, 0, Resources.HASH_BIN_LEN);
					msd=new SliceDigest(mdInfo);
				}

				seg.tmpFile=java.io.File.createTempFile(seg.fsi.getSliceName(), null);
				FileOutputStream outFile=null;
				try{
					offset=(seg.fi.verifyChecksum()?Resources.HASH_BIN_LEN:0);
					len=(int) seg.fsi.getLength();
					
					outFile=new FileOutputStream(seg.tmpFile);
					outFile.write(in, offset, len);
					if(msd!=null) msd.update(in, offset, len);
					
					//System.out.print("main slice data (o="+offset+", l="+len+"): ");
					//Resources.printByteArray(in, offset, len);
					
					outFile.flush();
					outFile.close();
					outFile=null;
				}catch(Exception ex){
					if(outFile!=null) outFile.close();
					seg.tmpFile.delete();
					seg.tmpFile=null;
				
					throw ex;
				}
				
				if(msd!=null) {
					msd.finalizeDigest();
					calChecksum=Resources.convertToHex(msd.getSliceChecksum());
				}
			}else if(seg.fi.verifyChecksum()){
				//cal the blk hashes in the tmp file
				msd=new SliceDigest(mdInfo);
				FileInputStream in=new FileInputStream(seg.tmpFile);
				while((len=in.read(tmp))!=-1)
					msd.update(tmp, 0, len);
				in.close();
				msd.finalizeDigest();
			}
			
			if(getChecksum!=null && !calChecksum.equals(getChecksum)){
				//retrieved file is corrupted
				//System.out.println("File is corrupted");
				seg.tmpFile.delete();
				return false;
			}
			
			RandomAccessFile routFile=new RandomAccessFile(seg.tmpFile, "rwd");
			FileSliceInfo fsi;
			FileSliceServerInfo segss;
			IFileSliceStore seg_fss;
			for(int index=0; index<seg.fsi.getSegments().size(); index++){
				fsi=seg.fsi.getSegments().get(index);

				if(seg.fsi.getSegments().get(index).isRemoved){
					if(seg.fsi.getLength()<fsi.getOffset()+fsi.getLength())
						seg.fsi.setLength(fsi.getOffset()+fsi.getLength());
					
					continue;
				}

				LOG.debug("Recovering seg: " + fsi.getSliceName());

				segss=na.resolveFileSliceServer(fsi.getServerId());
				if(segss==null || segss.getStatus()!=FileSliceServerInfo.Status.ACTIVE){
					return false;
				}
				seg_fss=FileSliceStoreFactory.getInstance(segss.getType());

				//retrieve the segment
				byte[] in=seg_fss.retrieve(segss, fsi.getSliceName(), seg.fi.getBlkSize());
				if(seg.fi.verifyChecksum()){
					if(!verifyChecksum(in, (int)fsi.getLength()))
						throw new StreamCorruptedException("Segment checksum does not match.");
					msd.finalizeDigest();
					msd.setOffset(fsi.getOffset());
					offset=Resources.HASH_BIN_LEN;
				}else offset=0;

				//write segment to the main slice
				routFile.seek(fsi.getOffset());
				routFile.write(in, offset, (int)fsi.getLength());
				//System.out.print("Segment data: ");
				//Resources.printByteArray(in, offset, (int)fsi.getLength());
				if(msd!=null) msd.update(in, offset, (int)fsi.getLength());

				//mark the segment for deletion but don't delete yet, in case
				//server crash then data cannot be retrieved back
				fsi.isRemoved=true;
				
				if(seg.fsi.getLength()<fsi.getOffset()+fsi.getLength())
					seg.fsi.setLength(fsi.getOffset()+fsi.getLength());
				
				LOG.debug("Seg recovery OK: " + fsi.getSliceName());
			}
			routFile.close();

			if(!seg.fsi.hasSegments() || seg.fsi.isAllSegmentsRemoved()){
				//write the main slice back to the slice server
				FileInputStream in=new FileInputStream(seg.tmpFile);
				byte[] data=new byte[(int)seg.tmpFile.length()];
				in.read(data);
				//System.out.println("done recovery data ("+seg.fsi.getLength()+"): ");
				//Resources.printByteArray(data);
				main_fss.store(main_ss, seg.fsi.getSliceName(), data, 0, data.length, mdInfo);
				in.close();
				data=null;
				
				if(msd!=null) seg.fsi.setSliceChecksum(Resources.convertToHex(msd.getSliceChecksum()));

				if(transLog!=null)
					transLog.fileSliceLog(TransLogger.LogEntry.FILE_SLICE_REV, seg.fsi, seg.fi.getNamespace(), 
							seg.fi.getFullPath(), null);
				
				//delete the segments
				seg.tmpFile.delete();
				for(FileSliceInfo i: seg.fsi.getSegments()){
					segss=na.resolveFileSliceServer(i.getServerId());
					try{ FileSliceStoreFactory.getInstance(segss.getType()).delete(segss, i.getSliceName());}
					catch(Exception ex){ LOG.error(ex);}
				}
				seg.fsi.clearSegments();
			}
			
			tmp=null;
			
			return true;
		}
		
		private boolean streamingRecovery(FileSliceServerInfo main_ss) throws Exception{
			IFileSliceStore main_fss=FileSliceStoreFactory.getInstance(main_ss.getType());
			IFileSliceStore seg_fss;
			
			//System.out.println("Streaming recovery req verification: " + seg.fi.verifyChecksum());
			
			String getChecksum, calChecksum;
			SliceDigest msd=null;
			if(seg.fi.verifyChecksum()){
				msd=new SliceDigest(mdInfo);
			}
			
			byte[] in;
			while(seg.fsi.hasSegments() && !seg.fi.isDeleted){
				FileSliceInfo fsi=seg.fsi.getSegments().get(0);
				//System.out.println("Recovering seg: " + fsi.getSliceName() + " to slice " + seg.fsi.getSliceName());
				
				FileSliceServerInfo segss=na.resolveFileSliceServer(fsi.getServerId());
				if(segss==null || segss.getStatus()!=FileSliceServerInfo.Status.ACTIVE){
					//System.out.println("slice server dead");
					return false;
				}
				seg_fss=FileSliceStoreFactory.getInstance(segss.getType());
				
				in=seg_fss.retrieve(segss, fsi.getSliceName(), seg.fi.getBlkSize());			
				
				if(seg.fi.verifyChecksum()){
					//1st few bytes is the combined hash returned
					byte[] checksum=new byte[Resources.HASH_BIN_LEN];
					System.arraycopy(in, 0, checksum, 0, checksum.length);
					getChecksum=Resources.convertToHex(checksum);
				
					msd.reset();
					msd.update(in, Resources.HASH_BIN_LEN, (int)fsi.getLength());
					msd.finalizeDigest();
					calChecksum=Resources.convertToHex(msd.getSliceChecksum());
					
					if(!calChecksum.equals(getChecksum)){
						//System.out.println("seg " +fsi.getSliceName()+ " retrieved checksum: " + getChecksum + "\n"
						//		+ "cal checksum ("+fsi.getLength()+"):" + calChecksum + "\n"
						//		+ "data: " + Resources.concatByteArray(in, Resources.HASH_BIN_LEN, (int) fsi.getLength()));
						//System.out.println("Checksum does not match!");
						return false;
					}
					
					mdInfo.setChecksum(checksum);
				}
				
				//System.out.println("Writting segment " + fsi.getSliceName() + " back to slice in "
				//		+ "offset " + fsi.getOffset() + " length " + fsi.getLength());
				
				main_fss.store(main_ss, seg.fsi.getSliceName(), fsi.getOffset(), in,
						(seg.fi.verifyChecksum()?Resources.HASH_BIN_LEN:0),
						(int)fsi.getLength(), mdInfo);
				
				if(seg.fsi.getLength()<fsi.getOffset()+fsi.getLength())
					seg.fsi.setLength(fsi.getOffset()+fsi.getLength());

				if(transLog!=null)
					transLog.fileSliceLog(TransLogger.LogEntry.FILE_SLICE_SEG_REM, seg.fsi, 
							seg.fi.getNamespace(), seg.fi.getFullPath(), fsi.getSliceName());
					
				//should still continue when problem with deleting seg because recovery will
				//still be considered successful
				try{ seg_fss.delete(segss, fsi.getSliceName()); }catch(Exception ex){LOG.error(ex);}
				
				seg.fsi.removeSegment(fsi);
				//System.out.println("Seg recovery OK: " + fsi.getSliceName());
			}
			
			//if successfully merge back, then write to the trans log
			if(!seg.fsi.hasSegments()){
				LOG.debug("Recovery done.");
				if(seg.fi.verifyChecksum()){
					//get the slice blk hashes from the recovered slice so the slice checksum
					//can be calculated correct in case in the event of file update, the original
					//slice server is down and the blk hashes cannot be retrieved before the update,
					//resulting in a incorrect slice checksum calculated
					//System.out.println("Before: " + seg.fsi.getSliceChecksum());
					seg.fsi.setSliceChecksum(Resources.convertToHex(
							SliceDigest.combineBlkHashes(main_fss.retrieveHashes(main_ss, seg.fsi.getSliceName()))));
					//System.out.println("After: " + seg.fsi.getSliceChecksum());
				}
				
				seg.fsi.clearSegments();
				
				//update the file list to inform that it's done
				/*
				if(lst_fileNamespaces!=null && lst_fileNamespaces.containsKey(seg.namespace)){
					synchronized(lst_fileNamespaces){
						SliceDigest slice_md=new SliceDigest(seg.blkSize, seg.keyHash, getSliceHashes(ss, seg.fsi.getSliceName()));
						
						FileInfo fi=lst_fileNamespaces.get(seg.namespace).get(seg.filePath);
						if(fi!=null && fi.getSlice(seg.fsi.getSliceSeq())!=null){
							FileSliceInfo fsi=fi.getSlice(seg.fsi.getSliceSeq());
							fsi.clearSegments();
							fsi.setSliceChecksum(Resources.convertToHex(slice_md.getSliceChecksum()));
						}
					}
				}
				*/
				
				if(transLog!=null)
					transLog.fileSliceLog(TransLogger.LogEntry.FILE_SLICE_REV, seg.fsi, seg.fi.getNamespace(), 
							seg.fi.getFullPath(), null);
			}
			
			return true;
		}
		
		public void run(){		
			boolean reQueue=false;
			
			if(seg==null || seg.fsi==null){
				return;
			}
			
			FileSliceServerInfo ss=na.resolveFileSliceServer(seg.fsi.getServerId());
			//server for main slice cannot be found, put back at the queue and try later
			if(ss!=null && ss.getStatus()==FileSliceServerInfo.Status.ACTIVE){
				//try to merge the different seg back
				try{
					reQueue=(ss.getMode()==FileIOMode.NON_STREAM ?
							!nonStreamingRecovery(ss) : 
								!streamingRecovery(ss));
				}catch(Exception ex){
					ex.printStackTrace();
					LOG.error(ex);
					//exception can be thrown when the slice/seg does not exist 
					//(cos already been deleted by user during recovery), so to 
					//prevent continuing slice recovery, check if slice exist before re-queuing
					if(!seg.fi.isDeleted) reQueue=true;
				}
			}else reQueue=true;

			//if there is problem merging or writing to trans log, put back into the queue to try again later
			if(reQueue){
				//if both limit are exceed then drop the slice to prevent it from 
				//doing forever
				if((new Date()).getTime()-seg.recoveryStartTime>recoveryTimeLimit &&
						seg.recoveryCnt>recoveryCntLimit){
					try{
						if(transLog!=null)
							transLog.fileSliceLog(TransLogger.LogEntry.FILE_SLICE_REM, seg.fsi, 
									seg.fi.getNamespace(), seg.fi.getFullPath(), null);
					}catch(Exception ex){
						LOG.error(ex);
					}

					//remove the slice from the file object
					if(seg.fi.getSlice(seg.fsi.getSliceSeq())!=null)
						seg.fi.removeSlice(seg.fsi.getSliceSeq());

					return;
				}else{
					try { Thread.sleep(recoveryTimeInterval); } catch (InterruptedException e) {}
				}

				seg.recoveryCnt++;

				addSlice(seg.fsi, seg.fi, seg.tmpFile);
			}
		}
	}
}
