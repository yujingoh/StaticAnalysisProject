package sg.edu.nyp.sit.svds.filestore;

import java.io.FileInputStream;
import java.util.Properties;

public class SliceStoreProperties {		
	public static final long serialVersionUID = 2L;
	
	public enum PropName{
		MASTER_PINGBACK_INTERVAL ("master.pingback.interval"),
		REQ_VERIFY ("request.verification"),
		SLICESTORE_KEY ("slicestore.key");
		
		private String name;
		PropName(String name){ this.name=name; }
		public String value(){ return name; }
	}
	
	private static Properties prop=new Properties();
	
	public static void load(String path) throws Exception{
		FileInputStream in=new FileInputStream(path);
		prop.load(in);
		in.close();
		in=null;
	}
	
	public static void load(java.io.File f) throws Exception{
		FileInputStream in=new FileInputStream(f);
		prop.load(in);
		in.close();
		in=null;
	}
	
	public static String getString(String propName){
		return prop.get(propName).toString();
	}
	
	public static String getString(PropName prop){
		return getString(prop.value());
	}
	
	public static int getInt(String propName){
		if(!prop.containsKey(propName))
			return 0;
		
		return Integer.parseInt(prop.get(propName).toString());
	}
	
	public static int getInt(PropName prop){
		return getInt(prop.value());
	}
	
	public static long getLong(String propName){
		if(!prop.containsKey(propName))
			return 0;
		
		return Long.parseLong(prop.get(propName).toString());
	}
	
	public static long getLong(PropName prop){
		return getLong(prop.value());
	}
	
	public static boolean getBool(String propName){
		String tmp=prop.get(propName).toString();
		if(tmp.equalsIgnoreCase("off") || tmp.equalsIgnoreCase("0"))
			return false;
		else
			return true;
	}
	
	public static boolean getBool(PropName prop){
		return getBool(prop.value());
	}
	
	public static void set(String propName, Object value){
		prop.put(propName, value);
	}
	
	public static void set(PropName prop, Object value){
		set(prop.value(), value);
	}
	
	public static boolean exist(String propName){
		return prop.containsKey(propName);
	}
	
	public static boolean exist(PropName prop){
		return exist(prop.value());
	}
	
	public static void remove(String propName){
		prop.remove(propName);
	}
	
	public static void remove(PropName prop){
		remove(prop.value());
	}
}
