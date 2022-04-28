package sg.edu.nyp.sit.pvfs.proxy.persistence;

import java.sql.*;

import sg.edu.nyp.sit.pvfs.proxy.ProxyProperties;

public class MySQLDBManager extends IDBManager {
	private Connection conn=null;
	
	public MySQLDBManager() throws Exception{
		Class.forName("com.mysql.jdbc.Driver");
		
		openConnection();
	}
	
	//destructor method when the object is collected by garbage collector
	protected void finalize() throws Throwable{
		close();
	}
	
	private void openConnection() throws Exception{
		conn = DriverManager.getConnection("jdbc:mysql://" + ProxyProperties.getString(PropName.HOST.value()) + ":"
				+ ProxyProperties.getString(PropName.PORT.value()) + "/" + ProxyProperties.getString(PropName.SCHEMA.value()), 
				ProxyProperties.getString(PropName.USERNAME.value()), 
				ProxyProperties.getString(PropName.PASSWORD.value()));
	}
	
	private void testConnection() throws Exception{
		if(conn.isClosed()) {
			openConnection();
			return;
		}
			
		if(!conn.isValid(0)){
			openConnection();
			return;
		}
	}
	
	@Override
	public Object query(String SQL, DBParameter[] params) throws SQLException {
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
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
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
		PreparedStatement stat=conn.prepareStatement(SQL);
		stat.setObject(1, param.value, param.type);

		return stat.executeQuery();
	}
	@Override
	public Object query(String SQL) throws SQLException {
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
		Statement stat=conn.createStatement();

		return stat.executeQuery(SQL);
	}
	
	@Override
	public Object queryScalar(String SQL, DBParameter[] params)
			throws SQLException {
		ResultSet rs=(ResultSet) query(SQL, params);
		
		try{
			if(!rs.first()) return null;
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
			if(!rs.first()) return null;
			else return rs.getObject(1);
		}finally{
			rs.close();
		}
	}
	@Override
	public Object queryScalar(String SQL) throws SQLException {
		ResultSet rs=(ResultSet) query(SQL);
		
		try{
			if(!rs.first()) return null;
			else return rs.getObject(1);
		}finally{
			rs.close();
		}
	}
	
	@Override
	public int execute(String SQL, DBParameter[] params) throws SQLException {
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
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
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
		PreparedStatement stat=conn.prepareStatement(SQL);
		stat.setObject(1, param.value, param.type);
		
		return stat.executeUpdate();
	}
	@Override
	public int execute(String SQL) throws SQLException {
		try{
			testConnection();
		}catch(Exception ex){
			throw new SQLException(ex);
		}
		
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
