package sg.edu.nyp.sit.svds.master.persistence;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.master.file.FileAction;
import sg.edu.nyp.sit.svds.metadata.FileIOMode;
import sg.edu.nyp.sit.svds.metadata.FileInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceServerInfo;
import sg.edu.nyp.sit.svds.metadata.NamespaceInfo;
import sg.edu.nyp.sit.svds.metadata.FileSliceInfo;

public class MasterImage {
	public static final long serialVersionUID = 3L;
	
	private enum ImageEntry {
		NAMESPACE,
		SLICE_SERVER,
		FILE,
		DIR,
		LOCK,
		CHG_MODE,
		END
	}
	
	private enum ImageEntryDataIndex{
		FILE_NAMESPACE (1),
		FILE_FULLPATH (2),
		FILE_IDA_VERSION (3),
		FILE_SIZE (4),
		FILE_OWNER (5),
		FILE_CREATEDT (6),
		FILE_LASTMODDT (7),
		FILE_LASTACCESSEDDT (8),
		FILE_BLKSIZE (9),
		FILE_KEYHASH (10),
		FILE_SLICE_SEQ (0),
		FILE_SLICE_NAME (1),
		FILE_SLICE_SVR (2),
		FILE_SLICE_LEN (3),
		FILE_SLICE_CHKSUM (4),
		FILE_SLICE_SEGCNT (5),
		FILE_SLICE_SEG_NAME (0),
		FILE_SLICE_SEG_SVR (1),
		FILE_SLICE_SEG_OFFSET (2),
		FILE_SLICE_SEG_LEN (3),
		FILE_LOCK_USR (1),
		FILE_CHG_MODE (1),
		DIR_NAMESPACE (1),
		DIR_FULLPATH (2),
		DIR_OWNER (3),
		DIR_CREATEDT (4),
		DIR_LASTMODDT (5),
		NAMESPACE_NAME (1),
		NAMESPACE_MEM_AVA (2), 
		NAMESPACE_MEM_USED (3),
		SLICE_SVR_ID (1),
		SLICE_SVR_HOST (2),
		SLICE_SVR_TYPE (3),
		SLICE_SVR_MODE (4),
		SLICE_SVR_KEYID (5),
		SLICE_SVR_KEY (6);
		
		private final int i;
		ImageEntryDataIndex(int i){ this.i=i; }
		public int index(){ return i;}
	}
	
	//default path for image file
	private File fImage=null;
	
	public MasterImage(String path){
		setImgPath(path);
	}
	
	public MasterImage(File f){
		setImgFile(f);
	}
	
	public void setImgPath(String path){
		fImage=new File(path + "/svds.img");
	}
	
	public void setImgFile(File f){
		fImage=f;
	}
	
	public File getImg(){
		return fImage;
	}
	
	public void deleteImg(){
		if(fImage==null)
			return;
		
		if(fImage.exists())
			fImage.delete();
	}
	
	public synchronized void updateImage(InputStream in) throws Exception{
		FileOutputStream out=null;

		if(fImage.exists())
			fImage.delete();

		fImage.createNewFile();

		out=new FileOutputStream(fImage);

		byte data[]=new byte[Resources.DEF_BUFFER_SIZE];
		int len=0;
		while((len=in.read(data))!=-1){
			out.write(data,0,len);
			out.flush();
		}
		data=null;
		out.close();
		in.close();

		out=null;
	}

