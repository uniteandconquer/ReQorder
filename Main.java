package reqorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ResourceBundle;
import oshi.SystemInfo;

public class Main 
{  
    protected static ResourceBundle BUNDLE;
    
     public static void main(String args[])
    {    
//        Locale locale = new Locale("nl", "NL");
//        Locale.setDefault(locale);
//        JOptionPane.setDefaultLocale(locale);
//        BUNDLE = ResourceBundle.getBundle("i18n/Language",locale); 
        BUNDLE = ResourceBundle.getBundle("i18n/Language"); 
        
        File dir = new File(System.getProperty("user.home"));
        if(!dir.exists())
            dir.mkdir();
        
        //Singleton implementation
        if(lockInstance(dir + "/reqorder_instance.lock"))
        {
            if(args.length == 0)
            {
                String message = BUNDLE.getString("invalidLaunch");
                JOptionPane.showMessageDialog(null, message);
                System.out.println(message);
                System.exit(0);
            }
            switch (args[0])
            {
                case "-setup":
                    if(new SystemInfo().getOperatingSystem().getFamily().equals("Windows"))
                    {
                         if(WinSetup.SetupLancher())
                            JOptionPane.showMessageDialog(null, BUNDLE.getString("setupComplete"));
                    }                   
                    else                        
                        JOptionPane.showMessageDialog(null, BUNDLE.getString("setupFailed"));
                    System.exit(0);
                    break;
                case "-cli":
                    CheckLaunch();
                    //Implementation pending
                    //ATTENTION: ADD CONSOLE FEEDBACK
                    BackgroundService bgs = new BackgroundService(false);
                    break;
                case "-gui":
                    CheckLaunch();
                    bgs = new BackgroundService(true);
                    break;
                default:
                    String message = BUNDLE.getString("invalidCommandLine");
                    //blocks user from starting the jar without using the proper commandline args
                    JOptionPane.showMessageDialog(null, message + args[0]);
                    System.out.println(message + args[0]);
                    break;
            }
        }
        else
        {
            //If another instance of app is running, show a message dialog for x seconds, then close the dialog and app, or close app on close of dialog
            JOptionPane jOptionPane = new JOptionPane(
                    Utilities.AllignCenterHTML(BUNDLE.getString("alreadyRunningMessage")), 
                    JOptionPane.INFORMATION_MESSAGE);
            
            JDialog dlg = jOptionPane.createDialog(BUNDLE.getString("alreadyRunningTitle"));
            dlg.addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentShown(ComponentEvent e)
                {
                    super.componentShown(e);
                    final Timer t = new Timer (12000, (ActionEvent e1) ->
                    {
                        dlg.dispose();
                    });
                    t.start();                    
                }
            });
            dlg.setVisible(true);
            System.exit(0);
        }        
    }   
     
     private static void CheckLaunch()
     {
         if(System.getProperty("user.dir").endsWith("NetBeansProjects\\reQorder"))
             return;
         //only allow launch from main reqorder folder, this to ensure database folder and bin folder are always accessible
         //when user clicks on .bat or .vsb file in bin folder the app will execute, but user.dir will be the bin folder, we want to avoid that
         if(!System.getProperty("user.dir").endsWith("ReQorder"))
        {
            if(new SystemInfo().getOperatingSystem().getFamily().equals("Windows"))
            {
                String message = BUNDLE.getString("invalidLaunchWin");
                JOptionPane.showMessageDialog(null, Utilities.AllignCenterHTML(message));
                System.out.println( message);
            }
            else
            {
                String message = BUNDLE.getString("invalidLaunchOther");
                System.out.println(message);                
                JOptionPane.showMessageDialog(null, Utilities.AllignCenterHTML(message));
            }                
            System.exit(0);
        }
         
     }
   
    private static boolean lockInstance(final String lockFile)
    {
        try
        {
            final File file = new File(lockFile);
            final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw"); //rw = read/write mode
            final FileLock fileLock = randomAccessFile.getChannel().tryLock();
            if (fileLock != null)
            {
                Runtime.getRuntime().addShutdownHook(new Thread()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            fileLock.release();
                            randomAccessFile.close();
                            file.delete();
                        }
                        catch (IOException e)
                        {
                            JOptionPane.showMessageDialog(null, BUNDLE.getString("removeLockFail") 
                                    + lockFile + "\n" +  e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
                return true;
            }
        }
        catch (IOException e)
        {
                JOptionPane.showMessageDialog(null, 
                        BUNDLE.getString("lockFileError") + lockFile + "\n" +  e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }
     
}
