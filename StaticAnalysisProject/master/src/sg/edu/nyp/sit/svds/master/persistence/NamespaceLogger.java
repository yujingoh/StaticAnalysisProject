package sg.edu.nyp.sit.svds.master.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.namespace.NamespaceAction;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;

public class NamespaceLogger {
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(NamespaceLogger.class);
	
	public enum LogEntry{
		NAMESP_REG,
		NAMESP_REM,
		SLICE_SVR_UPD,
		SLICE_SVR_PROP,
		END
	}
	
	private enum LogEntryDataIndex{
		NAMESPACE_SVR_REG_ID (1),
		NAMESPACE_SVR_REG_HOST (2),
		NAMESPACE_SVR_REG_TYPE (3),
		NAMESPACE_SVR_REG_MODE (4),
		NAMESPACE_SVR_REG_KEYID (5),
		NAMESPACE_SVR_REG_KEY (6),
		NAMESPACE_SVR_REG_NAMESPACE_NAME (0),
		NAMESPACE_SVR_REG_NAMESPACE_MEM (1),
		NAMESPACE_SVR_REM_ID (1),
		SLICE_SVR_UPD_ID (1),
		SLICE_SVR_UPD_HOST(2),
		SLICE_SVR_UPD_TYPE(3),
		SLICE_SVR_UPD_MODE(4),
		SLICE_SVR_UPD_KEYID(5),
		SLICE_SVR_UPD_KEY(6);
		
		private final int i;
		LogEntryDataIndex(int i){ this.i=i; }
		public int index(){ return i;}
	}
			
	private String logPath=null;
	
	private File currFileLog=null;
	private File prevFileLog=null;
	private FileOutputStream out=null;
	
	public NamespaceLogger(String path){
		logPath=path;
	}
	
	public NamespaceLogger(File log){
		this.currFileLog=log;
	}
	
	//destructor method when the object is collected by garbage collector
	protected void finalize() throws Throwable{
		closeLogForWrite();
	}
	
	private void openLog() throws Exception{
		//throw new Exception("Log path is: " + logPath);
		if(currFileLog==null)
			currFileLog=new File(logPath + "/namespaceTrans.log");
		
		if(!currFileLog.exists())
			currFileLog.createNewFile();
	}
	
	private void openLogForWrite() throws Exception{
		if(out!=null)
			return;
		
		openLog();
		
		out=new FileOutputStream(currFileLog, true);
	}
	
	public File getPrevLog(){
		return prevFileLog;
	}
	
	public synchronized void closeLogForWrite() throws Exception{
		if(out==null)
			return;
		
		out.flush();
		out.close();
		out=null;
	}
	
	public synchronized void rollLog() throws Exception{
		closeLogForWrite();
		
		prevFileLog=null;
		prevFileLog=new File(currFileLog.getAbsolutePath());
		File tmpFile=new File(logPath + "/namespaceTrans_"
				+((new Date()).getTime())+".log");
		prevFileLog.renameTo(tmpFile);
		prevFileLog=tmpFile;
	}
	
	public synchronized void deleteLog() throws Exception{
		closeLogForWrite();
		
		if(currFileLog.exists())
			currFileLog.delete();
	}
	
	public synchronized void deletePrevLog(){
		if(prevFileLog==null)
			return;
		
		prevFileLog.delete();
		prevFileLog=null;
	}
	
	public synchronized void namespaceLog(LogEntry op, FileSliceServerInfo fss, String[] namespaces,
			long[] avaMems) throws Exception{
		openLogForWrite();
		out.write((op + "\t").getBytes());
		
		writeSliceServerLog(op, fss);
		
		String namespace=null;
		for(int i=0; i<namespaces.length; i++){
			namespace=namespaces[i];
			out.write(namespace.getBytes());
			
			if(avaMems!=null && avaMems.length>i)
				out.write(("\t"+avaMems[i]).getBytes());
			
			out.write("\n".getBytes());
		}
		
		out.write((LogEntry.END + "\n").getBytes());
		
		out.flush();
	}
	
	public synchronized void sliceServerLog(LogEntry op, FileSliceServerInfo fss) throws Exception{
		openLogForWrite();
		out.write((op + "\t").getBytes());
		
		writeSliceServerLog(op, fss);
		
		out.flush();
	}
	
	private void writeSliceServerLog(LogEntry op, FileSliceServerInfo fss) throws Exception{
		out.write((fss.getServerId()+"\t"
				+fss.getServerHost()+"\t"
				+fss.getType().value()+ "\t"
				+fss.getMode().value() + "\t"
				+(fss.getKeyId()==null ? " " : fss.getKeyId()) + "\t"
				+(fss.getKey()!=null && fss.getType()!=FileSliceServerInfo.Type.RESTLET ? fss.getKey() : " ") + "\n" 
				).getBytes());
				
		if(op==LogEntry.NAMESP_REG || op==LogEntry.SLICE_SVR_UPD){
			out.write((LogEntry.SLICE_SVR_PROP+"\n").getBytes());
			if(fss.hasProperties()){ 
				//fss.getAllProperties().list(System.out);
				//fss.getAllProperties().store(out, null);
				Resources.storeProperties(fss.getAllProperties(), out);
			}
			out.write((LogEntry.END+"_"+LogEntry.SLICE_SVR_PROP+"\n").getBytes());
		}
	}

