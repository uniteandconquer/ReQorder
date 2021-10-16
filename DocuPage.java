package reqorder;

import java.net.URL;

/**Used for storing the users browsing history in the documentation, enabling user to go back to previous page*/
public class DocuPage
{
    public URL url;
    public int scrollValue;
    
    public DocuPage(URL url, int scrollValue)
    {
        this.url = url;
        this.scrollValue = scrollValue;
    }
}
