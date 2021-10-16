package reqorder;

/**Used for inserting multiple items into the same row in one sql statement*/
public class KeyItemPair
{
    public String item;
    public String itemValue;
    public String key;
    public String keyValue;
    
    public KeyItemPair(String item,String itemValue,String key,String keyValue)
    {
        this.item = item;
        this.itemValue = itemValue;
        this.key = key;
        this.keyValue = keyValue;
    }
}