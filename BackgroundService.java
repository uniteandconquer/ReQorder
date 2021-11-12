package reqorder;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class BackgroundService 
{    
    protected DatabaseManager dbManager;
    protected static GUI GUI;
    private Timer popupTimer;
    private TimerTask popupTask;
    private boolean timerRunning;
    private SystemTray tray;
    private TrayIcon trayIcon;
    protected char [] password;
    private static final Logger logger = Logger.getLogger("debug_log");  
    private FileHandler fileHandler;  
    protected static final String BUILDVERSION = "ReQorder 1.0-beta.5";    
    
    public BackgroundService(boolean GUI_enabled)
    {  
        try
        {
            // This block configures the logger with handler and formatter  
            fileHandler = new FileHandler(System.getProperty("user.dir") + "/log.txt");
            logger.addHandler(fileHandler);
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);
            logger.setUseParentHandlers(false);//disable console output
        }
        catch (IOException | SecurityException e)
        {
            AppendLog(e);
        }
        
        SetTrayIcon();        
        
        if(GUI_enabled)
        {      
            dbManager = new DatabaseManager();
            GUI = new GUI(this);      
            popupTimer = new Timer();
        }        
        else
        {
            //CLI version
            //ATTENTION: ADD CONSOLE FEEDBACK
             dbManager = new DatabaseManager();
             System.out.println("Starting new database manager.\nFurther implementation pending.\n\nExiting program...");
        }   
    }     

    private void SetTrayIcon()
    {
        if (SystemTray.isSupported())
        {
            URL imageURL = BackgroundService.class.getClassLoader().getResource("Images/icon.png");
            Image icon = Toolkit.getDefaultToolkit().getImage(imageURL);
            final PopupMenu popup = new PopupMenu();
            trayIcon = new TrayIcon(icon, "ReQorder", popup);
            trayIcon.setImageAutoSize(true);
            tray = SystemTray.getSystemTray();
            
            MenuItem guiItem = new MenuItem("Open UI");
            MenuItem exitItem = new MenuItem("Exit");
            guiItem.addActionListener((ActionEvent e) ->{SetGUIEnabled(true);});            
            trayIcon.addActionListener((ActionEvent e) ->{SetGUIEnabled(true);});//double click action
            exitItem.addActionListener((ActionEvent e) ->{GUI.exitButtonActionPerformed(null);});//checks for reqording
            popup.add(guiItem);
            popup.add(exitItem);
            trayIcon.setPopupMenu(popup);           
        }
    }
    
    protected void BackupAndExit()
    {
        if(GUI.mainToolbar.isVisible() && dbManager.backupEnabled)
        {
            //this will check for inaccessible db's, if none are found 
            //it will automatically backup the account
            dbManager.CheckDbFiles();
        }
        System.exit(0);
    }
    
    private void SetTrayIconActive(boolean isActive)
    { 
        if(tray == null)
            return;
        
        try
        {
            if(isActive)
                tray.add(trayIcon);
            else
                tray.remove(trayIcon);
        }
        catch (AWTException e)
        {
            System.out.println("TrayIcon could not be added.");
        }
    }

    //Using a seperate method in order to inform the user that the app is running in background on JFrame closure
    public void SetGUIEnabled(boolean enabled)
    {
        if(enabled)
        {
            GUI.setVisible(true);
            GUI.requestFocus(); 
            SetTrayIconActive(false);
        }
        else
        {
            //if reqording run in background, otherwise exit the program
            if(!GUI.REQORDING)
                BackupAndExit();
            
            if(!SystemTray.isSupported())
            {
                GUI.setState(Frame.ICONIFIED);
                return;
            }
            SetTrayIconActive(true);
            //Decided not to dispose of GUI on close, the resources saved do not warrant losing all the instance's values
            GUI.setVisible(false);                
            GUI.trayPopup.pack();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            GUI.trayPopup.setLocation(screenSize.width - GUI.trayPopup.getBounds().width -35,
                    screenSize.height - GUI.trayPopup.getBounds().height - 50);
                                        
            if(timerRunning)
                return;
            
            GUI.trayPopup.setVisible(true);  
            
            popupTask = new TimerTask()
            {                
                @Override
                public void run()
                {
                    GUI.trayPopup.setVisible(false);
                    timerRunning = false;
                }
            };    
            timerRunning = true;
            popupTimer.schedule(popupTask, 12000); 
        }
    }  
    
    public static void AppendLog(Exception e)
    {
        //comment 'setUseParentHandlers(false);'  in constructor
        //to enable console output for errors       
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        
        logger.info(sw.toString());
        
        if(GUI != null)
            GUI.logPanel.AppendLog(e);
    }
    
    public static void AppendLog(String e)
    {        
        logger.info(e);        
        
        if(GUI != null)
            GUI.logPanel.AppendLog(e);
    }
    
}
