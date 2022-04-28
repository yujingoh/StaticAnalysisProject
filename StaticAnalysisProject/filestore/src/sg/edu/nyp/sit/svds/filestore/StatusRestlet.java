package sg.edu.nyp.sit.svds.filestore;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.*;
import org.restlet.data.*;

import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;
import sg.edu.nyp.sit.svds.metadata.RestletFileSliceServerQueryPropName;

public class StatusRestlet extends Restlet {
	public static final long serialVersionUID = 1L;
	
	@SuppressWarnings("unused")
	private static final Log LOG = LogFactory.getLog(StatusRestlet.class);
			
	private String fileRootPath=null;
	private String serverId=null;
	
	public StatusRestlet(String path, String serverId){
		this.fileRootPath=path;
		this.serverId=serverId;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		//http://localhost:8010/status?info=1,3
		String strStat = urlQuery.getFirstValue(RestletFileSliceServerQueryPropName.Status.INFO.value(), true, null);
		if(strStat==null){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing arguments.");
			return;
		}

		String statArr[]=null;
		if(strStat.indexOf(",")!=-1)
			statArr=strStat.split(",");
		else
			statArr=new String[]{strStat};
		
		try{
			StringBuilder resp=new StringBuilder();
			for(String s: statArr){
				s=s.trim();
				if(s.length()==0)
					continue;
			
				switch(RestletFileSliceServerQueryPropName.StatusReq.valueOf(Integer.parseInt(s))){
					case STATUS_ALIVE:
						break;
					case AVA_STORAGE:
						resp.append(NamespaceInfo.PropName.AVA_MEM.value()+"="+checkAvailableStorage()+"\n");
						break;
					case KEY:
						resp.append(FileSliceServerInfo.PropName.KEY.value()+"="
								+(SliceStoreProperties.exist(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+serverId)?
										SliceStoreProperties.getString(SliceStoreProperties.PropName.SLICESTORE_KEY.value()+"."+serverId)
										: "")
								+"\n");
						break;
					default:
						response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
						return;
				}
			}
			
			response.setEntity(resp.toString(), MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid arguments.");
		}
	}
	
	private long checkAvailableStorage(){
		File f = new File(fileRootPath);
		return f.getUsableSpace();
	}
}