	public synchronized void updateImage(Map<String, NamespaceInfo> namespaces, 
			Map<String, FileSliceServerInfo> sliceServers,
			Map<String, SortedMap<String, FileInfo>> fileNamespaces) throws Exception{
		FileOutputStream out=null;
		
		if(fImage.exists())
			fImage.delete();

		fImage.createNewFile();

		out=new FileOutputStream(fImage);
		
		for(FileSliceServerInfo fss: sliceServers.values()){
			out.write((ImageEntry.SLICE_SERVER + "\t"
					+ fss.getServerId() + "\t"
					+ fss.getServerHost() + "\t"
					+ fss.getType().value() + "\t"
					+ fss.getMode().value() + "\t"
					+(fss.getKeyId()==null ? " " : fss.getKeyId()) + "\t"
					+(fss.getKey()!=null && fss.getType()!=FileSliceServerInfo.Type.RESTLET ? fss.getKey() : " ") + "\n" 
					).getBytes());

			if(fss.hasProperties()){
				//fss.getAllProperties().store(out, null);
				Resources.storeProperties(fss.getAllProperties(), out);
			}

			out.write((ImageEntry.END+"\n").getBytes());
		}

		for(NamespaceInfo n: namespaces.values()){
			out.write((ImageEntry.NAMESPACE + "\t"
					+ n.getNamespace() + "\t"
					+ n.getMemoryAvailable() + "\t"
					+ n.getMemoryUsed() + "\n"
			).getBytes());

			for(String fss: n.getServers()){
				out.write((fss + "\n").getBytes());
			}

			out.write((ImageEntry.END + "\n").getBytes());

			out.flush();
		}

		for(SortedMap<String, FileInfo> files: fileNamespaces.values()){
			for(FileInfo fi: files.values()){
				if(fi.getFullPath().equals(FileInfo.PATH_SEPARATOR))
					continue;
				
				if(fi.getType()==FileInfo.Type.FILE){
					out.write((ImageEntry.FILE + "\t"
							+ fi.getNamespace() + "\t"
							+ fi.getFullPath() + "\t"
							+ fi.getIdaVersion() + "\t"
							+ fi.getFileSize() + "\t"
							+ fi.getOwner().getId() + "\t"
							+ fi.getCreationDate().getTime() + "\t"
							+ fi.getLastModifiedDate().getTime() + "\t"
							+ fi.getLastAccessedDate().getTime() + "\t"
							+ fi.getBlkSize() + "\t" 
							+ (fi.getKeyHash()==null?" ":fi.getKeyHash()) + "\n"
					).getBytes());

					for(FileSliceInfo fsi: fi.getSlices()){
						out.write((fsi.getSliceSeq() + "\t"
								+ fsi.getSliceName() + "\t"
								+ fsi.getServerId() + "\t"
								+ fsi.getLength() + "\t"
								+ (fsi.getSliceChecksum()==null?" ":fsi.getSliceChecksum()) + "\t"
								+ (fsi.hasSegments() ? fsi.getSegments().size() : 0) + "\n"
						).getBytes());
						
						if(fsi.hasSegments()){
							for(FileSliceInfo seg: fsi.getSegments()){
								out.write((seg.getSliceName() + "\t"
										+ seg.getServerId() + "\t"
										+ seg.getOffset() + "\t"
										+ seg.getLength() + "\n"
										).getBytes());
							}
						}
					}

					out.write((ImageEntry.END + "\n").getBytes());
					
					if(fi.getLockBy()!=null)
						out.write((ImageEntry.LOCK + "\t" + fi.getLockBy().getId() + "\n").getBytes());
					if(fi.isChgMode())
						out.write((ImageEntry.CHG_MODE + "\t" + fi.getChgMode().value() + "\n").getBytes());
				}else{
					out.write((ImageEntry.DIR+"\t"
							+fi.getNamespace()+"\t"
							+fi.getFullPath()+"\t"
							+fi.getOwner()+"\t"
							+fi.getCreationDate().getTime()+"\n"
					).getBytes());
				}

				out.flush();
			}
		}

		out.flush();
		out.close();
		out=null;
	}
	
