package sg.edu.nyp.sit.pvfs.proxy.persistence;


public class DBParameter {
	public Object value;
	public int type;
	
	//the type uses the java.sql.Types
	public DBParameter(Object value, int type){
		this.value=value;
		this.type=type;
	}
}
