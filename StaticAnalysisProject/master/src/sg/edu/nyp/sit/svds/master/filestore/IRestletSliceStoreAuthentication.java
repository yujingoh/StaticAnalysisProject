package sg.edu.nyp.sit.svds.master.filestore;

import java.util.Date;

import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;

public interface IRestletSliceStoreAuthentication {
	public static final long serialVersionUID = 2L;
	
	public static final String EXP_QUERYPROP="exp";
	
	public Object generateAuthentication(FileSliceServerInfo fs, Object o) throws Exception;
	public Object generateSharedAuthentication(FileSliceServerInfo fs, Date expiry, String name) throws Exception;
}
