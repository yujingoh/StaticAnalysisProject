package sg.edu.nyp.sit.svds.master.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sg.edu.nyp.sit.svds.master.file.FileAction;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;

public class TransLogger {
	public static final long serialVersionUID = 1L;
	
	public enum LogEntry{
		FILE_ADD,
		FILE_UPD,
		FILE_DEL,
		FILE_MV,
		FILE_LOCK,
		FILE_UNLOCK,
		FILE_ACC,
		FILE_CHG_MODE,
		DIR_ADD,
		DIR_DEL,
		FILE_SLICE_REV,
		FILE_SLICE_REM,
		FILE_SLICE_SEG_REM,
		FILE_SLICE_MV,
		END
	}
	
	private enum LogEntryDataIndex{
		FILE_ADDUPD_NAMESPACE (1),
		FILE_ADDUPD_FULLPATH (2),
		FILE_ADDUPD_IDA_VERSION (3),
		FILE_ADDUPD_SIZE (4),
		FILE_ADDUPD_OWNER (5),
		FILE_ADDUPD_CREATEDT (6),
		FILE_ADDUPD_LASTMODEDT (7),
		FILE_ADDUPD_BLKSIZE (8),
		FILE_ADDUPD_KEYHASH (9),
		FILE_ADDUPD_SLICE_SEQ (0),
		FILE_ADDUPD_SLICE_NAME (1),
		FILE_ADDUPD_SLICE_SVR (2),
		FILE_ADDUPD_SLICE_LEN (3),
		FILE_ADDUPD_SLICE_CHKSUM (4),
		FILE_ADDUPD_SLICE_SEGCNT (5),
		FILE_ADDUPD_SLICE_SEG_NAME (0),
		FILE_ADDUPD_SLICE_SEG_SVR (1),
		FILE_ADDUPD_SLICE_SEG_OFFSET (2),
		FILE_ADDUPD_SLICE_SEG_LEN (3),
		FILE_MV_ORI_NAMESPACE (1),
		FILE_MV_NEW_NAMESPACE (3),
		FILE_MV_ORI_FULLPATH (2),
		FILE_MV_NEW_FULLPATH (4),
		FILE_MV_DT (5),
		FILE_ACC_NAMESPACE (1),
		FILE_ACC_FULLPATH (2),
		FILE_ACC_DT (3),
		FILE_LOCK_NAMESPACE (1),
		FILE_LOCK_FULLPATH (2),
		FILE_LOCK_USR (3),
		FILE_UNLOCK_NAMESPACE (1),
		FILE_UNLOCK_FULLPATH (2),
		FILE_SLICE_REV_NAMESPACE (1),
		FILE_SLICE_REV_FULLPATH (2),
		FILE_SLICE_REV_SEQ (3),
		FILE_SLICE_REV_NAME (4),
		FILE_SLICE_REV_LEN (5),
		FILE_SLICE_REV_CHKSUM (6),
		FILE_SLICE_REM_NAMESPACE (1),
		FILE_SLICE_REM_FULLPATH (2),
		FILE_SLICE_REM_SEQ (3),
		FILE_SLICE_REM_NAME (4),
		FILE_SLICE_MV_NAMESPACE (1),
		FILE_SLICE_MV_FULLPATH (2),
		FILE_SLICE_MV_SEQ (3),
		FILE_SLICE_MV_SVR (4),
		FILE_SLICE_SEG_REM_NAMESPACE (1),
		FILE_SLICE_SEG_REM_FULLPATH (2),
		FILE_SLICE_SEG_REM_SEQ (3),
		FILE_SLICE_SEG_REM_NAME (4),
		FILE_SLICE_SEG_REM_LEN (5),
		FILEDIR_DEL_NAMESPACE (1),
		FILEDIR_DEL_FULLPATH (2),
		FILEDIR_DEL_DT (3),
		DIR_ADD_NAMESPACE (1),
		DIR_ADD_FULLPATH (2),
		DIR_ADD_OWNER (3),
		DIR_ADD_DT (4),
		FILE_CHG_MODE_NAMESPACE (1),
		FILE_CHG_MODE_FULLPATH (2),
		FILE_CHG_MODE_MODE (3);
		
