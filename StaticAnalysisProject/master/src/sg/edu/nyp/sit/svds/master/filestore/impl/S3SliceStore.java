package sg.edu.nyp.sit.svds.master.filestore.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.SliceDigest;
import sg.edu.nyp.sit.svds.exception.SVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class S3SliceStore implements IFileSliceStore {
	public static final long serialVersionUID = 2L;
	
	private static final Log LOG = LogFactory.getLog(S3SliceStore.class);
	
	private enum Region{
		AP_Singapore ("s3-ap-southeast-1.amazonaws.com"),
		AP_Tokyo ("s3-ap-northeast-1.amazonaws.com"),
		EU_Ireland ("s3-eu-west-1.amazonaws.com"),
		US_Standard ("s3.amazonaws.com"),
		US_West ("s3-us-west-1.amazonaws.com");

		private String url;
		Region(String url){
			this.url=url;
		}
		public String url(){return url;}
	}
	 
	private AmazonS3Client s3=null;
	private String bucketName=null;
	
	private FileSliceServerInfo currServer=null;
	
	private void connect(FileSliceServerInfo server) throws SVDSException{
		if(currServer!=null && currServer.getServerId().equals(server.getServerId()))
			//a previous connection has been made, no need to connect again
			return;
		
		currServer=server;
		
		Properties props=server.getAllProperties();
		
		if(server.getKeyId()==null || server.getKey()==null || props==null
				|| !props.containsKey(FileSliceServerInfo.S3PropName.CONTAINER.value()))
			throw new SVDSException("Missing storage information.");
		
		try{
			BasicAWSCredentials s3Acct=new BasicAWSCredentials(server.getKeyId(), server.getKey());

			ClientConfiguration s3Config=new ClientConfiguration();
			s3Config.withProtocol(Protocol.HTTP);
			if(props.containsKey(FileSliceServerInfo.S3PropName.RETRY_CNT.value())){
				s3Config=s3Config.withMaxErrorRetry(
						Integer.parseInt(props.get(FileSliceServerInfo.S3PropName.RETRY_CNT.value()).toString()));
			}

			bucketName=props.get(FileSliceServerInfo.S3PropName.CONTAINER.value()).toString().toLowerCase();

			s3=new AmazonS3Client(s3Acct, s3Config);
			s3.setEndpoint(Region.valueOf(server.getServerHost()).url());

			if(!s3.doesBucketExist(bucketName))
				throw new SVDSException("Container "+bucketName+" does not exist.");
		}catch(SVDSException ex){
			throw ex;
		}catch(AmazonServiceException ex){
			throw new SVDSException(ex);
		}catch(AmazonClientException ex){
			throw new SVDSException(ex);
		}
	}
	
	@Override
	public boolean isAlive(FileSliceServerInfo server) throws Exception{
		try{
			connect(server);
			
			return true;
		}catch(Exception ex){
			LOG.error(ex);
			return false;
		}
	}
	
	@Override
	public String[] getAccessURL(FileSliceServerInfo server, String name) throws Exception {
		connect(server);
		
		Date expiry=new Date();
		expiry.setTime(expiry.getTime()
				+(MasterProperties.getInt(MasterProperties.PropName.SLICESTORE_ACCESS_URL_LIMIT)*1000*60));
		
		//gets access url for not just the slice but also the checksum
		return new String[]{s3.generatePresignedUrl(bucketName, name, expiry, HttpMethod.GET).toString(),
						s3.generatePresignedUrl(bucketName, name+".chk", expiry, HttpMethod.GET).toString(),
						s3.generatePresignedUrl(bucketName, name, expiry, HttpMethod.PUT).toString(),
						s3.generatePresignedUrl(bucketName, name+".chk", expiry, HttpMethod.PUT).toString(),
						s3.generatePresignedUrl(bucketName, name, expiry, HttpMethod.DELETE).toString(),
						s3.generatePresignedUrl(bucketName, name+".chk", expiry, HttpMethod.DELETE).toString()};
	}
	
	@Override
	public List<byte[]> retrieveHashes(FileSliceServerInfo server, String name)
			throws Exception {
		connect(server);
		
		S3Object chksum=s3.getObject(bucketName, name+".chk");
		if(chksum==null)
			throw new SVDSException("Unable to retrieve slice checksum");
		
		List<byte[]> hashes=new ArrayList<byte[]>();
		byte[] d=new byte[Resources.HASH_BIN_LEN];
		InputStream in= new BufferedInputStream(chksum.getObjectContent());
		while((in.read(d))!=-1){
			//must copy to a new array as list will only keep the reference
			hashes.add(Arrays.copyOf(d, d.length));
		}
		in.close();
		
		return hashes;
	}
	
	@Override
	public void delete(FileSliceServerInfo server, String name)
			throws Exception {
		connect(server);
		
		s3.deleteObject(bucketName, name);
		
		//deletes the checksum file, if any
		s3.deleteObject(bucketName, name+".chk");
	}
	
	@Override
	public void store(FileSliceServerInfo server, String name, long offset,  byte[] in, 
			int inOffset, int inLen, SliceDigestInfo md) throws Exception {
		throw new UnsupportedOperationException("Partial storage not supported.");
	}
	
	@Override
	public void store(FileSliceServerInfo server, String name, byte[] in, 
			int inOffset, int inLen, SliceDigestInfo md) throws Exception {
		connect(server);
		
		//put the slice data
		s3.putObject(bucketName, name, new ByteArrayInputStream(in, inOffset, inLen), null);
		
		//put the checksums if available
		if(md!=null){
			ByteArrayOutputStream out=new ByteArrayOutputStream();
			for(byte[] h: md.getBlkHashes()){
				out.write(h);
			}
			
			s3.putObject(bucketName, name+".chk", new ByteArrayInputStream(out.toByteArray()), null);
		}
	}
	
	@Override
	public byte[] retrieve(FileSliceServerInfo server, String name,
			int blkSize) throws Exception {
		connect(server);
		
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		
		if(blkSize>0){
			List<byte[]> hashes=retrieveHashes(server, name);
			if(hashes==null || hashes.size()==0)
				throw new SVDSException("Unable to retrieve slice checksum");
			
			out.write(SliceDigest.combineBlkHashes(hashes));
		}
		
		S3Object data=s3.getObject(bucketName, name);
		if(data==null)
			throw new SVDSException("Unable to retrieve slice");
		
		byte[] tmp=new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		InputStream in=data.getObjectContent();
		while((len=in.read(tmp))!=-1)
			out.write(tmp, 0, len);
		in.close();
		
		return out.toByteArray();
	}
}
