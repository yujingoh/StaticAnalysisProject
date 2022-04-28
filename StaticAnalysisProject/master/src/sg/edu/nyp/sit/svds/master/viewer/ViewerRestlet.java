package sg.edu.nyp.sit.svds.master.viewer;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;

import sg.edu.nyp.sit.svds.master.file.FileAction;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;
import sg.edu.nyp.sit.svds.metadata.RestletMasterQueryPropName;

public class ViewerRestlet extends Restlet {
	public static final long serialVersionUID = 1L;
	
	@SuppressWarnings("unused")
	private static final Log LOG = LogFactory.getLog(ViewerRestlet.class);
	
	private FileAction fa=null;
	
	public ViewerRestlet(FileAction fa){
		super();
		this.fa=fa;
	}
	
	@Override  
    public void handle(org.restlet.Request request, org.restlet.Response response) { 
		super.handle(request, response);
		String reqType=request.getResourceRef().getLastSegment();
		
		if(reqType.equalsIgnoreCase("ALL_FILES")){
			//http://localhost:9000/viewer/all_files
			displayAllFiles(request, response);
		}else if(reqType.equalsIgnoreCase("FILE")){
			//http://localhost:9000/viewer/file?namespace=urn:nyp.edu.sg&filename=/abc/secret.doc
			displayFileDetail(request, response);
		}else if(reqType.equalsIgnoreCase("SVR")){
			//http://localhost:9000/viewer/svr?id=testfs
			displaySliceServer(request, response);
		}else if(reqType.equalsIgnoreCase("ALL_SVRS")){
			//http://localhost:9000/viewer/all_svr
			displayAllSliceServers(request,response);
		}else{
			response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Invalid method.");
		}
	}
	
	private void displayAllSliceServers(org.restlet.Request request, org.restlet.Response response){
		StringBuilder resp=new StringBuilder();
		
		resp.append("<html>" + displayPageHeader() + "<body>"
				+ "<h1 align=\"center\">Secure Virtualized Diffused Storage (SVDS) - File system viewer</h1>"
				+ "<p align=\"center\"><a href=\"all_files\">View all files</a>"
				+ "&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;"	
				+ "View all slice stores</p>"
				+ "<hr/><table border=\"0\" align=\"center\"><tr><td>");
		
		Map<String, NamespaceInfo> lst_ns=fa.getAllNamespaces();
		if(lst_ns==null || lst_ns.isEmpty()){
			resp.append("<i>No namespaces found.</i></td></tr></table></body></html>");
			response.setEntity(resp.toString(), MediaType.TEXT_HTML);
			return;
		}
		
		for(NamespaceInfo ns: lst_ns.values()){
			resp.append("<p>" 
					+ "<b>Namespace:</b>&nbsp;" + ns.getNamespace()
					+ "<br/><b>Slice store(s):</b><br/><ul>");
			try {
				for(String svr: ns.getServers()){
					resp.append("<li><a href=\"svr?"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"="
							+ URLEncoder.encode(svr, "UTF-8")+ "\">"+svr+"</a></li>");
				}
			} catch (Exception ex) { ex.printStackTrace(); }
			
			resp.append("</ul></p>");
		}
		
		resp.append("</td></tr></table></body></html>");
		response.setEntity(resp.toString(), MediaType.TEXT_HTML);
	}
	
