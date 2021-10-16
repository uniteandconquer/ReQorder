package reqorder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import mslinks.ShellLink;
import mslinks.ShellLinkException;
import mslinks.ShellLinkHelper;
import mslinks.ShellLinkHelper.Options;

/**This class creates 2 batch files and a windows shortcut link <br><br>
 *We want to limit the execution of the jar file to only run with certain command line arguments and VM commands.<br>
The first batch file is the shell.bat, this file would open a console, so we open that file with the vbs file which hides the console.<br>
Next we create a Windows shortcut link that links to the vbs file*/
public class WinSetup
{
    public static boolean SetupLancher()
    {
        try
        {   
            //create the batch that will run the reqorder.jar + vm options and args
            //disabling attachment of java agents to avoid heap dumps by unauthorized actors
            String batString = "java -XX:+DisableAttachMechanism -jar ";
            batString += "\"" + System.getProperty("user.dir") + "\\bin\\reqorder.jar\" -gui";
            File shellBat = new File(System.getProperty("user.dir") + "/bin/shell.bat");
            byte[] batBytes = batString.getBytes(StandardCharsets.UTF_8);
            if (shellBat.exists())
                shellBat.delete();
            Files.write(shellBat.toPath(), batBytes); 
            //hides the file, the average user has no use for this file and double clicking it will throw an error for them
            Files.setAttribute(shellBat.toPath(), "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
            
            //create the file that runs the batch file, but hides the console window
            String vbsString = "Set WshShell = CreateObject(\"WScript.Shell\") \n"
                    + "WshShell.Run chr(34) & ";
            vbsString += "\"" + System.getProperty("user.dir") + "\\bin\\shell.bat\"";
            vbsString += " & Chr(34), 0\nSet WshShell = Nothing";

            File shellVbs = new File(System.getProperty("user.dir") + "/bin/shell.vbs");
            byte[] vbsBytes = vbsString.getBytes(StandardCharsets.UTF_8);
            if (shellVbs.exists())
                shellVbs.delete();
            Files.write(shellVbs.toPath(), vbsBytes);  
            //hides the file, the average user has no use for this file and double clicking it will throw an error for them
            Files.setAttribute(shellVbs.toPath(), "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);

            //create the windows shorcut (.lnk) file that runs the .vbs file
            var sl = new ShellLink()
                    .setWorkingDir(System.getProperty("user.dir"))
                    .setIconLocation(System.getProperty("user.dir") + "\\bin\\icon.dll");
            sl.getHeader().setIconIndex(0);
            sl.getConsoleData()
                    .setFont(mslinks.extra.ConsoleData.Font.Consolas)
                    .setFontSize(24)
                    .setTextColor(5);

            Path targetPath = shellVbs.toPath();
            String root = targetPath.getRoot().toString();
            String path = targetPath.subpath(0, targetPath.getNameCount()).toString();

            new ShellLinkHelper(sl)
                    .setLocalTarget(root, path, Options.ForceTypeFile)
                    .saveTo("ReQorder.lnk");
            
            return true;
            
        }
        catch (IOException | ShellLinkException e)
        {
            JOptionPane.showMessageDialog(null, e.toString());
            System.out.println(e.toString());
            return false;
        }      
    }
}