		private final int i;
		LogEntryDataIndex(int i){ this.i=i; }
		public int index(){ return i;}
	}
	
	private String logPath=null;
	
	private File currFileLog=null;
	private File prevFileLog=null;
	
	private FileOutputStream out=null;
	
	public TransLogger(String path){
		this.logPath=path;
	}
	
	public TransLogger(File log){
		this.currFileLog=log;
	}
	
	//destructor method when the object is collected by garbage collector
	protected void finalize() throws Throwable{
		closeLogForWrite();
	}
	
	private void openLog() throws Exception{
		//throw new Exception("Log path is: " + logPath);
		if(currFileLog==null)
			currFileLog=new File(logPath + "/svdsTrans.log");
		
		if(!currFileLog.exists())
			currFileLog.createNewFile();
	}
	
	private void openLogForWrite() throws Exception{
		if(out!=null)
			return;
		
		openLog();
		
		out=new FileOutputStream(currFileLog, true);
	}
	
	public synchronized void deletePrevLog(){
		if(prevFileLog==null)
			return;
		
		prevFileLog.delete();
		prevFileLog=null;
	}
	
	public File getPrevLog(){
		return prevFileLog;
	}
	
	public synchronized void rollLog() throws Exception{
		closeLogForWrite();
		
		prevFileLog=null;
		prevFileLog=new File(currFileLog.getAbsolutePath());
		File tmpFile=new File(logPath + "/svdsTrans_"
				+((new Date()).getTime())+".log");
		prevFileLog.renameTo(tmpFile);
		prevFileLog=tmpFile;
	}
	
	public long getLogSize(){
		if(currFileLog==null)
			return -1;
		
		//System.out.println(currFileLog.getAbsolutePath()+" is " + currFileLog.length());
		return currFileLog.length();
	}
	
	public synchronized void closeLogForWrite() throws Exception{
		if(out==null)
			return;
		
		out.flush();
		out.close();
		out=null;
	}
	
	public synchronized  void deleteLog() throws Exception{
		closeLogForWrite();
		
		if(currFileLog.exists())
			currFileLog.delete();
	}
	