	private void displaySliceServer(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String id;
		try{
			id=urlQuery.getFirstValue(RestletMasterQueryPropName.Namespace.SVR_ID.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(id==null || id.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		StringBuilder resp=new StringBuilder();
		
		resp.append("<html>" + displayPageHeader() + "<body>"
				+ "<h1 align=\"center\">Secure Virtualized Diffused Storage (SVDS) - File system viewer</h1>"
				+ "<p align=\"center\"><a href=\"all_files\">View all files</a>"
				+ "&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;"	
				+ "<a href=\"all_svrs\">View all slice stores</a></p>"
				+ "<hr/><table border=\"0\" align=\"center\"><tr><td>");
		
		Hashtable<FileSliceInfo, FileInfo> slices=new Hashtable<FileSliceInfo, FileInfo>();
		FileSliceServerInfo svr=fa.getFileSliceServerSlices(id, slices);
		
		if(svr==null){
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Slice store information not found.");
			return;
		}
		
		StringWriter svrPropStr=new StringWriter();
		try {
			if(svr.hasProperties()) svr.getAllProperties().store(svrPropStr, null);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		resp.append("<p><b>Slice store:</b>&nbsp;" + id
				+ "<br/><b>Host:</b>&nbsp;"+svr.getServerHost()
				+ "<br><b>Properties:</b><br/>"
				+ (!svr.hasProperties() ? "-" : svrPropStr.toString())
				+ "<br/><b>Namespace(s) registered:</b><ul>");
		for(String ns: svr.getRegisteredNamespaces()){
			resp.append("<li>"+ns+"</li>");
		}
		resp.append("</ul></p>");
		
		if(slices.size()==0){
			resp.append("<i>No file slices found.</i></td></tr></table></body></html>");
			response.setEntity(resp.toString(), MediaType.TEXT_HTML);
			return;
		}

		try{
			resp.append("<table border=\"1\"><tr>" 
					+ "<th>Namespace</th>"
					+ "<th>File path</th>"
					+ "<th>Slice Name</th>"
					+ "<th>Hash</th>"
					+ "</tr>");
			for(Map.Entry<FileSliceInfo, FileInfo> s: slices.entrySet()){
				resp.append("<tr>" 
						+ "<td>"+ s.getValue().getNamespace() +"</td>"
						+ "<td><a href=\"file?"+RestletMasterQueryPropName.File.NAMESPACE.value()+"="+URLEncoder.encode(s.getValue().getNamespace(), "UTF-8")
						+ "&"+RestletMasterQueryPropName.File.PATH.value()+"="+ URLEncoder.encode(s.getValue().getFullPath(), "UTF-8") + "\">" + s.getValue().getFullPath() + "</a></td>"
						+ "<td>"+s.getKey().getSliceName()+"</td>"
						+ "<td>"+(s.getKey().getSliceChecksum()==null? "" : s.getKey().getSliceChecksum())+"</td>"
						+ "</tr>");
			}
		}catch(Exception ex){
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, "Error retriving file information.");
			return;
		}
		
		resp.append("</table></td></tr></table></body></html>");
		response.setEntity(resp.toString(), MediaType.TEXT_HTML);
	}
	
	private void displayFileDetail(org.restlet.Request request, org.restlet.Response response){
		Form urlQuery=request.getResourceRef().getQueryAsForm();
		
		String namespace, filename;
		try{
			namespace=urlQuery.getFirstValue(RestletMasterQueryPropName.File.NAMESPACE.value(), true, null);
			filename=urlQuery.getFirstValue(RestletMasterQueryPropName.File.PATH.value(), true, null);
		}catch(Exception ex){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Invalid arguments.");
			return;
		}
		
		if(namespace==null || filename==null || namespace.length()==0 || filename.length()==0){
			response.setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Missing/Empty arguments.");
			return;
		}
		
		StringBuilder resp=new StringBuilder();
		
		resp.append("<html>" + displayPageHeader() + "<body>"
				+ "<h1 align=\"center\">Secure Virtualized Diffused Storage (SVDS) - File system viewer</h1>"
				+ "<p align=\"center\"><a href=\"all_files\">View all files</a>"
				+ "&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;"	
				+ "<a href=\"all_svrs\">View all slice stores</a></p>"
				+ "<hr/><table border=\"0\" align=\"center\"><tr><td>");
		
		try {
			FileInfo fi=fa.getFileInfo(namespace, filename, null);
			SimpleDateFormat dtFormat=new SimpleDateFormat("dd MMM yyyy HH:mm");
			
			resp.append("<p><b>Namespace:</b>&nbsp;" + namespace);
			resp.append("<br/><b>File path:</b>&nbsp;" + filename);
			resp.append("<br/><b>Type:</b>&nbsp;" + fi.getType().toString());
			resp.append("<br/><b>Size:</b>&nbsp;" + fi.getFileSize() + "&nbsp;bytes");
			resp.append("<br/><b>Date created:</b>&nbsp;"+dtFormat.format(fi.getCreationDate()));
			resp.append("<br/><b>Date modified:</b>&nbsp;"+(fi.getLastModifiedDate()==null?"":dtFormat.format(fi.getLastModifiedDate())));
			resp.append("<br/><b>Date accessed:</b>&nbsp;"+(fi.getLastAccessedDate()==null?"":dtFormat.format(fi.getLastAccessedDate()))+"</p>");
			
			List<FileSliceInfo> slices=fi.getSlices();
			if(slices==null || slices.size()==0){
				resp.append("<p><i>No slice information available.</i></p>");
			}else{
				StringBuilder respSeg=new StringBuilder();
				boolean displaySeg=false;
				SimpleDateFormat segDtFormat=new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SS");
				respSeg.append("<h3><a name=\"seg\">Segments</a></h3><table border=\"1\"><tr>"
						+ "<th>Slice Store</th>"
						+ "<th>Slice Name</th>"
						+ "<th>Segment Name</th>"
						+ "<th>Offset</th>"
						+ "<th>Size</th>"
						+ "<th>Timestamp</th>"
						+ "</tr>");
				
				resp.append("<table border=\"1\"><tr><th>S/N</th>" +
						"<th>Slice Store</th>" +
						"<th>Slice Name</th>" +
						"<th>Hash</th>" +
						"<th>Segments?</th></tr>");
				
				for(FileSliceInfo fsi: slices){
					resp.append("<tr>"
							+ "<td>"+fsi.getSliceSeq()+"</td>"
							+ "<td><a href=\"svr?"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"="+URLEncoder.encode(fsi.getServerId(), "UTF-8")+"\">"+fsi.getServerId()+"</a></td>"
							+ "<td>"+fsi.getSliceName()+"</td>"
							+ "<td>"+(fsi.getSliceChecksum()==null? "" : fsi.getSliceChecksum())+"</td>"
							+ "<td>"+(fsi.hasSegments() ? "<a href=\"#seg\">"+fsi.getSegmentCount()+"</a>" : "NA")+"</td>"
							+ "</tr>");
					
					if(fsi.hasSegments()){
						for(FileSliceInfo seg: fsi.getSegments()){
							respSeg.append("<tr>"
									+ "<td><a href=\"svr?"+RestletMasterQueryPropName.Namespace.SVR_ID.value()+"="+URLEncoder.encode(fsi.getServerId(), "UTF-8")+"\">"+seg.getServerId()+"</a></td>"
									+ "<td>"+fsi.getSliceName()+"</td>"
									+ "<td>"+seg.getSliceName()+"</td>"
									+ "<td>"+seg.getOffset()+"</td>"
									+ "<td>"+seg.getLength()+"</td>"
									+ "<td>"+segDtFormat.format(new Date(seg.getTimestamp()))+"</td>"
									+ "</tr>");
						}
						
						displaySeg=true;
					}
				}
			
				resp.append("</table>");
				respSeg.append("</table>");
				
				if(displaySeg) resp.append(respSeg);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			response.setStatus(Status.SERVER_ERROR_INTERNAL, "Error retriving file information.");
			return;
		}
		
		resp.append("</td></tr></table></body></html>");
		response.setEntity(resp.toString(), MediaType.TEXT_HTML);
	}
	
	private void displayAllFiles(org.restlet.Request request, org.restlet.Response response){
		StringBuilder resp=new StringBuilder();
		
		resp.append("<html>" + displayPageHeader() + "<body>" 
				+ "<h1 align=\"center\">Secure Virtualized Diffused Storage (SVDS) - File system viewer</h1>"
				+ "<p align=\"center\"><b>View all files"
				+ "&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;"	
				+ "<a href=\"all_svrs\">View all slice stores</a></b></p>"
				+ "<hr/><div align=\"center\">");
		
		Map<String, SortedMap<String, FileInfo>> lst_nsfiles=fa.getAllFiles();
		if(lst_nsfiles==null || lst_nsfiles.isEmpty()){
			resp.append("<i>No namespaces and files found.</i></td></tr></table></body></html>");
			response.setEntity(resp.toString(), MediaType.TEXT_HTML);
			return;
		}
		
		for(Map.Entry<String, SortedMap<String, FileInfo>> ns: lst_nsfiles.entrySet()){
			resp.append("<p><h2>Namespace:&nbsp;" + ns.getKey() + "</h2>");
			
			if(ns.getValue().isEmpty()){
				resp.append("<i>No files found.</i><br/><br/><br/>");
			}else{
				try{
					SimpleDateFormat dtFormat=new SimpleDateFormat("dd MMM yyyy HH:mm");
					
					resp.append("<table border=\"1\"><tr>"
							+ "<th>File Path</th>"
							+ "<th>Size (bytes)</th>"
							+ "<th>Date created</th>"
							+ "<th>Date Modified</th>"
							+ "<th>Date Accessed</th></tr>");
					for(FileInfo fi: ns.getValue().values()){
						resp.append("<tr>"
								+ "<td><a href=\"file?"+RestletMasterQueryPropName.File.NAMESPACE.value()+"="+URLEncoder.encode(ns.getKey(), "UTF-8")
								+"&"+RestletMasterQueryPropName.File.PATH.value()+"="+ URLEncoder.encode(fi.getFullPath(), "UTF-8") + "\">" + fi.getFullPath() + "</a></td>"
								+ "<td>"+fi.getFileSize()+"</td>"
								+ "<td>"+dtFormat.format(fi.getCreationDate())+"</td>"
								+ "<td>"+(fi.getLastModifiedDate()==null?"":dtFormat.format(fi.getLastModifiedDate()))+"</td>"
								+ "<td>"+(fi.getLastAccessedDate()==null?"":dtFormat.format(fi.getLastAccessedDate()))+"</td>"
								+ "</tr>");
					}
					resp.append("</table><br/>");
				}catch(Exception ex){
					ex.printStackTrace();
				}
			}
			
			resp.append("<b>Total no of files:</b>&nbsp;" + ns.getValue().size()
					+ "</p>");
		}
		
		resp.append("</body></html>");
		response.setEntity(resp.toString(), MediaType.TEXT_HTML);
	}
	
	private String displayPageHeader(){
		return "<head><title>Secure Virtualized Diffused Storage (SVDS) - File system viewer</title></head>";
	}
}
