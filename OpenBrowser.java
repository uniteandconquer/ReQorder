package reqorder;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public class OpenBrowser
{
    public static void OpenLink(URI uri) throws IOException
    {
        String myOS = System.getProperty("os.name").toLowerCase();

        if (Desktop.isDesktopSupported())
        {
            // Probably Windows
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(uri);
        }
        else
        { 
            // Definitely Non-windows
            Runtime runtime = Runtime.getRuntime();
            if (myOS.contains("mac"))
            { 
                // Apple
                runtime.exec("open " + uri.toString());
            }
            else if (myOS.contains("nix") || myOS.contains("nux"))
            { 
                // Linux
                runtime.exec("xdg-open " + uri.toString());
            }
            else
            {
                OUT("unable to launch a browser in your OS");
            }
        }
    }

    private static void OUT(String str)
    {
        System.out.println(str);
    }
}