	public synchronized void directoryLog(LogEntry op, FileInfo fi) throws Exception{
		openLogForWrite();
		
		switch(op){
			case DIR_ADD:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t" 
						+ fi.getFullPath() + "\t"
						+ fi.getOwner().getId() + "\t"
						+ fi.getCreationDate().getTime() + "\n"
						).getBytes());
				break;
			case DIR_DEL:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getLastModifiedDate().getTime() + "\n"
						).getBytes());
				break;
			default:
				throw new UnsupportedOperationException("Operation not supported");
		};
		
		out.flush();
	}
	
	public synchronized void fileSliceLog(LogEntry op, FileSliceInfo fsi, String namespace, 
			String fullPath, String segName) throws Exception{
		openLogForWrite();
		
		out.write((op + "\t"
				+ namespace + "\t"
				+ fullPath + "\t"
				+ fsi.getSliceSeq()
			).getBytes());
		
		switch(op){
			case FILE_SLICE_REV:
				out.write(("\t" + fsi.getSliceName() + "\t"
						+ fsi.getLength() + "\t"
						+ (fsi.getSliceChecksum()==null? " ": fsi.getSliceChecksum())
						).getBytes());
				break;
			case FILE_SLICE_SEG_REM:
				out.write(("\t" + segName + "\t"
						+ fsi.getLength()
						).getBytes());
				break;
			case FILE_SLICE_REM:
				out.write(("\t" + fsi.getSliceName()
				).getBytes());
				break;
			case FILE_SLICE_MV:
				out.write(("\t" + fsi.getServerId()
				).getBytes());
				break;
		}
		
		out.write("\n".getBytes());
		
		out.flush();
	}
	
	public synchronized void fileLog(LogEntry op, FileInfo fi, FileInfo fiOld)
		throws Exception{
		//don do any error checking, assuming input parameters are correct so as to improve performance
		openLogForWrite();
		
		switch(op){
			case FILE_ADD: 
			case FILE_UPD:
				out.write((op + "\t").getBytes());
				writeFileLog(fi);
				break;
			case FILE_MV:
				out.write((op + "\t"
						+ fiOld.getNamespace() + "\t"
						+ fiOld.getFullPath() + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getLastAccessedDate().getTime() + "\n"
						).getBytes());
				break;
			case FILE_DEL:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getLastModifiedDate().getTime() + "\n"
						).getBytes());
				break;
			case FILE_LOCK:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getLockBy().getId() + "\n"
						).getBytes());
				break;
			case FILE_CHG_MODE:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getChgMode().value() + "\n"
						).getBytes());
				break;
			case FILE_UNLOCK:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\n"
						).getBytes());
				break;
			case FILE_ACC:
				out.write((op + "\t"
						+ fi.getNamespace() + "\t"
						+ fi.getFullPath() + "\t"
						+ fi.getLastAccessedDate().getTime() + "\n"
						).getBytes());
				break;
			default:
				throw new UnsupportedOperationException("Operation not supported");
		}
		
		out.flush();
	}
	
	private void writeFileLog(FileInfo fi) throws Exception{
		out.write((fi.getNamespace() + "\t" 
				+ fi.getFullPath() + "\t"
				+ fi.getIdaVersion() + "\t"
				+ fi.getFileSize() + "\t"
				+ fi.getOwner().getId() + "\t"
				+ fi.getCreationDate().getTime() + "\t"
				+ fi.getLastModifiedDate().getTime() + "\t"
				+ fi.getBlkSize() + "\t" 
				+ (fi.getKeyHash()==null?" ":fi.getKeyHash()) + "\n"
				).getBytes());
		
		for(FileSliceInfo fsi: fi.getSlices()){
			out.write((fsi.getSliceSeq() + "\t"
					+ fsi.getSliceName() + "\t"
					+ fsi.getServerId() + "\t"
					+ fsi.getLength() + "\t"
					+ (fsi.getSliceChecksum()==null?" ":fsi.getSliceChecksum()) + "\t"
					+ (fsi.hasSegments() ? fsi.getSegments().size() : 0) + "\n"
					).getBytes());
			
			if(fsi.hasSegments()){
				for(FileSliceInfo seg: fsi.getSegments()){
					out.write((seg.getSliceName() + "\t"
							+ seg.getServerId() + "\t"
							+ seg.getOffset() + "\t"
							+ seg.getLength() + "\n"
							).getBytes());
				}
			}
		}
		
		out.write((LogEntry.END + "\n").getBytes());
		
		out.flush();
	}
	
	public void readFileDirLog(FileAction fa)
		throws Exception{
		openLog();
		
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(currFileLog)));
		
		String data=null, details[]=null;
		while((data=in.readLine()) != null){
			//System.out.println("log: " + data);
			if (data.startsWith(LogEntry.FILE_ADD.toString()+"\t")){
				details=data.split("\t");
				if(details.length<10)
					throw new IOException("Missing details in file add entry.");
				
				fa.addFileInfo(details[LogEntryDataIndex.FILE_ADDUPD_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_ADDUPD_FULLPATH.index()], 
						Integer.parseInt(details[LogEntryDataIndex.FILE_ADDUPD_IDA_VERSION.index()]), 
						Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_SIZE.index()]), 
						details[LogEntryDataIndex.FILE_ADDUPD_OWNER.index()], 
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_CREATEDT.index()])), 
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_CREATEDT.index()])),
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_CREATEDT.index()])),
						Integer.parseInt(details[LogEntryDataIndex.FILE_ADDUPD_BLKSIZE.index()]), 
						(details[LogEntryDataIndex.FILE_ADDUPD_KEYHASH.index()].trim().length()==0?
								null:details[LogEntryDataIndex.FILE_ADDUPD_KEYHASH.index()]), 
						getFileSlices(in));
			}else if (data.startsWith(LogEntry.FILE_UPD.toString()+"\t")){
				details=data.split("\t");
				if(details.length<10)
					throw new IOException("Missing details in file update entry.");
				
				fa.updateFileInfo(details[LogEntryDataIndex.FILE_ADDUPD_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_ADDUPD_FULLPATH.index()], 
						Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_SIZE.index()]), 
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_LASTMODEDT.index()])), 
						Integer.parseInt(details[LogEntryDataIndex.FILE_ADDUPD_BLKSIZE.index()]), 
						(details[LogEntryDataIndex.FILE_ADDUPD_KEYHASH.index()].trim().length()==0?
								null:details[LogEntryDataIndex.FILE_ADDUPD_KEYHASH.index()]), 
						getFileSlices(in));
			}else if (data.startsWith(LogEntry.FILE_MV.toString()+"\t")){
				details=data.split("\t");
				if(details.length<6)
					throw new IOException("Missing details in file move entry.");
				
				fa.moveFileInfo(details[LogEntryDataIndex.FILE_MV_ORI_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_MV_NEW_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_MV_ORI_FULLPATH.index()], 
						details[LogEntryDataIndex.FILE_MV_NEW_FULLPATH.index()],
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_MV_DT.index()])));
			}else if (data.startsWith(LogEntry.FILE_LOCK.toString()+"\t")){
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in file lock entry.");
				
				fa.lockFileInfo(details[LogEntryDataIndex.FILE_LOCK_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_LOCK_FULLPATH.index()], 
						details[LogEntryDataIndex.FILE_LOCK_USR.index()], false);
			}else if (data.startsWith(LogEntry.FILE_UNLOCK.toString()+"\t")){
				details=data.split("\t");
				if(details.length<3)
					throw new IOException("Missing details in file unlock entry.");
				
				fa.unlockFileInfo(details[LogEntryDataIndex.FILE_UNLOCK_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_UNLOCK_FULLPATH.index()]);
			}else if (data.startsWith(LogEntry.FILE_ACC.toString()+"\t")){
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in file access entry.");
				
				fa.updateFileInfoLastAccessed(details[LogEntryDataIndex.FILE_ACC_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_ACC_FULLPATH.index()], 
						new Date(Long.parseLong(details[LogEntryDataIndex.FILE_ACC_DT.index()])));
			}else if (data.startsWith(LogEntry.FILE_DEL.toString()+"\t") || 
					data.startsWith(LogEntry.DIR_DEL.toString()+"\t")){
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in file/directory delete entry.");
				
				fa.deleteFileInfo(details[LogEntryDataIndex.FILEDIR_DEL_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILEDIR_DEL_FULLPATH.index()], 
						new Date(Long.parseLong(details[LogEntryDataIndex.FILEDIR_DEL_DT.index()])));
			}else if (data.startsWith(LogEntry.DIR_ADD.toString()+"\t")){
				details=data.split("\t");
				if(details.length<5)
					throw new IOException("Missing details in dirctory add entry.");
				
				fa.addDirectoryInfo(details[LogEntryDataIndex.DIR_ADD_NAMESPACE.index()], 
						details[LogEntryDataIndex.DIR_ADD_FULLPATH.index()], 
						details[LogEntryDataIndex.DIR_ADD_OWNER.index()], 
						new Date(Long.parseLong(details[LogEntryDataIndex.DIR_ADD_DT.index()])));
			}else if (data.startsWith(LogEntry.FILE_SLICE_REV.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<7)
					throw new IOException("Missing details in file slice segment recovery entry.");
				
				fa.fileSliceRecoveryDone(details[LogEntryDataIndex.FILE_SLICE_REV_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_SLICE_REV_FULLPATH.index()], 
						Integer.parseInt(details[LogEntryDataIndex.FILE_SLICE_REV_SEQ.index()]), 
						details[LogEntryDataIndex.FILE_SLICE_REV_NAME.index()], 
						Long.parseLong(details[LogEntryDataIndex.FILE_SLICE_REV_LEN.index()]),
					(details[LogEntryDataIndex.FILE_SLICE_REV_CHKSUM.index()].trim().length()>0?
							details[LogEntryDataIndex.FILE_SLICE_REV_CHKSUM.index()]:null));
			}else if (data.startsWith(LogEntry.FILE_SLICE_REM.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<4)
					throw new IOException("Missing details in file slice removal entry.");
				
				fa.fileSliceRecoveryFail(details[LogEntryDataIndex.FILE_SLICE_REM_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_SLICE_REM_FULLPATH.index()], 
						Integer.parseInt(details[LogEntryDataIndex.FILE_SLICE_REM_SEQ.index()]),
						details[LogEntryDataIndex.FILE_SLICE_REM_NAME.index()]);				
			}else if (data.startsWith(LogEntry.FILE_SLICE_SEG_REM.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<6)
					throw new IOException("Missing details in file slice segment removal entry.");
				
				fa.fileSliceSegRecovery(details[LogEntryDataIndex.FILE_SLICE_SEG_REM_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_SLICE_SEG_REM_FULLPATH.index()], 
						Integer.parseInt(details[LogEntryDataIndex.FILE_SLICE_SEG_REM_SEQ.index()]), 
						details[LogEntryDataIndex.FILE_SLICE_SEG_REM_NAME.index()],
						Long.parseLong(details[LogEntryDataIndex.FILE_SLICE_SEG_REM_LEN.index()]));
			}else if (data.startsWith(LogEntry.FILE_SLICE_MV.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<5)
					throw new IOException("Missing details in file slice move entry.");
				
				fa.fileSliceMove(details[LogEntryDataIndex.FILE_SLICE_MV_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_SLICE_MV_FULLPATH.index()], 
						Integer.parseInt(details[LogEntryDataIndex.FILE_SLICE_MV_SEQ.index()]), 
						details[LogEntryDataIndex.FILE_SLICE_MV_SVR.index()]);
			}else if (data.startsWith(LogEntry.FILE_CHG_MODE.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<4)
					throw new IOException("Missing details in file change mode entry.");
				
				fa.fileChangeMode(details[LogEntryDataIndex.FILE_CHG_MODE_NAMESPACE.index()], 
						details[LogEntryDataIndex.FILE_CHG_MODE_FULLPATH.index()], 
						FileIOMode.valueOf(Integer.parseInt(details[LogEntryDataIndex.FILE_CHG_MODE_MODE.index()])));
			}
		}
		
		in.close();
		in=null;
		fa=null;
	}
	
	private List<FileSliceInfo> getFileSlices(BufferedReader in)throws Exception{
		String data=null, details[]=null;
		int segCnt=0;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		
		long timestamp=new Date().getTime();
		
		while((data=in.readLine()) != null){
			if(data.equals(LogEntry.END.toString()))
				break;
			
			details=data.split("\t");
			if(details.length<6)
				throw new IOException("Missing details in file slice entry.");
			
			FileSliceInfo fsi=new FileSliceInfo(
					details[LogEntryDataIndex.FILE_ADDUPD_SLICE_NAME.index()], 
					details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SVR.index()], 
					Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_LEN.index()]),
					(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_CHKSUM.index()].trim().length()==0?
							null:details[LogEntryDataIndex.FILE_ADDUPD_SLICE_CHKSUM.index()]), 
					Integer.parseInt(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEQ.index()]));
			
			segCnt=Integer.parseInt(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEGCNT.index()]);
			for(int s=0; s<segCnt; s++){
				data=in.readLine();
				if(data==null)
					throw new IOException("Expecting file slice segments.");
				
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in file slice segment entry.");
				
				fsi.addSegment(new FileSliceInfo(
						details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEG_NAME.index()], 
						details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEG_SVR.index()], 
						Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEG_OFFSET.index()]),
						Long.parseLong(details[LogEntryDataIndex.FILE_ADDUPD_SLICE_SEG_LEN.index()]),
						timestamp));
			}
			
			slices.add(fsi);
		}
		
		return slices;
	}
}
