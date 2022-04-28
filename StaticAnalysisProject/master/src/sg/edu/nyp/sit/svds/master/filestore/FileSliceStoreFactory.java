package sg.edu.nyp.sit.svds.master.filestore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;

public class FileSliceStoreFactory {
	public static final long serialVersionUID = 1L;
	
	private static final Log LOG = LogFactory.getLog(FileSliceStoreFactory.class);
	
	private static final String AZURE_FS_PROP="slicestore.azure";
	private static final String S3_FS_PROP="slicestore.s3";
	private static final String RESTLET_FS_PROP="slicestore.restlet";
	
	private static IFileSliceStore restletInstance=null;
	
	@SuppressWarnings("rawtypes")
	public static IFileSliceStore getInstance(FileSliceServerInfo.Type type) {
		Class cls=null;
		
		try{
			switch(type){
				case AZURE:
					//new object everytime because object might be pointing to different azure 
					//storage account
					cls = Class.forName(MasterProperties.getString(AZURE_FS_PROP));
					break;
				case RESTLET:
					if(restletInstance==null)
						restletInstance=(IFileSliceStore)Class.forName(
								MasterProperties.getString(RESTLET_FS_PROP)).newInstance();
					
					return restletInstance;
				case S3:
					cls = Class.forName(MasterProperties.getString(S3_FS_PROP));
					break;
				default:
					return null;
			}
			
			return (IFileSliceStore)cls.newInstance();
		}catch(Exception ex){
			LOG.error(ex);
			return null;
		}
	}
}
