package sg.edu.nyp.sit.svds.master.filestore;

import java.util.List;

import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public interface IFileSliceStore {
	public static final long serialVersionUID = 2L;
	
	public boolean isAlive(FileSliceServerInfo server) throws Exception;
	public String[] getAccessURL(FileSliceServerInfo server, String name) throws Exception;
	
	public List<byte[]> retrieveHashes(FileSliceServerInfo server, String name) throws Exception;
	
	public void delete(FileSliceServerInfo server, String name) throws Exception;
	
	public void store(FileSliceServerInfo server, String name, 
			long offset, byte[] in, int inOffset, int inLen, SliceDigestInfo md) throws Exception;
	public void store(FileSliceServerInfo server, String name, 
			byte[] in, int inOffset, int inLen, SliceDigestInfo md) throws Exception;
	
	public byte[] retrieve(FileSliceServerInfo server, String name, int blkSize) throws Exception;
}
