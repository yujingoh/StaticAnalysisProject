package sg.edu.nyp.sit.pvfs.proxy.persistence;

public class DBFactory {
	private static final String INSTANCE_CLS="sg.edu.nyp.sit.pvfs.proxy.persistence.MySQLDBManager";
	
	public static IDBManager INSTANCE=null;
	
	public static IDBManager getInstance(){
		if(INSTANCE!=null)
			return INSTANCE;
		
		try{
			INSTANCE=(IDBManager) Class.forName(INSTANCE_CLS).newInstance();
			
			return INSTANCE;
		}catch(Exception ex){
			ex.printStackTrace();
			throw new ExceptionInInitializerError(ex);
		}
	}
}
