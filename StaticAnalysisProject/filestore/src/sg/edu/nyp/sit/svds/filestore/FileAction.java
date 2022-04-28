package sg.edu.nyp.sit.svds.filestore;

import java.io.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sg.edu.nyp.sit.svds.Resources;
import sg.edu.nyp.sit.svds.SliceDigest;
import sg.edu.nyp.sit.svds.metadata.SliceDigestInfo;

public class FileAction {
	public static final long serialVersionUID = 1L;

	private static final Log LOG = LogFactory.getLog(FileAction.class);

	private String fileRootPath = null;

	public FileAction(String path) {
		fileRootPath = path;
	}

	public InputStream getFile(String sliceName, int blkSize, byte[] checksum) {
		try {
			File f = new File(fileRootPath + "/" + sliceName);
			if (!f.exists()) {
				return null;
			}

			if (checksum != null) {
				File fChk = new File(fileRootPath + "/" + sliceName + ".chk");
				System.arraycopy(
						SliceDigest.combineBlkHashes(fChk, 0, blkSize, -1), 0,
						checksum, 0, Resources.HASH_BIN_LEN);
			}

			/*
			 * FileInputStream in=new FileInputStream(f); byte[] data=new
			 * byte[(int) f.length()]; in.read(data);
			 * System.out.print("Get data: "); Resources.printByteArray(data);
			 * in.close();
			 */

			return new FileInputStream(f);
		} catch (Exception ex) {
			LOG.error(ex);
			ex.printStackTrace();
			return null;
		}
	}

	public InputStream getFile(String sliceName, long offset, int len,
			int blkSize, byte[] checksum) {

		RandomAccessFile acf = null;
		File f = null;
		ByteArrayInputStream in = null;
		
		try {

			f = new File(fileRootPath + "/" + sliceName);
			if (!f.exists())
				return null;

			byte[] data = null;

			acf = new RandomAccessFile(f, "r");
			acf.seek(offset);

			int readSize = -1;
			if (len == 0) {
				data = new byte[(new Long(f.length() - offset)).intValue()];
				acf.read(data);
				in = new ByteArrayInputStream(data);
			} else {
				data = new byte[len];
				readSize = acf.read(data);

				if (readSize == -1)
					in = new ByteArrayInputStream(new byte[0]); // creates a
																// empty stream
				else {
					in = new ByteArrayInputStream(data, 0, readSize);
				}
			}

			if (checksum != null) {
				System.arraycopy(SliceDigest.combineBlkHashes(new File(
						fileRootPath + "/" + sliceName + ".chk"), offset,
						blkSize, len), 0, checksum, 0, Resources.HASH_BIN_LEN);
			}

			acf.close();
			
		} catch (Exception ex) {
			LOG.error(ex);
			ex.printStackTrace();
			return null;
		} 
		return in;
	}

	// for appending to file slice or writing to file slice for the first time
	public void storeFile(String sliceName, InputStream in, String checksum,
			String key, int blkSize) throws Exception {
		File f = new File(fileRootPath + "/" + sliceName);
		if (!f.exists())
			f.createNewFile();

		File fChk = new File(fileRootPath + "/" + sliceName + ".chk");

		File tmp = null;
		if (checksum != null) {
			if (!fChk.exists())
				fChk.createNewFile();

			tmp = verifyChecksum(in, checksum, key, blkSize, f.length(), fChk);
			in = new FileInputStream(tmp);
		} else if (fChk.exists())
			fChk.delete();

		storeFile(in, f.length(), f);

		if (tmp != null)
			tmp.delete();

	}

	// for random write to file slice
	public void storeFile(String sliceName, InputStream in, long offset,
			String checksum, String key, int blkSize) throws Exception {
		File f = new File(fileRootPath + "/" + sliceName);
		if (!f.exists())
			f.createNewFile();

		File fChk = new File(fileRootPath + "/" + sliceName + ".chk");

		File tmp = null;
		if (checksum != null) {
			if (!fChk.exists())
				fChk.createNewFile();

			tmp = verifyChecksum(in, checksum, key, blkSize, offset, fChk);
			in = new FileInputStream(tmp);
		} else if (fChk.exists())
			fChk.delete();

		storeFile(in, offset, f);

		if (tmp != null)
			tmp.delete();
	}

	private File verifyChecksum(InputStream in, String checksum, String key,
			int blkSize, long offset, File fChk) throws Exception {
		SliceDigest md = new SliceDigest(new SliceDigestInfo(blkSize, key));
		md.setOffset(offset, null);

		// creates a temp file to store the contents as going thru the input
		// stream
		File tmp = File.createTempFile(checksum, null);
		FileOutputStream out = new FileOutputStream(tmp);

		byte[] data = new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		int total = 0;
		while ((len = in.read(data)) != -1) {
			total += len;
			md.update(data, 0, len);
			out.write(data, 0, len);
		}
		// data=null;
		in.close();
		out.flush();
		out.close();
		md.finalizeDigest();

		// System.out.println("[FS] Slice (" + fChk.getName()+ ") offset=" +
		// offset+", total="
		// +total+", key="+key+", recChecksum="+checksum+", calChecksum="
		// +Resources.convertToHex(md.getSliceChecksum())+"\nData: " +
		// Resources.concatByteArray(data, 0, total));

		if (!checksum.equals(Resources.convertToHex(md.getSliceChecksum()))) {
			tmp.delete();
			throw new StreamCorruptedException();
		}

		// creates or update the checksum file is it is not null
		if (fChk != null) {
			md.updBlkHashes(fChk);
		}

		return tmp;
	}

	private void storeFile(InputStream in, long offset, File f)
			throws Exception {
		// System.out.println(f.getAbsolutePath());

		RandomAccessFile out = new RandomAccessFile(f, "rw");
		out.seek(offset);

		// System.out.print("Write to slice from offset " + offset + ": ");

		byte[] data = new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		while ((len = in.read(data)) != -1) {
			out.write(data, 0, len);
			// Resources.printByteArray(data, 0, len);
		}
		data = null;
		in.close();
		out.close();
	}

	public void delFile(String sliceName) throws Exception {
		File f = new File(fileRootPath + "/" + sliceName);
		if (f.exists())
			f.delete();

		f = null;

		// deletes the checksum file too
		f = new File(fileRootPath + "/" + sliceName + ".chk");
		if (f.exists())
			f.delete();

		f = null;

	}

	public void storeFileHashes(String sliceName, InputStream in)
			throws Exception {
		RandomAccessFile f = new RandomAccessFile(fileRootPath + "/"
				+ sliceName + ".chk", "rwd");

		// truncate the file
		f.setLength(0);

		byte[] tmp = new byte[Resources.DEF_BUFFER_SIZE];
		int len;
		while ((len = in.read(tmp)) != -1) {
			f.write(tmp, 0, len);
		}
		tmp = null;

		in.close();
		f.close();
		f = null;
	}

	public InputStream getFileHashes(String sliceName) {
		return getFile(sliceName + ".chk", 0, null);
	}
}