	public void readNamespaceLog(NamespaceAction na)
		throws Exception{
		openLog();
		
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(currFileLog)));
		
		String data=null, details[]=null;
		long[] avaMems=null;
		ArrayList<String> namespaces=new ArrayList<String>();
		while((data=in.readLine()) != null){
			if (data.startsWith(LogEntry.NAMESP_REG.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<7)
					throw new IOException("Missing details in namespace register entry.");
				
				Properties props=getSliceServerProperties(in);
				
				if(details[LogEntryDataIndex.NAMESPACE_SVR_REG_KEYID.index()].trim().length()>0){
					props.put(FileSliceServerInfo.PropName.KEYID.value(), details[LogEntryDataIndex.NAMESPACE_SVR_REG_KEYID.index()].trim());
				}
				if(details[LogEntryDataIndex.NAMESPACE_SVR_REG_KEY.index()].trim().length()>0){
					props.put(FileSliceServerInfo.PropName.KEY.value(), details[LogEntryDataIndex.NAMESPACE_SVR_REG_KEY.index()].trim());
				}
				
				avaMems=getNamespaces(in, namespaces);
				
				na.registerNamespaces((String[])namespaces.toArray(new String[namespaces.size()]), 
						avaMems, details[LogEntryDataIndex.NAMESPACE_SVR_REG_ID.index()],
						details[LogEntryDataIndex.NAMESPACE_SVR_REG_HOST.index()], 
						FileSliceServerInfo.Type.valueOf(Integer.parseInt(
								details[LogEntryDataIndex.NAMESPACE_SVR_REG_TYPE.index()])), 
						false, false, FileIOMode.valueOf(Integer.parseInt(
								details[LogEntryDataIndex.NAMESPACE_SVR_REG_MODE.index()])), 
						props);

				avaMems=null;
			}else if (data.startsWith(LogEntry.NAMESP_REM.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<2)
					throw new IOException("Missing details in namespace remove entry.");
				
				getNamespaces(in, namespaces);
				
				na.removeNamespaces((String[])namespaces.toArray(new String[namespaces.size()]), 
						details[LogEntryDataIndex.NAMESPACE_SVR_REM_ID.index()], false);
			}else if (data.startsWith(LogEntry.SLICE_SVR_UPD.toString()+"\t")){
				details=data.split("\t");
				
				if(details.length<5)
					throw new IOException("Missing details in slice server update entry.");
				
				Properties props=getSliceServerProperties(in);
				
				if(details[LogEntryDataIndex.SLICE_SVR_UPD_KEYID.index()].trim().length()>0){
					props.put(FileSliceServerInfo.PropName.KEYID.value(), details[LogEntryDataIndex.SLICE_SVR_UPD_KEYID.index()].trim());
				}
				if(details[LogEntryDataIndex.SLICE_SVR_UPD_KEY.index()].trim().length()>0){
					props.put(FileSliceServerInfo.PropName.KEY.value(), details[LogEntryDataIndex.SLICE_SVR_UPD_KEY.index()].trim());
				}
				
				na.updateFileSliceServer(details[LogEntryDataIndex.SLICE_SVR_UPD_ID.index()], 
						details[LogEntryDataIndex.SLICE_SVR_UPD_HOST.index()], 
						FileSliceServerInfo.Type.valueOf(Integer.parseInt(details[LogEntryDataIndex.SLICE_SVR_UPD_TYPE.index()])), 
						FileIOMode.valueOf(Integer.parseInt(details[LogEntryDataIndex.SLICE_SVR_UPD_MODE.index()])),  
						props, false);
			}
			
			namespaces.clear();
		}
		
		in.close();
		in=null;
		namespaces=null;
	}
	
	private Properties getSliceServerProperties(BufferedReader in){
		String data;
		
		try{
			data=in.readLine();
			if(data==null || !data.equals(LogEntry.SLICE_SVR_PROP.toString()))
				return null;
			
			StringBuilder str=new StringBuilder();
			while(!(data=in.readLine()).equals(LogEntry.END.toString()+"_"+LogEntry.SLICE_SVR_PROP.toString())){
				str.append(data+"\n");
			}
			
			if(str.length()>0){
				Properties props=new Properties();
				props.load(new StringReader(str.toString()));

				return props;
			}else
				return null;
		}catch(Exception ex){
				LOG.error(ex);
				return null;
		}
	}
	
	private long[] getNamespaces(BufferedReader in, ArrayList<String> namespaces) throws Exception{
		ArrayList<String> avaMemsTmp=new ArrayList<String>();
		
		String data;
		String[] tmp=null;
		while((data=in.readLine())!=null){
			if(data.equals(LogEntry.END.toString()))
				break;
			
			if(data.indexOf("\t")!=-1){
				tmp=data.split("\t");
				namespaces.add(tmp[LogEntryDataIndex.NAMESPACE_SVR_REG_NAMESPACE_NAME.index()]);
				avaMemsTmp.add(tmp[LogEntryDataIndex.NAMESPACE_SVR_REG_NAMESPACE_MEM.index()]);
			}else
				namespaces.add(data);
		}
		
		if(avaMemsTmp.size()>0){
			long[] avaMems=new long[avaMemsTmp.size()];
			for(int i=0; i<avaMems.length; i++){
				avaMems[i]=Long.parseLong(avaMemsTmp.get(i));
			}
			return avaMems;
		}else
			return null;
	}
}
