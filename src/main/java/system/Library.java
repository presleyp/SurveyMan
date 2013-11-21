package system;

import java.io.*;
import java.util.Properties;
import org.apache.log4j.Logger;

public class Library {

    public enum JobStatus { CANCELLED, INTERRUPTED, COMPLETED; }

    public Properties props = new Properties();
    private static final Logger LOGGER = Logger.getLogger("system");

    public static final String fileSep = File.separator;
    public static final String DIR = System.getProperty("user.home") + fileSep + "surveyman";
    public static final String CONFIG = DIR + fileSep + "config";
    public static final String OUTDIR = "output";
    public static final String PARAMS = DIR + fileSep + "params.properties";
    public static final String TIME = String.valueOf(System.currentTimeMillis());
    public static final String STATEDATADIR = String.format("%1$s%2$sdata", DIR, fileSep);

    public Library() {
        try {
            File dir = new File(DIR);
            if (! new File(OUTDIR).exists())
                new File(OUTDIR).mkdir();
            if (! (dir.exists() && new File(CONFIG).exists())) {
                LOGGER.fatal("ERROR: You have not yet set up the surveyman directory nor AWS keys. Please see the project website for instructions.");
            } else {
                if (! new File(DIR + fileSep + ".metadata").exists())
                    new File(DIR + fileSep + ".metadata").mkdir();
                if (! new File(STATEDATADIR).exists())
                    new File(STATEDATADIR).mkdir();
                if (! new File(DIR + fileSep + ".unfinished").exists())
                    new File(DIR + fileSep + ".unfinished").createNewFile();
                // load up the properties file
                this.props.load(new BufferedReader(new FileReader(this.PARAMS)));
                // make sure we have both names for the access keys in the config file
                Properties config = new Properties();
                config.load(new FileInputStream(CONFIG));
                if (config.containsKey("AWSAccessKeyId") && config.containsKey("AWSSecretKey")) {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(CONFIG, true));
                    bw.newLine();
                    if (! config.containsKey("access_key")) {
                        bw.write("access_key=" + config.getProperty("AWSAccessKeyId"));
                        bw.newLine();
                    }
                    if (! config.containsKey("secret_key")) {
                        bw.write("secret_key=" + config.getProperty("AWSSecretKey"));
                        bw.newLine();
                    }
                    bw.close();
                } else if (config.containsKey("access_key") && config.containsKey("secret_key")) {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(CONFIG, true));
                    bw.newLine();
                    if (! config.containsKey("AWSAccessKeyId")) {
                        bw.write("AWSAccessKeyId="+config.getProperty("access_key"));
                        bw.newLine();
                    }
                    if (! config.containsKey("AWSSecretKey")) {
                        bw.write("AWSSecretKey="+config.getProperty("secret_key"));
                        bw.newLine();
                    }
                    bw.close();
                }
            }
        } catch (IOException ex) {
            LOGGER.fatal(ex);
        }
    }

}