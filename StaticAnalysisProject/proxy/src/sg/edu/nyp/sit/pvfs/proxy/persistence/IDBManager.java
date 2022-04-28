package sg.edu.nyp.sit.pvfs.proxy.persistence;

import java.sql.SQLException;

public abstract class IDBManager {
	protected enum PropName{
		HOST("db.host"),
		PORT("db.port"),
		USERNAME("db.username"),
		PASSWORD("db.password"),
		SCHEMA("db.schema");
		
		private String name;
		PropName(String name){ this.name=name; }
		public String value(){ return name; }
	}
	
	public abstract Object query(String SQL, DBParameter[] params) throws SQLException;
	public abstract Object query(String SQL, DBParameter param) throws SQLException;
	public abstract Object query(String SQL) throws SQLException;
	
	public abstract Object queryScalar(String SQL, DBParameter[] params) throws SQLException;
	public abstract Object queryScalar(String SQL, DBParameter param) throws SQLException;
	public abstract Object queryScalar(String SQL) throws SQLException;
	
	public abstract int execute(String SQL, DBParameter[] params) throws SQLException;
	public abstract int execute(String SQL, DBParameter param) throws SQLException;
	public abstract int execute(String SQL) throws SQLException;
	
	public abstract void close();
	
	public abstract void startTransaction() throws SQLException;
	public abstract void commitTransaction() throws SQLException;
	public abstract void rollbackTransaction() throws SQLException;
}
