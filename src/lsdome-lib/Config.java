// Global configuration variables.
import java.util.*;
import java.io.*;


public class Config {

    private static class ConfigInstance {
        public static Config config = new Config();
    }
    
    public static Config getConfig() {
        return ConfigInstance.config;
    }
    
    private Config()
    {
        FADECANDY_HOST = "127.0.0.1";
        
        Properties properties = new Properties();
        try {
        
            properties.load(new FileInputStream("config.properties"));
            FADECANDY_HOST = properties.getProperty("opcHost");
        } catch (IOException e) {
            // do nothing. Use defaults. Meh.
        }
        System.out.println(System.getProperty("user.dir"));
        System.out.println(FADECANDY_HOST);
    }
    
    // Debug mode.
    static final boolean DEBUG = false;

    // Fadecandy server.
    //public String FADECANDY_HOST = "127.0.0.1";
    public String FADECANDY_HOST;

    // The index of dead pixels that have been bypassed in the dome. Although
    // still rendered in software, these pixels will be skipped when sending to
    // the fadecandy. Must be sorted in ascending order.
    static final int[] DEAD_PIXELS = {};

    // Size of single panel's pixel grid.
    static final int PANEL_SIZE = 15;

    // Panel configuration.
    //static final PanelLayout PANEL_LAYOUT = PanelLayout._2;
    static final PanelLayout PANEL_LAYOUT = PanelLayout._13;
    //static final PanelLayout PANEL_LAYOUT = PanelLayout._24;

    // If true, size the panels as if they were part of a larger layout.
    static final boolean PARTIAL_LAYOUT = false;
    // The larger layout. Ignored if PARTIAL_LAYOUT is false.
    static final PanelLayout FULL_PANEL_LAYOUT = PanelLayout._24;
}
