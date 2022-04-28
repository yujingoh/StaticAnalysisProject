package sg.edu.nyp.sit.svds.master.filestore.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.microsoft.windowsazure.services.core.storage.*;
import com.microsoft.windowsazure.services.blob.client.*;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.SliceDigest;
import sg.edu.nyp.sit.svds.exception.SVDSException;
import sg.edu.nyp.sit.svds.master.MasterProperties;
import sg.edu.nyp.sit.svds.master.filestore.IFileSliceStore;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class AzureSliceStore implements IFileSliceStore {
	public static final long serialVersionUID = 3L;
	
	private static final Log LOG = LogFactory.getLog(AzureSliceStore.class);
	private static final int AZURE_MIN_PG_SIZE=512;
	private static final int AZURE_MAX_PG_SIZE=1024*1024*4;	//4MB
	//private static final int BLOB_MAX_SIZE=Integer.MAX_VALUE-(Integer.MAX_VALUE%AZURE_MIN_PG_SIZE);
	private static final long BLOB_MAX_SIZE=1024*1024*1024*1024;	//1TB	

	private CloudBlobClient blobStorage=null;
	private CloudBlobContainer blobContainer=null;
	private FileSliceServerInfo currServer=null;

	private int defRetryLimit=5;
	private int defRetryInterval=1000*5; //in seconds
	
	private void connect(FileSliceServerInfo server) throws SVDSException{
		if(currServer!=null && currServer.getServerId().equals(server.getServerId()))
			//a previous connection has been made, no need to connect again
			return;
		
		currServer=server;
		
		Properties props=server.getAllProperties();
		
		if(server.getKeyId()==null || server.getKey()==null)
			throw new SVDSException("Missing storage information.");
		
		if(props==null) props=new Properties();
		
		try{
			CloudStorageAccount cloudAccount=new CloudStorageAccount(
					new StorageCredentialsAccountAndKey(server.getKeyId(), server.getKey()));
			
			blobStorage=cloudAccount.createCloudBlobClient();
			blobContainer=blobStorage.getContainerReference(currServer.getServerId().toLowerCase());
			if(!blobContainer.exists())
				throw new SVDSException("Storage container " + currServer.getServerId().toLowerCase() + " does not exist.");
			
			blobStorage.setRetryPolicyFactory(new RetryLinearRetry(
					(props.containsKey(FileSliceServerInfo.AzurePropName.RETRY_INTERVAL.value())? 
							Integer.parseInt(props.get(FileSliceServerInfo.AzurePropName.RETRY_INTERVAL.value()).toString())
							: defRetryInterval),
					(props.containsKey(FileSliceServerInfo.AzurePropName.RETRY_CNT.value())? Integer.parseInt(props.get(FileSliceServerInfo.AzurePropName.RETRY_CNT.value()).toString())
							: defRetryLimit)));
		}catch(Exception ex){
			ex.printStackTrace();
			throw new SVDSException(ex);
		}
	}
	
	@Override
	public boolean isAlive(FileSliceServerInfo server) throws Exception{
		return (new CloudStorageAccount(new StorageCredentialsAccountAndKey(server.getKeyId(), server.getKey())))
				.createCloudBlobClient().getContainerReference(server.getServerId().toLowerCase()).exists();
	}
	
	@Override
	public String[] getAccessURL(FileSliceServerInfo server, String name) throws Exception {
		connect(server);
		
		SharedAccessPolicy sap=new SharedAccessPolicy();
		sap.setPermissions(EnumSet.of(SharedAccessPermissions.READ, 
				SharedAccessPermissions.WRITE, SharedAccessPermissions.DELETE));
		Date now=new Date();
		sap.setSharedAccessStartTime(now);
		sap.setSharedAccessExpiryTime(new Date(now.getTime()
				+(MasterProperties.getInt(MasterProperties.PropName.SLICESTORE_ACCESS_URL_LIMIT)*1000*60)));
		
		//gets access url for not just the slice but also the checksum
		return new String[]{blobStorage.getEndpoint().toString(), 
				name,
				blobContainer.getPageBlobReference(name).generateSharedAccessSignature(sap),
				blobContainer.getPageBlobReference(name+".chk").generateSharedAccessSignature(sap)};
	}
	
	@Override
	public List<byte[]> retrieveHashes(FileSliceServerInfo server, String name)
			throws Exception {
		connect(server);
		
		List<byte[]> hashes=null;
		
		CloudPageBlob pgBlob=blobContainer.getPageBlobReference(name+".chk");
		if(!pgBlob.exists()) return null;

		List<PageRange> pges=pgBlob.downloadPageRanges();
		if(pges==null || pges.size()==0) return null;

		hashes=new ArrayList<byte[]>();
		int rLen;
		byte[] tmp=null;
		for(PageRange r: pges){
			rLen=(int) (r.getEndOffset()-r.getStartOffset()+1);
			//as each blk checksum is stored in 1 pg (512 byte), cal how many
			//pages are in the return page range and get each blk checksum

			for(int i=0; i<rLen/AZURE_MIN_PG_SIZE; i++){
				tmp=new byte[Resources.HASH_BIN_LEN];
				pgBlob.downloadRange(r.getStartOffset(), Resources.HASH_BIN_LEN, tmp, 0);
				hashes.add(tmp);

				r.setStartOffset(r.getStartOffset()+AZURE_MIN_PG_SIZE);
			}
		}
		
		return hashes;
	}

	@Override
	public void delete(FileSliceServerInfo server, String name)
			throws Exception {
		connect(server);
		
		CloudPageBlob blob=blobContainer.getPageBlobReference(name);
		blob.delete();

		//deletes the checksum file, if any
		blob=blobContainer.getPageBlobReference(name+".chk");
		blob.deleteIfExists();
	}
	
	@Override
	public void store(FileSliceServerInfo server, String name, 
			byte[] in, int inOffset, int inLen, SliceDigestInfo md)
			throws Exception {
		store(server, name, 0, in, inOffset, inLen, md);
	}

	@Override
	public void store(FileSliceServerInfo server, String name, long offset,
			byte[] in, int inOffset, int inLen, SliceDigestInfo md)
			throws Exception {
		if(offset%AZURE_MIN_PG_SIZE>0)
			throw new SVDSException("Offset must be 512 bytes aligned.");
		
		connect(server);
		
		writeBlob(in, name, (int)offset, inOffset, inLen);
		
		if(md!=null){
			try{
				List<byte[]> chkArr=md.getBlkHashes(offset, inLen);
				offset=offset/md.getBlkSize() * AZURE_MIN_PG_SIZE;
				for(int i=0; i<chkArr.size(); i++){
					//System.out.println("write hash: " + offset);
		    	
					writeBlob(chkArr.get(i), name+".chk", (int)offset, 0, Resources.HASH_BIN_LEN);

					offset+=AZURE_MIN_PG_SIZE;
				}
		    }catch(SVDSException ex){
		    	//if write hash file, delete the slice such that it never exist;
		    	try {
		    		blobContainer.getPageBlobReference(name).deleteIfExists();
		    	} catch (Exception e) { e.printStackTrace(); }
		    	
		    	ex.printStackTrace();
		    	throw ex;
		    }
		}
	}


	private void writeBlob(byte[] in, String name, int startOffset, int inOffset, int inLen) throws SVDSException{	
		try{
			CloudPageBlob pgBlob=blobContainer.getPageBlobReference(name);
			if(!pgBlob.exists()){
				pgBlob.create(BLOB_MAX_SIZE);
				pgBlob.getProperties().setContentType("application/octet-stream");
			}
			
			byte[] data=in;
			//if the length of the byte array is not multiples of the min page size, increase the byte array to multiples of min page size
			if(inLen%AZURE_MIN_PG_SIZE>0) {
				inLen=((inLen/AZURE_MIN_PG_SIZE)+1)*AZURE_MIN_PG_SIZE;
				data=new byte[inLen];
				System.arraycopy(in, 0, data, 0, in.length);
			}
			
			if(inLen<=AZURE_MAX_PG_SIZE){
				pgBlob.uploadPages(new ByteArrayInputStream(data), startOffset, inLen);
			}else{
				int offset=0;
				while(inLen>AZURE_MAX_PG_SIZE){
					pgBlob.uploadPages(new ByteArrayInputStream(data, offset, AZURE_MAX_PG_SIZE), startOffset, AZURE_MAX_PG_SIZE);
					
					inLen-=AZURE_MAX_PG_SIZE;
					startOffset+=AZURE_MAX_PG_SIZE;
					offset+=AZURE_MAX_PG_SIZE;
				}
				if(inLen>0){
					pgBlob.uploadPages(new ByteArrayInputStream(data, offset, inLen), startOffset, inLen);
				}
			}
		}catch(Exception ex){
			ex.printStackTrace();
			throw new SVDSException(ex);
		}
	}
	
	@Override
	public byte[] retrieve(FileSliceServerInfo server, String name,
			int blkSize) throws Exception {
		connect(server);
		
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		
		//System.out.println("Getting data for slice " + name);
		
		if(blkSize>0){
			List<byte[]> hashes=retrieveHashes(server, name);
			if(hashes==null || hashes.size()==0)
				throw new SVDSException("Unable to retrieve slice checksum");

			byte[] combinedHash=SliceDigest.combineBlkHashes(hashes);
			out.write(combinedHash);
		}		
		
		try{
			CloudPageBlob pgBlob=blobContainer.getPageBlobReference(name);
			if(!pgBlob.exists()) return null;
			
			//get list of pages with valid data
			List<PageRange> pges=pgBlob.downloadPageRanges();
			
			//if checksum is required then fill it with 0 first
			if(blkSize>0) for(int i=0;i<Resources.HASH_BIN_LEN; i++) out.write(0);
			
			byte[] tmp=new byte[0];
			for(PageRange r: pges) {
				if(r.getEndOffset()-r.getStartOffset()+1>tmp.length){
					tmp=null;
					tmp=new byte[(int) (r.getEndOffset()-r.getStartOffset()+1)];
				}
				pgBlob.downloadRange(r.getStartOffset(), (int)(r.getEndOffset()-r.getStartOffset()+1), tmp, 0);
				out.write(tmp, 0, (int)(r.getEndOffset()-r.getStartOffset()+1));
			}
			tmp=null;
		}catch(Exception ex){
			throw new SVDSException(ex);
		}
		
		if(out.size()<=(blkSize>0?Resources.HASH_BIN_LEN:0)){
			LOG.debug("no data found!");
			return null;
		}

		return out.toByteArray();
	}
}
