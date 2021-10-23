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
import oshi.SystemInfo;

public class Main 
{          
     public static void main(String args[])
    {          
        File dir = new File(System.getProperty("user.home"));
        if(!dir.exists())
            dir.mkdir();
        
        //Singleton implementation
        if(lockInstance(dir + "/reqorder_instance.lock"))
        {
            if(args.length == 0)
            {
                String message = "Invalid lauch. Please start the program using the launcher.\n"
                        + "'ReQorder.exe' for Windows, 'launch.sh' for linux and Mac.";
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
                            JOptionPane.showMessageDialog(null, "Setup complete. You can run the program with the 'ReQorder' link file.");
                    }                   
                    else                        
                        JOptionPane.showMessageDialog(null, "Setup failed");
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
                    //blocks user from starting the jar without using the proper commandline args
                    JOptionPane.showMessageDialog(null, "Invalid command line argument: " + args[0]);
                    System.out.println("Invalid command line argument: " + args[0]);
                    break;
            }
        }
        else
        {
            //If another instance of app is running, show a message dialog for x seconds, then close the dialog and app, or close app on close of dialog
            JOptionPane jOptionPane = new JOptionPane(
                    Utilities.AllignCenterHTML("ReQorder is already running on this machine<br/>You can open the UI by double clicking the ReQorder icon in the system tray"), 
                    JOptionPane.INFORMATION_MESSAGE);
            
            JDialog dlg = jOptionPane.createDialog("Already running");
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
                JOptionPane.showMessageDialog(null, Utilities.AllignCenterHTML(
                     "Invalid launch detected.<br/><br/>Use the 'ReQorder' launcher found in the ReQorder folder"));
                System.out.println( "Invalid launch detected.\nUse the 'ReQorder' launcher found in the ReQorder folder");
            }
            else
            {
                System.out.println( "Invalid launch detected.\nUse the 'launch.sh' file found in the ReQorder folder");                
                JOptionPane.showMessageDialog(null, Utilities.AllignCenterHTML(
                     "Invalid launch detected.<br/><br/>Use the 'launch.sh' file found in the ReQorder folder"));
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
                            JOptionPane.showMessageDialog(null, "Unable to remove lock file: " + lockFile + "\n" +  e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
                return true;
            }
        }
        catch (IOException e)
        {
                JOptionPane.showMessageDialog(null, 
                        "Unable to create and/or lock file: "  + lockFile + "\n" +  e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }
     
}
