package io.qhm.grpc.plugin;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigUtil {

	private static Logger LOG = LoggerFactory.getLogger(ConfigUtil.class);
	
	private static final Properties COMMON_CONFIG = new Properties();
	private static final Properties CONFIG = new Properties();

	static {
		loadConfig();
	}

	private static void loadConfig() {
		InputStream in = null;
		try {
			if (System.getProperty("os.name").toLowerCase().startsWith("win")){
				in = new FileInputStream("d:\\grpc-consul-plugin.properties");
			}else{
				in = new FileInputStream("/etc/grpc-consul-plugin.properties");
			}
			LOG.info("load grpc-consul-plugin.propertis...");
			COMMON_CONFIG.load(in);
		} catch (IOException e) {
			LOG.error("can't load grpc-consul-plugin.propertis");
			e.printStackTrace();
		} finally {
			closeQuietly(in);
		}
	}
	
	/**
	 * Get config with default value.
	 * This is different from the old one, which specific configs is prior to common ones.
	 * @param key
	 * @param defaultValue
	 * @return The config value.
	 */
	public static final String getConfig(String key, String defaultValue) {
		// Specific is prior for different environment.
		if (CONFIG.containsKey(key)) {
			return CONFIG.getProperty(key);
		}
		
		if (COMMON_CONFIG.containsKey(key)) {
			return COMMON_CONFIG.getProperty(key);
		}
		
		return defaultValue;
	}
	
	public static final String getConfig(String key) {
		return getConfig(key, "");
	}
	
	public static void closeQuietly(InputStream input) {
		closeQuietly((Closeable) input);
	}

	public static void closeQuietly(Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (IOException arg1) {
			;
		}

	}
}
