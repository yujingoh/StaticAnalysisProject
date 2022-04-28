package sg.edu.nyp.sit.pvfs.proxy.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDBManager extends IDBManager {
	private Connection conn=null;
	
	public SQLiteDBManager(String DBPath) throws Exception{
		Class.forName("org.sqlite.JDBC");

		conn=DriverManager.getConnection("jdbc:sqlite:" + DBPath);
	}
	
	//destructor method when the object is collected by garbage collector
	protected void finalize() throws Throwable{
		close();
	}
	
	@Override
	public Object query(String SQL, DBParameter[] params) throws SQLException {
		PreparedStatement stat=conn.prepareStatement(SQL);
		DBParameter p;
		for(int i=0; i<params.length; i++){
			p=params[i];
			stat.setObject(i+1, p.value, p.type);
		}

		return stat.executeQuery();
	}

	@Override
	public Object query(String SQL, DBParameter param) throws SQLException {
		PreparedStatement stat=conn.prepareStatement(SQL);
		stat.setObject(1, param.value, param.type);

		return stat.executeQuery();
	}

	@Override
	public Object query(String SQL) throws SQLException {
		Statement stat=conn.createStatement();

		return stat.executeQuery(SQL);
	}

	@Override
	public Object queryScalar(String SQL, DBParameter[] params)
			throws SQLException {
		ResultSet rs=(ResultSet) query(SQL, params);
		
		try{
			if(!rs.next()) return null;
			else return rs.getObject(1);
		}finally{
			rs.close();
		}
	}

	@Override
	public Object queryScalar(String SQL, DBParameter param)
			throws SQLException {
		ResultSet rs=(ResultSet) query(SQL, param);
		
		try{
			if(!rs.next()) return null;
			else return rs.getObject(1);
		}finally{
			rs.close();
		}
	}

	@Override
	public Object queryScalar(String SQL) throws SQLException {
		ResultSet rs=(ResultSet) query(SQL);
		
		try{
			if(!rs.next()) return null;
			else return rs.getObject(1);
		}finally{
			rs.close();
		}
	}

	@Override
	public int execute(String SQL, DBParameter[] params) throws SQLException {
		PreparedStatement stat=conn.prepareStatement(SQL);
		DBParameter p;
		for(int i=0; i<params.length; i++){
			p=params[i];
			stat.setObject(i+1, p.value, p.type);
		}
		
		return stat.executeUpdate();
	}

	@Override
	public int execute(String SQL, DBParameter param) throws SQLException {
		PreparedStatement stat=conn.prepareStatement(SQL);
		stat.setObject(1, param.value, param.type);
		
		return stat.executeUpdate();
	}

	@Override
	public int execute(String SQL) throws SQLException {
		Statement stat=conn.createStatement();
		
		return stat.executeUpdate(SQL);
	}

	@Override
	public void close() {
		try{
			if(conn!=null && !conn.isClosed()) {
				conn.close();
				conn=null;
			}
		}catch(Exception ex){
			
		}
	}
	
	@Override
	public void startTransaction() throws SQLException {
		conn.setAutoCommit(false);
	}

	@Override
	public void commitTransaction() throws SQLException {
		conn.commit();
		conn.setAutoCommit(true);
	}

	@Override
	public void rollbackTransaction() throws SQLException {
		conn.rollback();
		conn.setAutoCommit(true);
	}

}
