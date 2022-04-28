package sg.edu.nyp.sit.svds.filestore;

public interface ISliceStoreClientAuthentication {
	public static final long serialVersionUID = 2L;
	
	public static final String EXP_QUERYPROP="exp";
	
	public boolean authenticateClient(Object o) throws Exception;
}
