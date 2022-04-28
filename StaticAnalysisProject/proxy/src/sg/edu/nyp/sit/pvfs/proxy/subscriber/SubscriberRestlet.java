package sg.edu.nyp.sit.pvfs.proxy.subscriber;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;

import sg.edu.nyp.sit.pvfs.proxy.RequestAuthentication;
import sg.edu.nyp.sit.svds.metadata.RestletProxyQueryPropName;
import sg.edu.nyp.sit.svds.metadata.User;

public class SubscriberRestlet extends Restlet {
	private static final Log LOG = LogFactory.getLog(SubscriberRestlet.class);
	
	private SubscriberAction sa;
	private RequestAuthentication ra;
	
	public SubscriberRestlet(RequestAuthentication ra, SubscriberAction sa){
		super();
		this.sa=sa;
		this.ra=ra;
	}
	
	private enum Functs{
		INITREMOTE(true, false),
		AUTHREMOTE(true, true),
		ENDREMOTE(true, true),
		DELREMOTE(true, true),
		CHKREMOTE(true, true),
		REGISTER(false, false),
		LIST(false, false),
		GET(true, false),
		UPDATE(false, false),
		DELETE(true, false);
		
		private boolean getIDFrmQuery=false;
		private boolean checkSignature=false;
		Functs(boolean getIDFrmQuery, boolean checkSignature){
			this.getIDFrmQuery=getIDFrmQuery;
			this.checkSignature=checkSignature;
		}	
		public boolean getIDFrmQuery(){
			return getIDFrmQuery;
		}
		public boolean checkSignature(){
			return checkSignature;
		}
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		Functs funct=Functs.valueOf(request.getResourceRef().getLastSegment().toUpperCase());
		if(funct==null){
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Not a valid request");
			return;
		}
		
		String usr=null;
		if(funct.getIDFrmQuery()){
			usr=request.getResourceRef().getQueryAsForm()
				.getFirstValue(RestletProxyQueryPropName.Subscriber.ID.value(), true, null);
			if(usr!=null) usr=usr.toUpperCase();
		}
		if(funct.checkSignature()){
			int verify;
			if(ra!=null && (verify=ra.checkSignature(usr, request.getResourceRef().getQuery()))<=0){
				if(verify==0)
					response.setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, "Unauthenticated request.");
				else if(verify==-1 && funct==Functs.CHKREMOTE)
					response.setEntity("-1", MediaType.TEXT_PLAIN);
				else
					response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Remote session does not exist.");
				
				return;
			}
		}
		
