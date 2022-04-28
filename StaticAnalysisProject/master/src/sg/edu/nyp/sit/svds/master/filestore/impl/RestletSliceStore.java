package sg.edu.nyp.sit.svds.master.filestore.impl;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.master.filestore.IRestletSliceStoreAuthentication;
import sg.edu.nyp.sit.svds.master.filestore.RestletSliceStoreAuthenticationFactory;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.RestletFileSliceServerQueryPropName;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class RestletSliceStore implements IFileSliceStore {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(RestletSliceStore.class);
	private IRestletSliceStoreAuthentication ssa=null;
	
	public RestletSliceStore(){
		ssa=RestletSliceStoreAuthenticationFactory.getInstance();
	}
	
	@Override
	public boolean isAlive(FileSliceServerInfo server) throws Exception{
		HttpURLConnection fsConn=null;
		
		try{
			String strUrl = "http" +(server.getProperty(FileSliceServerInfo.RestletPropName.STATUS_SSL.value()).equalsIgnoreCase("on") ?
					"s":"")+"://" + server.getProperty(FileSliceServerInfo.RestletPropName.STATUS_HOST.value()) + "/status?"
					+RestletFileSliceServerQueryPropName.Status.INFO.value()+"="
					+RestletFileSliceServerQueryPropName.StatusReq.STATUS_ALIVE.value()+","
					+RestletFileSliceServerQueryPropName.StatusReq.KEY.value();
			
			//System.out.println(strUrl);

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();
			
			fsConn.setDoInput(true);

			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK){
				LOG.debug("Check slice server status: " + fsConn.getResponseCode() + "-"+fsConn.getResponseMessage());
				return false;
			}
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(fsConn.getInputStream()));
			Properties data=new Properties();
			data.load(reader);
			reader.close();
			
			if(!data.containsKey(FileSliceServerInfo.PropName.KEY.value()))
				throw new NullPointerException("Unable to retrieve slice store key from server.");

			server.setKey((data.get(FileSliceServerInfo.PropName.KEY.value()).toString().length()==0?
					null:data.get(FileSliceServerInfo.PropName.KEY.value()).toString()));
			
			LOG.debug("Slice server key " + server.getKey());
			
			return true;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	@Override
	public String[] getAccessURL(FileSliceServerInfo server, String name) throws Exception {
		Date expiry=new Date();
		expiry.setTime(expiry.getTime()
				+(MasterProperties.getInt(MasterProperties.PropName.SLICESTORE_ACCESS_URL_LIMIT)*1000*60));
		
		//gets access url for not just the slice but also the checksum
		return new String[]{(String)ssa.generateSharedAuthentication(server, expiry, name)};
	}
	
	@Override
	public List<byte[]> retrieveHashes(FileSliceServerInfo server, String name)
			throws Exception {
		HttpURLConnection fsConn=null;
		
		try{
			LOG.debug("FS key @ recovery: " + server.getKey());
			String query=RestletFileSliceServerQueryPropName.Slice.NAME.value()+"="+ URLEncoder.encode(name, "UTF-8");
			String strUrl = "http://" + server.getServerHost() + "/svds/hget?" 
				+query
				+ (String)ssa.generateAuthentication(server, query);
				//+(server.getKey()==null?"":
				//	"&"+MasterProperties.SLICESTORE_SIGNATURE_QUERY_PROP+"="+
				//	Resources.computeSignature(query, server.getKey()));

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();

			fsConn.setDoInput(true);

			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());

			byte[] d=new byte[Resources.HASH_BIN_LEN];
			List<byte[]> hashes=new ArrayList<byte[]>();

			InputStream in= new BufferedInputStream(fsConn.getInputStream());
			while((in.read(d))!=-1){			
				//must copy to a new array as list will only keep the reference
				hashes.add(Arrays.copyOf(d, d.length));
			}
			d=null;
			in.close();

			return hashes;
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}

	@Override
	public void delete(FileSliceServerInfo server, String name){
		HttpURLConnection fsConn=null;
		
		try{
			String query=RestletFileSliceServerQueryPropName.Slice.NAME.value()+"="+ URLEncoder.encode(name, "UTF-8");
			String strUrl = "http://" + server.getServerHost() + "/svds/delete?" 
				+query
				+ (String)ssa.generateAuthentication(server, query);
				//+(server.getKey()==null?"":
				//	"&"+MasterProperties.SLICESTORE_SIGNATURE_QUERY_PROP+"="+
				//	Resources.computeSignature(query, server.getKey()));

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();

			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());
		}catch(Exception ex){
			LOG.error(ex);
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}
	
	@Override
	public void store(FileSliceServerInfo server, String name, byte[] in, 
			int inOffset, int inLen, SliceDigestInfo md) throws Exception {
		store(server, name, 0, in, inOffset, inLen, md);
	}

	@Override
	public void store(FileSliceServerInfo server, String name, long offset, byte[] in, 
			int inOffset, int inLen, SliceDigestInfo md) throws Exception {
		HttpURLConnection fsConn=null;

		try{
			String query=RestletFileSliceServerQueryPropName.Slice.NAME.value()+"="+ URLEncoder.encode(name, "UTF-8")
				+ "&"+RestletFileSliceServerQueryPropName.Slice.OFFSET.value()+"="+offset
				+(md==null?"":"&"+RestletFileSliceServerQueryPropName.Slice.CHECKSUM.value()+"="+URLEncoder.encode(md.getChecksum(), "UTF-8")
						+ "&"+RestletFileSliceServerQueryPropName.Slice.FILE_BLKSIZE.value()+"="+md.getBlkSize()
						+(md.getKey()==null?"" : "&"+RestletFileSliceServerQueryPropName.Slice.FILE_KEYHASH.value())+"="
							+URLEncoder.encode(md.getKey(), "UTF-8"));
			
			String strUrl = "http://" + server.getServerHost() + "/svds/put?" 
				+query
				+ (String)ssa.generateAuthentication(server, query);
				//+(server.getKey()==null?"":
				//	"&"+MasterProperties.SLICESTORE_SIGNATURE_QUERY_PROP+"="+
				//	Resources.computeSignature(query, server.getKey()));

			URL fsUrl = new URL(strUrl);
			fsConn=(HttpURLConnection)fsUrl.openConnection();

			fsConn.setDoOutput(true);

			OutputStream out=fsConn.getOutputStream();
			out.write(in, inOffset, inLen);
			out.close();

			if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
				throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());
		}finally{
			if(fsConn!=null)
				fsConn.disconnect();
		}
	}

	@Override
	public byte[] retrieve(FileSliceServerInfo server, String name,
			int blkSize) throws Exception {
		HttpURLConnection fsConn=null;
		
		LOG.debug("FS key @ recovery: " + server.getKey());
		String query=RestletFileSliceServerQueryPropName.Slice.NAME.value()+"="+ URLEncoder.encode(name, "UTF-8")
			+ (blkSize==0?"":"&"+RestletFileSliceServerQueryPropName.Slice.FILE_BLKSIZE.value()+"="+blkSize);
		
		String strUrl = "http://" + server.getServerHost() + "/svds/get?" 
			+query
			+ (String)ssa.generateAuthentication(server, query);
			//+(server.getKey()==null?"":
			//	"&"+MasterProperties.SLICESTORE_SIGNATURE_QUERY_PROP+"="+
			//	Resources.computeSignature(query, server.getKey()));
		//LOG.debug(strUrl);

		URL fsUrl = new URL(strUrl);
		fsConn=(HttpURLConnection)fsUrl.openConnection();

		fsConn.setDoInput(true);

		if(fsConn.getResponseCode()!=HttpURLConnection.HTTP_OK)
			throw new Exception(fsConn.getResponseCode() + ": " + fsConn.getResponseMessage());

		InputStream in=fsConn.getInputStream();
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		int len;
		byte[] tmp=new byte[Resources.DEF_BUFFER_SIZE];
		while((len=in.read(tmp))!=-1){
			out.write(tmp,0, len);
		}
		in.close();
		fsConn.disconnect();
		out.close();
		
		return out.toByteArray();
	}

}
