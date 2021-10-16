package reqorder;

public class AlertItem
{
    public String subject;
    public String message;
    public long timestamp;
    public boolean read;
    
    public AlertItem(String subject,String message,long timestamp,boolean read)
    {
        this.subject = subject;
        this.message = message;
        this.timestamp = timestamp;
        this.read = read;
    }
    
    @Override
    public String toString()
    {
        return subject;
    }
    
}