		switch(funct){
			case INITREMOTE:
				//http://localhost/subscriber/initRemote?id=xxx&key=yyy
				initRemoteSession(request, response, usr);
				break;
			case AUTHREMOTE:
				//http://localhost/subscriber/authRemote?id=xxx&dt=aaaa&s=yyy
				authRemoteSession(request, response, usr);
				break;
			case ENDREMOTE:
				//http://localhost/subscriber/endRemote?id=xxx&dt=aaaa&s=yyy
				endRemoteSession(request, response, usr);
				break;
			case DELREMOTE:
				//http://localhost/subscriber/delRemote?id=xxx&dt=aaaa&s=yyy
				delRemoteSession(request, response, usr);
				break;
			case CHKREMOTE:
				//http://localhost/subscriber/chkRemote?id=xxx&dt=aaaa&s=yyy
				chkRemoteSession(request, response, usr);
				break;
			case REGISTER:
				registerSubscriber(request, response);
				break;
			case LIST:
				listSubscribers(request, response);
				break;
			case GET:
				getSubscriber(request, response, usr);
				break;
			case UPDATE:
				updateSubscriber(request, response);
				break;
			case DELETE:
				deleteSubscriber(request, response,usr);
				break;
		}
	}
	
	private void deleteSubscriber(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			sa.removeSubscriber(id);
			
			response.setEntity("<h2>Subscriber deleted successfully.</h2>", MediaType.TEXT_HTML);
		}catch(RejectedExecutionException ex){
			response.setEntity("<h2>Subscriber has existing remote session. Unable to delete.</h2>", MediaType.TEXT_HTML);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setEntity("<h2>Error retrieving subscriber.</h2>", MediaType.TEXT_HTML);
		}
	}
	
	private void updateSubscriber(org.restlet.Request request, org.restlet.Response response){
		Form form=new Form(request.getEntity());
		
		String id, key, name, email;
		try{
			id=form.getFirstValue(RestletProxyQueryPropName.Subscriber.ID.value(), true, null);
			key=form.getFirstValue(RestletProxyQueryPropName.Subscriber.KEY.value(), true, null);
			name=form.getFirstValue(RestletProxyQueryPropName.Subscriber.NAME.value(), true, null);
			email=form.getFirstValue(RestletProxyQueryPropName.Subscriber.EMAIL.value(), true, null);
		}catch(Exception ex){
			response.setEntity("<h2>Missing information.</h2>", MediaType.TEXT_HTML);
			return;
		}
		
		if(id==null || name==null || email==null || id.length()==0 || name.length()==0 || email.length()==0){
			response.setEntity("<h2>Missing information.</h2>", MediaType.TEXT_HTML);
			return;
		}
		
		try{
			sa.updateSubscriber(id, (key!=null && key.length()==0?null:key), name, email);
			
			response.setEntity("<h2>Subscriber ("+id+") successfully updated.</h2>", MediaType.TEXT_HTML);
		}catch(IllegalArgumentException ex){
			response.setEntity("<h2>Subsriber does not exist.</h2>", MediaType.TEXT_HTML);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setEntity("<h2>Error updating subscriber.</h2>", MediaType.TEXT_HTML);
		}
	}
	
	private void getSubscriber(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			User usr=sa.getSubscriber(id);
			
			if(usr==null){
				response.setEntity("<h2>Subscriber information not found.</h2>", MediaType.TEXT_HTML);
				return;
			}
			
			StringBuilder resp=new StringBuilder();
			resp.append("<h2>Update Subscriber</h2>");
			resp.append("<form action='/subscriber/update' method='post'>");
			resp.append("<table border='0'' cellpadding='2'>");
			resp.append("<tr>"
					+ "<td><b>ID:</b></td>"
					+ "<td>"+usr.getId()+"<input type='hidden' name='"+RestletProxyQueryPropName.Subscriber.ID.value()+"' value='"+usr.getId()+"'/></td>"
					+ "</tr>"
					+ "<tr>"
					+ "<td><b>* Password:</b></td>"
					+ "<td><input type='password' name='"+RestletProxyQueryPropName.Subscriber.KEY.value()+"' maxlength='40'/></td>"
					+ "</tr>"
					+"<tr>"
					+ "<td><b>Name:</b></td>"
					+ "<td><input type='text' name='"+RestletProxyQueryPropName.Subscriber.NAME.value()+"' maxlength='200' value='"+usr.getName()+"'/></td>"
					+"</tr>"
					+"<tr>"
					+"<td><b>Email:</b></td>"
					+"<td><input type='text' name='"+RestletProxyQueryPropName.Subscriber.EMAIL.value()+"' maxlength='200' value='"+usr.getEmail()+"'/></td>"
					+"</tr>"
					+"<tr>"
					+"<td colspan='2' align='center'>"
					+"<br/><input type='submit' value='Update'/>&nbsp;&nbsp;<input type='reset' value='Clear'/>"
					+"</td>"
					+"</tr>");
			resp.append("</table></form><br/><br/>* Leave blank if do not wish to update");
			
			response.setEntity(resp.toString(), MediaType.TEXT_HTML);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setEntity("<h2>Error retrieving subscriber.</h2>", MediaType.TEXT_HTML);
		}
	}
	
	private void listSubscribers(org.restlet.Request request, org.restlet.Response response){
		try{
			List<User> usrs=sa.getAllSubscribers();
			
			if(usrs==null || usrs.size()==0){
				response.setEntity("<h2>No subscriber found.</h2>", MediaType.TEXT_HTML);
				return;
			}
			
			StringBuilder resp=new StringBuilder();
			resp.append("<h2>List Subscriber(s)</h2>");
			resp.append("<table border='1' cellpadding='2'>");
			resp.append("<tr>"
					+ "<th>ID</th>"
					+ "<th>Name</th>"
					+ "<th>Email</th>"
					+ "<th>&nbsp;</th><th>&nbsp;</th>"
					+ "</tr>");
			for(User u: usrs){
				resp.append("<tr>"
						+"<td>"+u.getId()+"</td>"
						+"<td>"+u.getName()+"</td>"
						+"<td>"+u.getEmail()+"</td>"
						+"<td><a href='/subscriber/get?"+RestletProxyQueryPropName.Subscriber.ID.value()+"="+u.getId()+"'>Update</a></td>"
						+"<td><a href='/subscriber/delete?"+RestletProxyQueryPropName.Subscriber.ID.value()+"="+u.getId()+"'>Delete</a></td>"
						+"</tr>");
			}
			resp.append("</table>");
			
			response.setEntity(resp.toString(), MediaType.TEXT_HTML);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setEntity("<h2>Error listing subscriber(s).</h2>", MediaType.TEXT_HTML);
		}
	}
	
	private void registerSubscriber(org.restlet.Request request, org.restlet.Response response){
		Form form=new Form(request.getEntity());
		
		String id, key, name, email;
		try{
			id=form.getFirstValue(RestletProxyQueryPropName.Subscriber.ID.value(), true, null);
			key=form.getFirstValue(RestletProxyQueryPropName.Subscriber.KEY.value(), true, null);
			name=form.getFirstValue(RestletProxyQueryPropName.Subscriber.NAME.value(), true, null);
			email=form.getFirstValue(RestletProxyQueryPropName.Subscriber.EMAIL.value(), true, null);
		}catch(Exception ex){
			response.setEntity("<h2>Missing information.</h2>", MediaType.TEXT_HTML);
			return;
		}
		
		if(id==null || name==null || email==null || key==null || key.length()==0 || id.length()==0 
				|| name.length()==0 || email.length()==0){
			response.setEntity("<h2>Missing information.</h2>", MediaType.TEXT_HTML);
			return;
		}
		
		try{
			sa.registerSubscriber(id, key, name, email);
			
			response.setEntity("<h2>Subscriber ("+id+") successfully registered.</h2>", MediaType.TEXT_HTML);
		}catch(IllegalArgumentException ex){
			response.setEntity("<h2>Subscriber ("+id+") already exist.</h2>", MediaType.TEXT_HTML);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setEntity("<h2>Error adding subscriber.</h2>", MediaType.TEXT_HTML);
		}
	}
	
	private void initRemoteSession(org.restlet.Request request, org.restlet.Response response, String id){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String key;
		try{
			key=urlQuery.getFirstValue(RestletProxyQueryPropName.Subscriber.KEY.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(key==null || key.length()==0 ){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		try{
			if(!sa.isValidSubscriber(id, key)){
				response.setStatus(Status.CLIENT_ERROR_EXPECTATION_FAILED, "Invalid id/key.");
				return;
			}
			
			//remove any previous token stored in the memory
			if(ra!=null) ra.removeToken(id);
			response.setEntity(sa.createRemoteSession(id), MediaType.TEXT_PLAIN);
		}catch(IllegalStateException ex){
			response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void authRemoteSession(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			if(sa.chkRemoteSession(id)>0)
				response.setEntity("OK", MediaType.TEXT_PLAIN);
			else
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "No remote session available.");
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void endRemoteSession(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			sa.endRemoteSession(id);
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void delRemoteSession(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			sa.delRemoteSession(id);
			if(ra!=null) ra.removeToken(id);
			response.setEntity("OK", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
	
	private void chkRemoteSession(org.restlet.Request request, org.restlet.Response response, String id){
		try{
			response.setEntity(sa.chkRemoteSession(id)+"", MediaType.TEXT_PLAIN);
		}catch(Exception ex){
			LOG.error(ex);
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, ex.getMessage());
		}
	}
}