	public void readNamespaces(Map<String, NamespaceInfo> lst_namespaces,
			Map<String, FileSliceServerInfo> lst_sliceServers) throws Exception{	
		if(!fImage.exists())
			return;
		
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(fImage)));
		
		String data=null;
		String[] details=null;
		FileSliceServerInfo fss=null;
		while((data=in.readLine()) != null){
			//System.out.println("namespace: " +data);
			if(data.startsWith(ImageEntry.SLICE_SERVER.toString()+"\t")){
				details=data.split("\t");
				if(details.length<7)
					throw new IOException("Missing details in file slice server entry.");
				
				fss=new FileSliceServerInfo(
						details[ImageEntryDataIndex.SLICE_SVR_ID.index()], 
						details[ImageEntryDataIndex.SLICE_SVR_HOST.index()], 
						FileSliceServerInfo.Type.valueOf(Integer.parseInt(details[ImageEntryDataIndex.SLICE_SVR_TYPE.index()])), 
						FileIOMode.valueOf(Integer.parseInt(details[ImageEntryDataIndex.SLICE_SVR_MODE.index()])));
				
				if(details[ImageEntryDataIndex.SLICE_SVR_KEYID.index()].trim().length()>0){
					fss.setKeyId(details[ImageEntryDataIndex.SLICE_SVR_KEYID.index()].trim());
				}
				if(details[ImageEntryDataIndex.SLICE_SVR_KEY.index()].trim().length()>0){
					fss.setKey(details[ImageEntryDataIndex.SLICE_SVR_KEY.index()].trim());
				}
				
				Properties props=getSliceServerProperties(in);
				if(props!=null) fss.setAllProperties(props);
				
				lst_sliceServers.put(details[ImageEntryDataIndex.SLICE_SVR_ID.index()], fss);
			}else if(data.startsWith(ImageEntry.NAMESPACE.toString()+"\t")){
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in namespace entry.");
				
				NamespaceInfo n=new NamespaceInfo(
						details[ImageEntryDataIndex.NAMESPACE_NAME.index()], 
						Long.parseLong(details[ImageEntryDataIndex.NAMESPACE_MEM_AVA.index()]), 
						Long.parseLong(details[ImageEntryDataIndex.NAMESPACE_MEM_USED.index()]));
				
				while((data=in.readLine()) != null){
					if(data.equals(ImageEntry.END.toString()))
						break;

					if(!lst_sliceServers.containsKey(data))
						throw new IOException("File slice server " + data + " not found.");
					
					n.addFileSliceServer(data);
					lst_sliceServers.get(data).addRegisteredNamespace(details[ImageEntryDataIndex.NAMESPACE_NAME.index()]);
				}

				lst_namespaces.put(details[ImageEntryDataIndex.NAMESPACE_NAME.index()], n);
			}
		}
		
		in.close();
		in=null;
	}
	
	private Properties getSliceServerProperties(BufferedReader in) throws Exception{
		String data;
		
		data=in.readLine();
		StringBuilder str=new StringBuilder();
		while(data!=null && !data.equals(ImageEntry.END.toString())){
			str.append(data+"\n");
			data=in.readLine();
		}

		if(str.length()>0){
			Properties props=new Properties();
			props.load(new StringReader(str.toString()));
	
			return props;
		}else
			return null;
	}
	
	public void readDirFiles(Map<String, SortedMap<String, FileInfo>> lst) throws Exception{
		if(!fImage.exists())
			return;
		
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(fImage)));
		
		FileAction fa=new FileAction(null, lst, null, null, null);
		
		String data=null, details[]=null, newDetails[]=null;
		while((data=in.readLine()) != null){
			//System.out.println("files: " + data);
			if(data.startsWith(ImageEntry.FILE.toString()+"\t")){
				details=data.split("\t");
				if(details.length<11)
					throw new IOException("Missing details in file entry.");

				fa.addFileInfo(details[ImageEntryDataIndex.FILE_NAMESPACE.index()], 
						details[ImageEntryDataIndex.FILE_FULLPATH.index()], 
						Integer.parseInt(details[ImageEntryDataIndex.FILE_IDA_VERSION.index()]), 
						Long.parseLong(details[ImageEntryDataIndex.FILE_SIZE.index()]), 
						details[ImageEntryDataIndex.FILE_OWNER.index()], 
						new Date(Long.parseLong(details[ImageEntryDataIndex.FILE_CREATEDT.index()])), 
						new Date(Long.parseLong(details[ImageEntryDataIndex.FILE_LASTMODDT.index()])),
						new Date(Long.parseLong(details[ImageEntryDataIndex.FILE_LASTACCESSEDDT.index()])),
						Integer.parseInt(details[ImageEntryDataIndex.FILE_BLKSIZE.index()]),
						(details[ImageEntryDataIndex.FILE_KEYHASH.index()].trim().length()==0?null:
							details[ImageEntryDataIndex.FILE_KEYHASH.index()]), getFileSlices(in));
			}else if(data.startsWith(ImageEntry.LOCK.toString()+"\t")){
				newDetails=data.split("\t");
				if(newDetails.length<2)
					throw new IOException("Missing details in file lock entry.");
				
				fa.lockFileInfo(details[ImageEntryDataIndex.FILE_NAMESPACE.index()], 
						details[ImageEntryDataIndex.FILE_FULLPATH.index()], 
						newDetails[ImageEntryDataIndex.FILE_LOCK_USR.index()], false);
			}else if(data.startsWith(ImageEntry.CHG_MODE.toString()+"\t")){
				newDetails=data.split("\t");
				if(newDetails.length<2)
					throw new IOException("Missing details in file change mode entry.");
				
				fa.fileChangeMode(details[ImageEntryDataIndex.FILE_NAMESPACE.index()], 
						details[ImageEntryDataIndex.FILE_FULLPATH.index()], 
						FileIOMode.valueOf(Integer.parseInt(newDetails[ImageEntryDataIndex.FILE_CHG_MODE.index()])));
			}else if(data.startsWith(ImageEntry.DIR.toString()+"\t")){
				details=data.split("\t");
				if(details.length<5)
					throw new IOException("Missing details in directory entry.");
				
				fa.addDirectoryInfo(details[ImageEntryDataIndex.DIR_NAMESPACE.index()], 
						details[ImageEntryDataIndex.DIR_FULLPATH.index()], 
						details[ImageEntryDataIndex.DIR_OWNER.index()], 
						new Date(Long.parseLong(details[ImageEntryDataIndex.DIR_CREATEDT.index()])));
			}
		}
		
		in.close();
		in=null;
		fa=null;
	}
	
	private List<FileSliceInfo> getFileSlices(BufferedReader in)throws Exception{
		String data=null, details[]=null;
		int segCnt=0;
		List<FileSliceInfo> slices=new ArrayList<FileSliceInfo>();
		
		long timestamp=new Date().getTime();
		
		while((data=in.readLine()) != null){
			if(data.equals(ImageEntry.END.toString()))
				break;
			
			details=data.split("\t");
			if(details.length<6)
				throw new IOException("Missing details in file slice entry.");
			
			FileSliceInfo fsi=new FileSliceInfo(
					details[ImageEntryDataIndex.FILE_SLICE_NAME.index()], 
					details[ImageEntryDataIndex.FILE_SLICE_SVR.index()], 
					Long.parseLong(details[ImageEntryDataIndex.FILE_SLICE_LEN.index()]),
					(details[ImageEntryDataIndex.FILE_SLICE_CHKSUM.index()].trim().length()==0?
							null:details[ImageEntryDataIndex.FILE_SLICE_CHKSUM.index()]), 
					Integer.parseInt(details[ImageEntryDataIndex.FILE_SLICE_SEQ.index()]));
			
			segCnt=Integer.parseInt(details[ImageEntryDataIndex.FILE_SLICE_SEGCNT.index()]);
			for(int s=0; s<segCnt; s++){
				data=in.readLine();
				if(data==null)
					throw new IOException("Expecting file slice segments.");
				
				details=data.split("\t");
				if(details.length<4)
					throw new IOException("Missing details in file slice segment entry.");
				
				fsi.addSegment(new FileSliceInfo(
						details[ImageEntryDataIndex.FILE_SLICE_SEG_NAME.index()],
						details[ImageEntryDataIndex.FILE_SLICE_SEG_SVR.index()],
						Long.parseLong(details[ImageEntryDataIndex.FILE_SLICE_SEG_OFFSET.index()]),
						Long.parseLong(details[ImageEntryDataIndex.FILE_SLICE_SEG_LEN.index()]),
						timestamp));
			}
			
			slices.add(fsi);
		}
		
		return slices;
	}
}