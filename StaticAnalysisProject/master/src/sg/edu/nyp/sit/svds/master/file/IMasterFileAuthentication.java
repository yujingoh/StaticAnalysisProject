package sg.edu.nyp.sit.svds.master.file;

public interface IMasterFileAuthentication {
	public static final long serialVersionUID = 1L;
	
	public boolean authenticateClient(Object o) throws Exception;
}
