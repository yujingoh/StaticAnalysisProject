package sg.edu.nyp.sit.svds.master;

import java.io.File;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.*;
import org.restlet.data.*;
import org.restlet.representation.*;
//import org.restlet.resource.FileRepresentation;

import sg.edu.nyp.sit.svds.master.persistence.MasterImage;
import sg.edu.nyp.sit.svds.master.persistence.NamespaceLogger;
import sg.edu.nyp.sit.svds.master.persistence.TransLogger;

public class MasterRestlet extends Restlet{
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(MasterRestlet.class);
	
	private TransLogger transLog=null;
	private NamespaceLogger nsLog=null;
	private MasterImage mi=null;
	
	public MasterRestlet(MasterImage mi, TransLogger transLog, NamespaceLogger nsLog){
		this.transLog=transLog;
		this.nsLog=nsLog;
		this.mi=mi;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		
		String reqType=request.getResourceRef().getLastSegment();
		
		if(reqType.equalsIgnoreCase("CHECKPOINT")){
			try{
				transLog.rollLog();
				nsLog.rollLog();
				LOG.info("Checkpoint registered at " + (new Date()).toString());
				response.setEntity("OK", MediaType.TEXT_PLAIN);
			}catch(Exception ex){
				response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
			}
		}else if(reqType.equalsIgnoreCase("CHECK_SIZE")){
			response.setEntity((new Long(transLog.getLogSize())).toString(), MediaType.TEXT_PLAIN);
		}else if(reqType.equalsIgnoreCase("GET_IMG")){
			File f=mi.getImg();
			if(f==null || !f.exists())
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			else
				response.setEntity(new FileRepresentation(f, MediaType.ALL));
		}else if(reqType.equalsIgnoreCase("GET_TRANS_LOG")){
			File f=transLog.getPrevLog();
			if(f==null || !f.exists())
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			else
				response.setEntity(new FileRepresentation(f, MediaType.ALL));
		}else if(reqType.equalsIgnoreCase("GET_NS_LOG")){
			File f=nsLog.getPrevLog();
			if(f==null || !f.exists())
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			else
				response.setEntity(new FileRepresentation(f, MediaType.ALL));
		}else if(reqType.equalsIgnoreCase("UPD_IMG")){
			try{
				mi.updateImage(request.getEntity().getStream());
				transLog.deletePrevLog();
				nsLog.deletePrevLog();
				LOG.info("Image file updated at " + (new Date()).toString());
				response.setEntity("OK", MediaType.TEXT_PLAIN);
			}catch(Exception ex){
				response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
			}
		}
	}
}
