package reqorder;

import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class DatabaseManager 
{    
    private Connection dbConnection;
    private Connection propertiesConnection;
    private Statement statement;
    private ResultSet rs;
    private String sql;
    private JSONObject jSONObject;
    private String jsonString;
    
    private final SystemInfo systemInfo;
    private List<NetworkIF> interfaces;
    private final CentralProcessor processor;
    private final OperatingSystem os;
    private final Sensors sensors;
    private double cpu_usage;
    private double qortalRAM;
    private long [] oldTicks;
    
    protected static String dbFolderOS;
    private final int myProcessID;
    protected final String myOS;
    protected String blockChainFolder;
    protected static ArrayList<String> dbFiles;
    protected static ArrayList<String> encryptedFiles;
    protected static boolean logDbEntries;
    protected boolean backupEnabled = true;
    private String database;
    protected int retries;
    protected String customIP = "localhost";
    protected String customPort = "12391";
    protected String socket = customIP + ":" + customPort;
    
    /** 
     * using char[]  for possible future implementation of clearing password from heap<br>
     * to implement that we'd need to encrypt these then decrypt for every use in a separate<br>
     * char[] and clear that one after every use
     */
    protected static char[] dbPassword;
    protected static char[] reqorderPassword;
    private ArrayList<AlertItem> alertsPool;
    
    public DatabaseManager()
    {    
        systemInfo = new SystemInfo();
        os = systemInfo.getOperatingSystem();
        interfaces = new LinkedList<>();
        interfaces = systemInfo.getHardware().getNetworkIFs();
        myProcessID = systemInfo.getOperatingSystem().getProcessId();
        processor = systemInfo.getHardware().getProcessor();
        sensors = systemInfo.getHardware().getSensors();   
        myOS = systemInfo.getOperatingSystem().getFamily();
        oldTicks = processor.getSystemCpuLoadTicks();
        
        CreateDatabasesFolder();     
    } 
    
    //this method only creates a new folder if it is not present
    private  void CreateDatabasesFolder()
    {        
        dbFolderOS = System.getProperty("user.dir") + "/databases";
        
        if(Files.isDirectory(Paths.get(System.getProperty("user.dir") + "/databases")))
            return;
        
        File folder = new File(System.getProperty("user.dir") + "/databases");
        folder.mkdir(); 
    }  

    protected void FindDbFiles()
    {
         File folder = new File(dbFolderOS);
        dbFiles = new ArrayList<>();
        encryptedFiles = new ArrayList<>();
        //this file is in the bin folder (not in dbFolderOS), should always exist, should not be in dbfiles list
        encryptedFiles.add("dba");
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles)
            if (file.isFile())
                if (file.getName().endsWith(".mv.db"))
                {
                    String name = file.getName().split("\\.",2)[0];
                    dbFiles.add(name);
                    if(ConnectionDB.IsEncrypted(name))
                        encryptedFiles.add(name);
                }        
    }
    
    protected void InsertDbFiles()
    {               
        try(Connection c = ConnectionDB.getConnection("properties"))
        {            
            if(!TableExists("databases", c))
                CreateTable(new String[]{"databases","database","varchar(50)"}, c);
            
            ExecuteUpdate("delete from databases", c);
            //no need to iterate encrypted files list, they are also in this list
            dbFiles.stream().filter(file -> !(file.startsWith("properties"))).forEachOrdered(file ->
            {
                InsertIntoDB(new String[]{"databases","database",Utilities.SingleQuotedString(file)},c);
            });
            c.close();
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    public ArrayList<String> GetDbFiles()
    {
        return dbFiles;
    }
    
    /*Gets called when user authentication has failed. If user decides to create a new account
      we want to make sure all encrypted databases get moved out of the databases folder*/
    protected void MoveInaccessibleFiles(boolean saveFiles)
    {
        try
        {
            File dir = new File(System.getProperty("user.dir") + "/inaccessible/" + Utilities.DateFormatPath(System.currentTimeMillis()));
            if (!dir.isDirectory())
                dir.mkdir();
            
            ArrayList<String> removedFiles = new ArrayList<>();

            for(String db : encryptedFiles)
            {
                //don't move or delete db access file
                if(db.equals("dba"))
                    continue;
                
                File file = new File(dbFolderOS + "/" + db + ".mv.db");
                if(file.exists())
                {
                    if(saveFiles)
                    {
                        Path source = file.toPath();
                        String newFile = dir.getPath() + "/" + file.getName(); 
                        Files.move(source, source.resolveSibling(newFile));     
                        BackgroundService.AppendLog("Moving inaccessible database to : "  + newFile);                     
                    }
                    else                        
                        BackgroundService.AppendLog("Deleting inaccessible database  : "  + file.getName()); 
                    
                    if(file.exists())
                        file.delete();
                    removedFiles.add(db);
                }
            }
            removedFiles.forEach(db ->
            {
                encryptedFiles.remove(db);
                dbFiles.remove(db);
            }); 
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    protected void MoveInaccessibleFile(String fileName)
    {
        String[] split = Main.BUNDLE.getString("dbInaccessible").split("%%");
        boolean keepFile = JOptionPane.showConfirmDialog(BackgroundService.GUI, 
                        Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], fileName)), 
                        Main.BUNDLE.getString("dbInaccessibleTitle"), 
                        JOptionPane.YES_NO_OPTION, 
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        try
        {
            File dir = new File(System.getProperty("user.dir") + "/inaccessible/" + Utilities.DateFormatPath(System.currentTimeMillis()));
            if (!dir.isDirectory())
                dir.mkdir();

            File file = new File(dbFolderOS + "/" + fileName + ".mv.db");
            Path source = file.toPath();
            String newFile = dir.getPath() + "/" + file.getName();
            if(keepFile)
                Files.move(source, source.resolveSibling(newFile));
            if(file.exists())
                file.delete();
            
            dbFiles.remove(fileName);
            encryptedFiles.remove(fileName);            
            
            BackgroundService.GUI.PopulateDatabasesTree();
            
            if(keepFile)
                JOptionPane.showMessageDialog(BackgroundService.GUI, 
                    Utilities.AllignCenterHTML(String.format("'%s' " + Main.BUNDLE.getString("dbMoved") + "<br/>%s",fileName,newFile)),
                    Main.BUNDLE.getString("dbMovedTitle"), JOptionPane.WARNING_MESSAGE);
            else
                JOptionPane.showMessageDialog(BackgroundService.GUI, 
                    Utilities.AllignCenterHTML(String.format("'%s' " + Main.BUNDLE.getString("dbDeleted"),fileName)),
                    Main.BUNDLE.getString("dbDeletedTitle"), JOptionPane.WARNING_MESSAGE);
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    /**Tries to connect to every encrypted db found, any database that fails to
     * connect will be moved. <br>
     * If no inaccessible databases were found, AccountBackup() gets called,
     * which will check the accessibility<br>
     * of properties and backup the account if it's accessible and user has
     * opted for auto backup.<br>
     * This method gets called after login complete and after FindDbFiles
     */
    protected void CheckDbFiles()
    {        
        ArrayList<String> removeFiles = new ArrayList<>();
        
        //dba and properties files are in this list but should not be moved or
        //checked (which will create a new database in CanConnect)
        encryptedFiles.stream().filter(db -> 
                !(db.equals("dba"))).filter(db -> 
                !(db.equals("properties"))).filter(db -> 
                        (!ConnectionDB.CanConnect(db, dbPassword))).forEachOrdered(db ->
        {
            removeFiles.add(db);
        });
        removeFiles.forEach(file ->
        {
            MoveInaccessibleFile(file);
        });  
        
        //if login was successfull and all databases were accessible, make a backup of auth files and databases
        if(removeFiles.isEmpty())
            AccountBackup();        
    }
    
    /**This function applies all updated features to existing databases and properties file*/
    protected void CheckCompatibility()
    {        
        for(String dbFile : dbFiles)
        {
            try(Connection connection = ConnectionDB.getConnection(dbFile))
            {
                if(dbFile.equals("dba"))
                    continue;
                
                if(dbFile.equals("properties"))
                {
                    for(String table : GetTables(connection))
                    {
                        if(table.startsWith("WL_"))
                        {
                            if(!GetColumnHeaders(table, connection).contains("MINTED_ADJ"))
                            {                        
                                ExecuteUpdate("alter table " + table + " add minted_adj int", connection);
                                ExecuteUpdate("update " + table + " set minted_adj=0", connection);                        
                                System.out.println("added minted_adj column to " + table);
                                BackgroundService.AppendLog("added minted_adj column to " + table);
                            }
                            continue;
                        }
                        if(table.equals("ALERTS_SETTINGS"))
                        {
                            if(!GetColumnHeaders(table, connection).contains("USDPRICE"))
                            {                        
                                ExecuteUpdate("alter table alerts_settings add usdprice boolean", connection);
                                ExecuteUpdate("update alerts_settings set usdprice=false", connection);   
                                ExecuteUpdate("alter table alerts_settings add usdvalue double", connection);
                                ExecuteUpdate("update alerts_settings set usdvalue=0", connection);                        
                                System.out.println("added usdprice,usdvalue column to " + table);
                                BackgroundService.AppendLog("added usdprice,usdvalue column to " + table);
                            }                            
                        }
                    }
                    if(!TableExists("account_data", connection))
                    {
                        CreateTable(new String[]{"account_data","id","int","auto_backup","boolean",
                            "use_price_treshold","boolean","login_count","int","donate_dismissed","boolean"}, connection);
                        InsertIntoDB(new String[]{"account_data","id","0","auto_backup","true","use_price_treshold","false",
                            "login_count","0","donate_dismissed","false"}, connection);
                    }
                    //no need to check dbFiles for non properties related tables
                    continue;
                }
                
                if(TableExists("my_watchlist", connection))
                {
                    if(!GetColumnHeaders("my_watchlist", connection).contains("MINTED_ADJ"))
                    {                        
                        ExecuteUpdate("alter table my_watchlist add minted_adj int", connection);
                        ExecuteUpdate("update my_watchlist set minted_adj=0", connection);                        
                        System.out.println("added minted_adj table to " + dbFile);
                        BackgroundService.AppendLog("added minted_adj column to " + dbFile);
                    }
                }
                if(TableExists("node_prefs", connection))
                {
                    if(!GetColumnHeaders("node_prefs", connection).contains("USDPRICE"))
                    {                        
                        ExecuteUpdate("alter table node_prefs add usdprice boolean", connection);
                        ExecuteUpdate("update node_prefs set usdprice=false", connection);                        
                        System.out.println("added usdprice column to " + dbFile);
                        BackgroundService.AppendLog("added usdprice column to " + dbFile);
                    }
                }
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }
    
    protected void SetSocket()
    {
        File checkFile = new File(System.getProperty("user.dir") + "/databases/properties.mv.db");
        if(checkFile.exists())
        {
            try (Connection connection = ConnectionDB.getConnection( "properties"))
            {
               if(TableExists("socket", connection))
               {
                   customIP = (String)GetFirstItem("socket", "ip", connection);
                   customPort = (String) GetFirstItem("socket","port", connection);
                   socket = customIP + ":" + customPort;
               }
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }
    
    private void AccountBackup()
    {  
        BackgroundService.GUI.ShowLoadScreen();
        //don't do a backup if props doesn't exist or is not accessible 
        File checkFile = new File(System.getProperty("user.dir") + "/databases/properties.mv.db");
        if(!checkFile.exists())
            return;
        if(!ConnectionDB.CanConnect("properties", dbPassword))
            return;
        
        //must check auto backup prefs after checkin if properties file is accessible
        try(Connection connection = ConnectionDB.getConnection("properties"))
        {
            //older properties files will not have this table, the compatibility check must be
            //done after CheckDBFiles() (to ensure access to properties) which calls this function. 
            if(!TableExists("account_data", connection))
                return;
            
            if(!(boolean)GetFirstItem("account_data", "auto_backup", connection))
                return;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }        
        
        ArrayList<File> backupFiles = new ArrayList<>();
        File initFile = new File(System.getProperty("user.dir") + "/bin/init");
        //initFile could be non-existent if user has not set a password, we still want to be able to backup the account in that case
        if(initFile.exists())
            backupFiles.add(initFile);
        backupFiles.add(new File(System.getProperty("user.dir") + "/bin/auth"));
        backupFiles.add(new File(System.getProperty("user.dir") + "/bin/dba.mv.db"));
        
         for(String db : dbFiles)
         {
             //added above (different folder)
             if(db.equals("dba"))
                 continue;
             
             backupFiles.add(new File(dbFolderOS + "/" + db + ".mv.db"));
         }
         
         File dir = new File(System.getProperty("user.dir") + "/restore");
         if(!dir.isDirectory())
             dir.mkdir();
         File newFile = new File(System.getProperty("user.dir") + "/restore/restore_temp.zip");
         if(newFile.exists())
             newFile.delete();
         
        try
        {
            Utilities.ZipFiles(backupFiles, newFile);
            
            //in case the zip operation fails, we don't want to delete the old restore file
            //we make the new zip file a temp, if it succeeds, we delete the old file and rename the new one
            File oldFile = new File(System.getProperty("user.dir") + "/restore/restore.zip");
            if (oldFile.exists())
                oldFile.delete();
            Path fileToMovePath = Paths.get(newFile.getPath());
            Path targetPath = Paths.get(oldFile.getPath());
            Files.move(fileToMovePath, targetPath,StandardCopyOption.REPLACE_EXISTING);
            
            BackgroundService.AppendLog("Account backup complete");
            System.out.println(Main.BUNDLE.getString("backupComplete"));
        }
        catch (IOException e)
        {      
            newFile.delete();
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog("ACCOUNT BACKUP FAILED\n" + e.toString());
            System.out.println(Main.BUNDLE.getString("backupFailed"));
        }
    }
    
    protected File ImportAccountFile()
    {
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/restore");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("zip files (*.zip)", "zip");
        //        jfc.setSelectedFile(new File("properties.mv.db")); //show preferred filename in filechooser
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);//only allow *.zip files
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = jfc.getSelectedFile();

            if (selectedFile.getName().equals("restore.zip"))
            {
                return selectedFile;
            }
            else
            {
                JOptionPane.showMessageDialog(null, Main.BUNDLE.getString("invalidRestoreFile"));
                return null;
            }
        }
        else
        {
            BackgroundService.AppendLog("Could not open file chooser @ import acount file");
            return null;
        }
    }    
    
    protected boolean RestoreAccount()
    {
        File accountFile = ImportAccountFile();
        
        if(accountFile != null)
        {
            if(JOptionPane.showConfirmDialog(BackgroundService.GUI, 
                    Utilities.AllignCenterHTML(Main.BUNDLE.getString("importAccountPrompt")),
                    Main.BUNDLE.getString("importAccountPromptTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
            {
                File dbDir = new File(dbFolderOS);
                File authDir = new File(System.getProperty("user.dir") + "/bin");
                if(dbDir.isDirectory())
                    dbDir.delete();
                if(authDir.isDirectory())
                    authDir.delete();

                try
                {
                    UnzipRestoreFile(accountFile, dbDir, authDir);        
                    JOptionPane.showMessageDialog(BackgroundService.GUI, Main.BUNDLE.getString("accountImported"));     
                    return true;//success
                }
                catch (HeadlessException | IOException | NullPointerException e)
                {
                    BackgroundService.AppendLog(e);
                    JOptionPane.showMessageDialog(BackgroundService.GUI, Main.BUNDLE.getString("importFailed") + e.toString());
                    return false;//exception thrown
                }
            }
            return false;//user clicked no
        }
        else //account file = null
            return false;                    
    }
    
    private static void UnzipRestoreFile(File restoreFile, File dbDir, File authDir) throws FileNotFoundException, IOException
    {
        if(!dbDir.isDirectory())
            dbDir.mkdir();
        if(!authDir.isDirectory())
            authDir.mkdir();
        
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(restoreFile)))
        {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null)
            {
                File newFile;

                switch (zipEntry.getName())
                {
                    case "auth":
                    case "init":
                    case "dba.mv.db":
                        newFile = new File(authDir.toPath() + "/" + zipEntry.getName());
                        break;
                    default:
                        newFile = new File(dbDir.toPath() + "/" + zipEntry.getName());

                }
                try (FileOutputStream fos = new FileOutputStream(newFile))
                {
                    int len;
                    while ((len = zis.read(buffer)) > 0)
                    {
                        fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
    
    public boolean TableExists(String table, Connection c)
    {
        try
        {
            String sqlString = "select * from " + table + " limit 1";//limit result to 1 row
            Statement stmt = c.createStatement();
            //no return value needed, just need to know if error is thrown
            stmt.executeQuery(sqlString);
            
            //if no error was thrown, the table exists
            return  true;    
        }
        catch (SQLException e)
        {
            return false;
        }
    }
    
    protected char[] RetrieveDbPassword(char[] fileAndReqorderPassword)
    {        
         //For reading a dba file we don't use ConnectionDB, we access this file using the reqorder password as filePW
        //ConnectionDB uses the dbPassword as filePW      
        String password;
        try (Connection c = ConnectionDB.get_DBA_Connection(fileAndReqorderPassword))
        {
             password = (String) GetFirstItem("dba", "value", c);
            c.createStatement().execute("SHUTDOWN");   
            c.close();
            return password.toCharArray();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);            
        }
        
        return null;        
    }
    
    //args[0] = table name, args[1..,3..,5..etc] = keys, args[2..,4..,6..etc] = type
    public void CreateTable(String[] args, Connection c)
    {
        String sqlString = "create table if not exists " + args[0];            
        if(args.length > 1)
        {
            sqlString += " (";                
            for(int i = 1; i < args.length - 1; i++)
                sqlString += i % 2 == 1 ? args[i] + " " : args[i] + ",";                
            sqlString += args[args.length - 1] + ")";                
            ExecuteUpdate(sqlString,c);
        }         
    }
    
    public void ChangeValue(String table, String item,  String itemValue, String key, String keyValue, Connection c)
    { 
        String sqlString = String.format("update %s set %s=%s where %s=%s", table,item,itemValue,key,keyValue); 
        ExecuteUpdate(sqlString,c);       
    }    
    
    public void ChangeValues(String table, ArrayList<KeyItemPair>pairs,Connection c)
    {
        pairs.stream().map(pair ->
        {
            return String.format("update %s set %s=%s where %s=%s", table,pair.item,pair.itemValue,pair.key,pair.keyValue);
        }).forEachOrdered(sqlString ->
        {                
            ExecuteUpdate(sqlString,c);
        }); 
    }
    
    //args[0] = table name, args[1..,3..,5..etc] = keys, values[2..,4..,6..etc] = value
    public void InsertIntoDB(String[] args,Connection c)
    {  
        String  sqlString = "insert into " + args[0];
                 
        sqlString += " (";
        for(int i = 1; i < args.length; i+=2)
            sqlString += i + 2 == args.length ? args[i] + ") values (" : args[i] + ",";
        for(int i = 2; i < args.length; i+=2)
            sqlString += i == args.length - 1 ? args[i] + ")" : args[i] + ",";   

         ExecuteUpdate(sqlString,c);                
    }
    
    public void InsertIntoColumn(String[] args,Connection c)
    {  
        String  sqlString = "insert into " + args[0] + " values ";
           
        for(int i = 1; i < args.length; i++)
            sqlString += i + 1 == args.length ? "(" + args[i] + ")" : "(" + args[i] + "),";        

         ExecuteUpdate(sqlString,c);                
    }
    
    
    public ArrayList<String> GetTables(Connection c)
    {
        try 
        {      
            ArrayList tables = new ArrayList<String>();
            String sqlString = "show tables";
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while (resultSet.next())
                tables.add(resultSet.getString(1));
            
            return tables;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;        
    }
    
    protected int GetAddressID(Connection connection, String nameOrAddress)
    {
        //Find out if selected node is qortal address or name and find it's ID
        Object ID_Object;
        ID_Object = GetItemValue(
                "my_watchlist", "id", "name", Utilities.SingleQuotedString(nameOrAddress), connection);
        if (ID_Object == null)
        {
            ID_Object = GetItemValue(
                    "my_watchlist", "id", "address", Utilities.SingleQuotedString(nameOrAddress), connection);
        }
        
        return (int) ID_Object;
    }
    
    //Gets the value type of the column in a table, the header for the columns so to speak
    public ArrayList<String> GetColumnHeaders(String table, Connection c)
    {
        try 
        {        
            ArrayList items = new ArrayList<String>();           
            String sqlString = "show columns from " + table;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getString(1));

            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }        
        return null;        
    }
    
    //Gets all the items in the specified column
    public ArrayList<Object> GetColumn(String table, String column, String orderKey,String order, Connection c)
    {
         try 
        {        
            String orderString = orderKey.isEmpty() ? orderKey : " order by " + orderKey + " " + order;
            ArrayList items = new ArrayList<String>();            
            String sqlString = "select " + column + "  from " + table + orderString;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getObject(1));            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }        
    
    public ArrayList<Object> GetRow(String table, String key, String keyValue,Connection c)
    {
         try 
        {        
            ArrayList items = new ArrayList<String>();           
            String sqlString = String.format("select * from %s where %s=%s", table,key,keyValue);
            Statement stmt  = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            int columnCount = resultSet.getMetaData().getColumnCount();
            while(resultSet.next())
            {
                for(int i=1;i <=columnCount;i++)
                    items.add(resultSet.getObject(i));
            }            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }  
    
    /**
     * Gets the specified item at the first row of the specified table,<br>
     * Used for tables that have only one row and do not need to change<br>
     * the value of single items.This way we don't need a key to identify a row.
     * @param table
     * @param item
     * @param c
     * @return the requested value as an Object*/
    public Object GetFirstItem(String table,String item,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s limit 1", item, table);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);      
                 
            return value;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(String.format("Item '%s' returned null for table '%s'", item,table));
            return null;
        }
    }    
    
    public Object GetFirstItem(String table,String item,String orderKey,String order,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s order by %s %s limit 1", item, table,orderKey,order);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);      
                 
            return value;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(String.format("Item '%s' returned null for table '%s'", item,table));
            return null;
        }
    }    
    
    public Object GetItemValue(String table,String item,String key, String keyValue,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s where %s=%s", item, table, key, keyValue);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);            
            
            return value;
        } 
        catch (SQLException e) 
        {
            //Since the methods calling this function (get account ID by name) sometimes expect a null return value, 
            //we don't want to print the stacktrace to the log everytime this exception is thrown
            BackgroundService.AppendLog(e.toString() + " @ GetItemValue() (ignore if thrown for charttree selection)");
        }
        
        return null;
    }    
    
     public void FillJTable(String table,String whereKey, JTable jTable, Connection c)
    {
        try 
        {      
            String header = GetColumnHeaders(table, c).get(0); //will be "ID" or "Timestamp"  
            String query = String.format("select * from %s %s order by %s asc", table,whereKey,header);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(query);    
            jTable.setModel(Utilities.BuildTableModel(table,resultSet));
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }        
    }   
    
    public void SaveCredentials(String username, String password,String smtp,String port,String recipient, String key, String salt)
    {
        try (Connection connection = ConnectionDB.getConnection("properties"))
        {     
            if(!TableExists("mail_server", connection))
                CreateTable(new String[]{"mail_server","id","tinyint",
                    "username","varchar(255)","password","varchar(255)","smtp","varchar(255)","port","varchar(255)",
                    "recipient","varchar(255)","key","varchar(255)","salt","varchar(255)"}, 
                        connection);
            
            ExecuteUpdate("delete from mail_server", connection);
            
            InsertIntoDB(new String[]{"mail_server",
                "id","0",
                "username",Utilities.SingleQuotedString(username),
                "password",Utilities.SingleQuotedString(password),
                "smtp",Utilities.SingleQuotedString(smtp),
                "port",Utilities.SingleQuotedString(port),
                "recipient",Utilities.SingleQuotedString(recipient),
                "key",Utilities.SingleQuotedString(key),
                "salt",Utilities.SingleQuotedString(salt)}, connection);
            
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    //args 0 = database, 1,3,5..etc = items, 2,4,6,etc = itemValues
    public boolean SaveNodePreferences(String[] args)
    {
        Connection c;
        try 
        {
            c = ConnectionDB.getConnection(args[0]);
            
            if(c == null)
                return false; //error message already shown @ getUnencryptedConnection
            
            //NOT USING MERGE TO AVOID LOGICAL ERRORS LATER ON IN CASE THE COLUMNS IN THE TABLE MIGHT CHANGE        
            if(TableExists("node_prefs",c))
            {
                //check if changes were made, if so get confirmation, delete node_prefs row and insert new one 
                //(needs only 1 update as opposed to using ChangeValue()), then delete all associated tables and recreate the new tables
                boolean changed = false;
                String updateDeltaString = "";

                for(int i = 1;i<args.length -1;i+=2)
                {
                    //If only updatedelta has changed, we don't need to remove the node_data table, just update the updatedelta
                    if(args[i].equals("updatedelta"))
                    {
                       if(!(args[i + 1]).equals(String.valueOf(GetItemValue("node_prefs", args[i], "id", "0",c))))
                       {
                           updateDeltaString = args[i+1];
                            continue;                       
                       }
                    }
                    //if we find even one change, we recreate all tables if user accepts
                    if(!(args[i + 1]).equals(String.valueOf(GetItemValue("node_prefs", args[i], "id", "0",c))))
                    {
                        changed = true;
                        break;
                    }
                }

                if(!changed)
                {
                    if(!updateDeltaString.isEmpty())
                    {
                        ChangeValue("node_prefs", "updatedelta", updateDeltaString, "id", "0",c);
                    }  
                    //we can start REQORDING if nothing has changed except updatedelta
                    return true;  
                }                 

                 String[] split = Main.BUNDLE.getString("nodeSettingsChanged").split("%%");
                 int choice = JOptionPane.showConfirmDialog(
                         BackgroundService.GUI,
                         Utilities.AllignCenterHTML(split[0] + args[0] + split[1]),
                         Main.BUNDLE.getString("nodeSettingsChangedTitle"),
                         JOptionPane.OK_CANCEL_OPTION,
                         JOptionPane.WARNING_MESSAGE
                 );

                 if(choice == JOptionPane.CANCEL_OPTION)
                     return false;

                //easier to just drop the entire table and re-create it than to change the values for every column
                ExecuteUpdate("delete from node_prefs",c);             
                ExecuteUpdate("drop table node_data",c);
                if(TableExists("usdprice", c))
                    ExecuteUpdate("drop table usdprice", c);   
                if(TableExists("ltcprice", c))
                    ExecuteUpdate("drop table ltcprice", c);   
                if(TableExists("dogeprice", c))
                    ExecuteUpdate("drop table dogeprice", c);
            }
            else
            {
                CreateTable(new String[]{"node_prefs","id","tinyint","blockheight","boolean","myblockheight","boolean",
                    "numberofconnections","boolean","uptime","boolean","allknownpeers","boolean","allonlineminters","boolean",
                    "usdprice","boolean","ltcprice","boolean","dogeprice","boolean","data_usage","boolean","cpu_temp","boolean",
                    "cpu_usage","boolean", "qortal_ram","boolean","blockchainsize","boolean","updatedelta","int"},c);
            }

           //insert new row if table did not exist or if settings have changed
           var newStatement = new ArrayList<String>();
           newStatement.add("node_prefs");//table
           newStatement.add(("id"));//key
           newStatement.add("0");//keyValue
           for(int i = 1;i<args.length;i++)
               newStatement.add(args[i]);//items to insert

           InsertIntoDB(newStatement.toArray(new String[0]),c);     

           //First we create the column with all items, then we delete the ones that are not specified in the settings
           //this to avoid logical errors later on, in case the order of args may have changed
           CreateTable(new String[]{"node_data","timestamp","long","blockheight","int","myblockheight","int",
               "numberofconnections","tinyint","uptime","long","allknownpeers","int","allonlineminters","int",
               "bytes_sent","long","bytes_received","long","avg_bytes_sent","long","avg_bytes_received","long",
               "cpu_temp","double","cpu_usage","double","qortal_ram","double","blockchainsize","long",
               "RAM_USAGE","LONG"},c);
           //ATTENTION: REMOVE RAM_USAGE,LONG WHEN DONE TESTING MEMORY USAGE??
           CreateTable(new String[]{"buildversion","timestamp","long","buildversion","varchar(50)"},c);

           //since data usage creates 4 seperate columns, we need to check for it seperately
           boolean dataSelected = false;
           for(int i = 1;i<args.length-1;i++)
           {
               if(args[i].equals("data_usage"))
               {
                   dataSelected = Boolean.parseBoolean(args[i+1]);
                   break;
               }
           }

           //get all column headers of node_data and compare them to the settings, if matched and setting not selected -> drop column
            for(String column : GetColumnHeaders("node_data",c))
            {  
                //dont remove timestamp, will always have a column
                if(column.equals("TIMESTAMP"))
                    continue;            

                //ATTENTION: REMOVE RAM_USAGE,LONG WHEN DONE TESTING MEMORY USAGE??
                if(column.equals("RAM_USAGE"))
                    continue;            

               for(int i=1;i<args.length-1;i+=2)
               {
                   //seperate check for data_usage (2 columns)
                   if(args[i].equals("data_usage"))
                   {
                       if(!dataSelected)
                       {
                           switch(column)
                           {
                               case "BYTES_SENT":
                               case "BYTES_RECEIVED":
                               case "AVG_BYTES_SENT":
                               case "AVG_BYTES_RECEIVED":
                                   ExecuteUpdate("alter table node_data drop column " + column, c);
                                   break;
                           }
    //                        System.out.println("DROPPING COLUMN  " + column);
    //                        System.out.println(String.format("%s , %s", column,args[i].toUpperCase()));    
                       }
                   }
                   
                   boolean itemSelected = Boolean.parseBoolean(args[i+1]);
                   if(args[i].toUpperCase().equals(column))
                   {
                       if(!itemSelected)
                       {
                            ExecuteUpdate("alter table node_data drop column " + column,c);                   
                       }
                   }
               }
            }            
            
           //check which price updates are set to true and create tables for them
           for(int i = 1;i<args.length - 1;i+=2)
           { 
               if(args[i].equals("usdprice"))
                {                    
                    if(Boolean.parseBoolean(args[i+1]))
                        CreateTable(new String[]{"usdprice","timestamp","long","usdprice","double"}, c);         
                } 
               if(args[i].equals("ltcprice"))
                {                    
                    if(Boolean.parseBoolean(args[i+1]))
                        CreateTable(new String[]{"ltcprice","timestamp","long","ltcprice","long"}, c);         
                }     
               if(args[i].equals("dogeprice"))
                {                    
                    if(Boolean.parseBoolean(args[i+1]))
                        CreateTable(new String[]{"dogeprice","timestamp","long","dogeprice","long"}, c);         
                } 
           }

            c.close();
            
            return  true;
        }
        catch (NullPointerException | HeadlessException | SQLException e)
        {    
            BackgroundService.AppendLog(e);     
        }
        
       return false;
       
    }//end SaveNodePrefs()
        
    public boolean AddAddress(String watchlist, String address)
    {        
        try (Connection c = ConnectionDB.getConnection("properties")) 
        {             
            //we need to check with Qortal API if the entered address is valid, no string variable needed
             String jsString = Utilities.ReadStringFromURL("http://" + socket + "/addresses/" + address);  
             JSONObject jsObject = new JSONObject(jsString);
             int mintedAdjusted = jsObject.getInt("blocksMintedAdjustment");

            //check if address exists, using the merge statement in h2 would be cleaner 
            //but this way we can notify the user that the address already exists
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery("select address from " + watchlist); //gets all stored addresses
            while(resultSet.next())
            {
                if(address.equals(resultSet.getString("address")))
                {
                    String[] split = Main.BUNDLE.getString("addressExists").split("%%");
                    JOptionPane.showMessageDialog(null,split[0] + address + split[1] + watchlist + split[2]);
                    c.close();
                    return false;
                }
            }    
            
            int ID = GetAddressPosition(watchlist,c);

            if(ID == -1)//fail safe (should never be the case)
            {
                c.close();
                return false;
            }

            //get the registered name for the address, if available
            jsString = Utilities.ReadStringFromURL("http://" + socket + "/names/address/" + address);
            JSONArray jSONArray = new JSONArray(jsString);
            String name = Utilities.SingleQuotedString("");
            if(jSONArray.length() > 0)
            {
                jsObject = jSONArray.getJSONObject(0);          
                name =  jsObject.getString("name");   
                if(name.contains("'"))
                        name = name.replace("'", "''");
            }

            //add address            
            InsertIntoDB(new String[]{watchlist,
                "address",Utilities.SingleQuotedString(address),
                "id",String.valueOf(ID),
                "name",Utilities.SingleQuotedString(name),
                "blocksminted","true",
                "level","true",
                "balance","true",
                "balancetreshold","0.1",
                "minted_adj",String.valueOf(mintedAdjusted)}, c);
            
            c.close();
            return true;            
        }
        catch(ConnectException e)
        {
                JOptionPane.showMessageDialog(null,Main.BUNDLE.getString("cannotConnect"));
                return false;            
        }
        catch(IOException e)
        {            
            JOptionPane.showMessageDialog(null, Main.BUNDLE.getString("invalidAddress") + address + "\n");
            return false;
        }
        catch (NullPointerException | HeadlessException | SQLException | TimeoutException | JSONException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return false;
        
    }//end AddAddress              
    
    //we need to know the position of the address in the table to create an ID for it
    private int GetAddressPosition(String watchlist, Connection c)
    {    
        try 
        {
            //find out if table is empty
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery("select count(*) from " + watchlist); //gets row count for table
            resultSet.first(); //move cursor to first (result will only have 1 position)
            int count = resultSet.getInt(1); 
            
            //if table is empty
            if(count == 0)
                return 0;
            else
            {
                //we cannot use rowcount as ID, if user has deleted an address from the db the rowcount
                //will decrease, which could cause duplicate ID snapShots, we need to find the highest ID
                //and increment it by 1 to make a unique ID for the new address
                stmt = c.createStatement();
                resultSet = stmt.executeQuery("select id from " + watchlist); //gets id column
                int highest = 0;
                while(resultSet.next())
                {
                    if(resultSet.getInt(1) > highest)
                        highest = resultSet.getInt(1);
                }
                
                return  highest + 1;                
            }
        } 
        catch (SQLException e) 
        {         
            BackgroundService.AppendLog(e);
        }
        
        //in case of error we return -1 to make sure (debug failsafe: should not be reached)
        JOptionPane.showMessageDialog(null,"Error: address ID returned -1\n");
        return -1;
    }    
    
    public void CreateAddressTables(String database, String watchlist)
    {
        Connection propsConnection;
        Connection dbConn;
        try
        {      
            //we need 2 connections here, the properties connection verifies which tables to make, the database 
            //connection creates the tables.            
            propsConnection = ConnectionDB.getConnection("properties");
            dbConn = ConnectionDB.getConnection(database);
            
            Statement stmt = propsConnection.createStatement();
            String sqlString = "select * from " + watchlist;
            ResultSet resultSet = stmt.executeQuery(sqlString);            

            while(resultSet.next())
            {
                if(resultSet.getBoolean("blocksminted"))
                {
                    sqlString = String.format(
                            "create table if not exists WL_address_%d_blocks (timestamp long,blocksminted int)", resultSet.getInt("id"));
                    ExecuteUpdate(sqlString,dbConn);
                }
                if(resultSet.getBoolean("level"))
                {
                    sqlString = String.format(
                            "create table if not exists WL_address_%d_level (timestamp long,level tinyint)", resultSet.getInt("id"));
                    ExecuteUpdate(sqlString,dbConn);                  
                } 
                if(resultSet.getBoolean("balance"))
                {
                    sqlString = String.format(
                            "create table if not exists WL_address_%d_balance (timestamp long,balance double)", resultSet.getInt("id"));
                    ExecuteUpdate(sqlString,dbConn);                  
                }                
            }    
            
            stmt = propsConnection.createStatement();
            resultSet = stmt.executeQuery("select * from " + watchlist);
            
            while(resultSet.next())
            {
                InsertIntoDB(new String[]{
                    "my_watchlist",
                    "id",String.valueOf(resultSet.getInt("id")),
                    "address",Utilities.SingleQuotedString(resultSet.getString("address")),
                    "name",Utilities.SingleQuotedString(resultSet.getString("name")),
                    "balancetreshold",String.valueOf(resultSet.getDouble("balancetreshold")),
                    "alert","false",
                    "alertvalue","0"}, 
                        dbConn);
            }
            
            propsConnection.close();
            dbConn.close();
            
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }    
    
    public void CheckCPU_Monitor()
    {
        if(myOS.equals("Windows") && sensors.getCpuTemperature() < 1)
        {
            JOptionPane jOptionPane = new JOptionPane(
                        Utilities.AllignCenterHTML(Main.BUNDLE.getString("ohwmDialog")), 
                        JOptionPane.PLAIN_MESSAGE);

                JDialog dlg = jOptionPane.createDialog(Main.BUNDLE.getString("ohwmDialogTitle"));
                dlg.addComponentListener(new ComponentAdapter()
                {
                    @Override
                    public void componentShown(ComponentEvent e)
                    {
                        super.componentShown(e);
                        final javax.swing.Timer t = new javax.swing.Timer (15000, (ActionEvent e1) ->
                        {
                            dlg.dispose();
                        });
                        t.start();                    
                    }
                });
                dlg.setVisible(true);            
        }
    }
    
    /**When user starts reqording, this function is called and checks for all alerts whether
     the data that  is needed to trigger the alert is actually being reqorded
     * @param database.*/
    protected void CheckAlertsValidity(String database)
    {
        try(Connection propsConnection = ConnectionDB.getConnection("properties"))
        {            
            if(!TableExists("alerts_settings", propsConnection))
                CreateAlertsSettingsTable(propsConnection);
            
            ArrayList<String> invalids = new ArrayList<>();

            try (Connection dbConn = ConnectionDB.getConnection(database))
            {
                if ((boolean) GetFirstItem("alerts_settings", "blockchainsize", propsConnection))
                {
                    if (!(boolean) GetFirstItem("node_prefs", "blockchainsize", dbConn))
                    {
                        invalids.add(Main.BUNDLE.getString("ivBlockchainSize"));
                        invalids.add(Main.BUNDLE.getString("ivSpaceLeft"));
                    }           
                }
                 if ((boolean) GetFirstItem("alerts_settings", "usdprice", propsConnection))
                {
                    if (!(boolean) GetFirstItem("node_prefs", "usdprice", dbConn))
                        invalids.add("USD price");              
                }
                 if ((boolean) GetFirstItem("alerts_settings", "ltcprice", propsConnection))
                {
                    if (!(boolean) GetFirstItem("node_prefs", "ltcprice", dbConn))
                        invalids.add(Main.BUNDLE.getString("ivLtc"));              
                }
                 if ((boolean) GetFirstItem("alerts_settings", "dogeprice", propsConnection))
                {
                    if (!(boolean) GetFirstItem("node_prefs", "dogeprice", dbConn))
                        invalids.add(Main.BUNDLE.getString("ivDoge"));              
                }
                 if (!TableExists(" my_watchlist", dbConn))  //GetFirstItem("my_watchlist", "id", dbConn) == null)//if table is empty
                {
                    if ((boolean) GetFirstItem("alerts_settings", "minting", propsConnection))
                        invalids.add(Main.BUNDLE.getString("ivMinting"));
                    if ((boolean) GetFirstItem("alerts_settings", "levelling", propsConnection))
                        invalids.add(Main.BUNDLE.getString("ivLevelling"));
                    if ((boolean) GetFirstItem("alerts_settings", "name_reg", propsConnection))
                        invalids.add(Main.BUNDLE.getString("ivName"));                 
                }
            }

            if(invalids.isEmpty())
                return;

            String paneMessage = Main.BUNDLE.getString("paneMessage1");
            paneMessage = invalids.stream().map(invalid -> invalid + "<br/>").reduce(paneMessage, String::concat);
            paneMessage += Main.BUNDLE.getString("paneMessage2");

            JOptionPane.showMessageDialog(BackgroundService.GUI, 
                    Utilities.AllignCenterHTML(paneMessage),
                    Main.BUNDLE.getString("paneMessageTitle"), JOptionPane.WARNING_MESSAGE);
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    public void CheckBlockchainFolder()
    {
        if(blockChainFolder == null)
        { 
             try (Connection c = ConnectionDB.getConnection("properties"))
            {
                if(!TableExists("blockchain_folder", c))
                {
                    JOptionPane.showMessageDialog(BackgroundService.GUI,
                            Utilities.AllignCenterHTML(Main.BUNDLE.getString("blockchainFolderWarning")),
                            Main.BUNDLE.getString("blockchainFolderWarningTitle"), JOptionPane.QUESTION_MESSAGE);
                    JFileChooser jfc = new JFileChooser();
                    jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    int returnValue = jfc.showSaveDialog(null);

                    if (returnValue == JFileChooser.APPROVE_OPTION)
                    {
                        File selectedFile = jfc.getSelectedFile();

                        if (selectedFile.getName().equals("db"))
                        {
                            blockChainFolder = selectedFile.getAbsolutePath();
                            System.out.println(Main.BUNDLE.getString("settingBlockchainFolder") + blockChainFolder);

                            CreateTable(new String[]{"blockchain_folder","id","tinyint","blockchain_folder","varchar(255)"}, c);
                            InsertIntoDB(new String[]{"blockchain_folder","id","0","blockchain_folder",Utilities.SingleQuotedString(blockChainFolder)}, c);
                        }
                        else
                        {
                            JOptionPane.showMessageDialog(BackgroundService.GUI,
                            Utilities.AllignCenterHTML(Main.BUNDLE.getString("invalidBcFolder")),
                            Main.BUNDLE.getString("invalidBcFolderTitle"), JOptionPane.WARNING_MESSAGE);
                        }
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(BackgroundService.GUI,
                            Main.BUNDLE.getString("bcFolderNotSet"),
                            Main.BUNDLE.getString("bcFolderNotSetTitle"), JOptionPane.WARNING_MESSAGE);
                    }                    
                }
                else
                {
                    blockChainFolder = (String) GetItemValue("blockchain_folder", "blockchain_folder", "id", "0", c);                  
                }
                
                c.close();
            }
            catch (HeadlessException | NullPointerException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }
    
    private void StatusAlert()
    {
        //check the alerts_settings every update, this way if user changes settings mid
        //reqording session the changes will take effect without having to stop reqording first
        if(!(boolean)GetFirstItem("alerts_settings", "statusalerts", propertiesConnection))
            return;
        
        long statusAlertInterval = (long) ((byte)GetFirstItem("alerts_settings", "statusinterval",propertiesConnection) * 3600000);//hours to milliseconds        
        
        if(System.currentTimeMillis() - lastStatusAlertTime < statusAlertInterval)
            return;

        lastStatusAlertTime = System.currentTimeMillis();
        
        String subject = Main.BUNDLE.getString("statusSubject");
        String message = Main.BUNDLE.getString("statusHeader");
        
        if((boolean)GetFirstItem("alerts_settings", "shownodeinfo", propertiesConnection))
        {
            if(chainHeight - myBlockHeight >= 30)
            {
                String[] split = Main.BUNDLE.getString("syncWarning").split("%%");
                message += String.format(split[0] + "%d" + split[1], chainHeight - myBlockHeight);
            }
            
            message += String.format(Main.BUNDLE.getString("nodeHeight") + "%s\n",
                    NumberFormat.getIntegerInstance().format(myBlockHeight));
            message += String.format(Main.BUNDLE.getString("chainHeight") + "%s\n",
                    NumberFormat.getIntegerInstance().format(chainHeight));
            message += String.format(Main.BUNDLE.getString("coreUptime") + "%s\n",
                    Utilities.MillisToDayHrMin(uptime));
            message += String.format(Main.BUNDLE.getString("coreBuildversion") + "%s\n",
                    buildVersion.substring(1, buildVersion.length() - 2));
            message += String.format(Main.BUNDLE.getString("connectedPeers") + "%d\n",numberofconnections);
            message += String.format(Main.BUNDLE.getString("mintersOnline") + "%d\n",allOnlineMinters);
            message += String.format(Main.BUNDLE.getString("allKnownPeers") + "%d\n",allKnownPeers);
            message += String.format(Main.BUNDLE.getString("downloadRate") + "%.2f Mb\n",
                    (double)(avrgReceivedPerMinute * 1440) / 1000000);
            message += String.format(Main.BUNDLE.getString("uploadRate") + "%.2f Mb\n", 
                    (double)(avrgSentPerMinute * 1440) / 1000000);                    
                    
            if (blockChainFolder != null)
            {
                File folder = new File(blockChainFolder);
                long size = Utilities.getDirectorySize(folder);
                int sizeMb = (int) ((double) size / 1000000);
                message += String.format(Main.BUNDLE.getString("blockChainSizeDBM") + "%s Mb\n",
                        NumberFormat.getIntegerInstance().format(sizeMb));
                message += String.format(Main.BUNDLE.getString("spaceLeftDBM") + "%s Mb\n",
                        NumberFormat.getIntegerInstance().format(folder.getFreeSpace() / 1000000));
            }

            try
            {        
                String jsString = Utilities.ReadStringFromURL("http://" + socket + "/admin/mintingaccounts");
                JSONArray jSONArray = new JSONArray(jsString);
                //If there's no minting account set we'll get a nullpointer exception
                if (jSONArray.length() > 0)
                {
                    JSONObject jso = jSONArray.getJSONObject(0);
                    String myMintingAddress = jso.getString("mintingAccount");
                    double myBalance = Double.parseDouble(Utilities.ReadStringFromURL("http://" + socket + "/addresses/balance/" + myMintingAddress));
                    jsString = Utilities.ReadStringFromURL("http://" + socket + "/addresses/" + myMintingAddress);
                    jso = new JSONObject(jsString);
                    int myLevel = jso.getInt("level");
                    blocksMinted = jso.getInt("blocksMinted");

                    message+="'\n";
                    message += String.format(Main.BUNDLE.getString("activeAccountDBM") + "%s\n",myMintingAddress);
                    message += String.format(Main.BUNDLE.getString("blocksMintedDBM") + "%s\n",
                            NumberFormat.getIntegerInstance().format(blocksMinted));
                    message += String.format(Main.BUNDLE.getString("balanceDBM") + "%.5f QORT\n",myBalance);
                    message += String.format(Main.BUNDLE.getString("levelDBM") + "%d", myLevel);
                }
                else
                    message += Main.BUNDLE.getString("noAccountDBM");    
            }
            catch (IOException | NumberFormatException | TimeoutException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }    
        }                   
        
        PoolAlert(subject, message);        
    }
    
    private void SpaceLeftAlert()
    {
        if(blockChainFolder == null)
            return;
        
        if ((boolean) GetFirstItem("alerts_settings", "spaceleft", propertiesConnection))
        {
            long spaceLeftValue = (long) GetFirstItem("alerts_settings", "spaceleftvalue", propertiesConnection);
            File file = new File(blockChainFolder);//new File(filePath);
            if (!spaceLeftAlertSent && file.getFreeSpace() <= spaceLeftValue)
            {
                String[] split = Main.BUNDLE.getString("spaceAlert").split("%%");//0=folder,1=space left,2=alert setting
                PoolAlert(Main.BUNDLE.getString("spaceAlertSubject"),
                        String.format(split[0] + "%s" + split[1] + "%s" + split[2] + "%s" + split[3],
                                blockChainFolder, NumberFormat.getIntegerInstance().format(file.getFreeSpace() / 1000000),
                                NumberFormat.getIntegerInstance().format(spaceLeftValue / 1000000)));
                spaceLeftAlertSent = true;
            }
        }
    }
    
     private void OutOfSyncAlert()
    {        
        if ((boolean) GetFirstItem("alerts_settings", "out_of_sync", propertiesConnection))
        {
            if (!outOfSyncAlertSent && chainHeight - myBlockHeight >= 30)
            {
                String[] split = Main.BUNDLE.getString("outOfSyncAlert").split("%%");
                PoolAlert(Main.BUNDLE.getString("outOfSyncSubject"),
                        String.format(split[0] + "%d" +  split[1],chainHeight - myBlockHeight));
                outOfSyncAlertSent = true;
            }
        }
    }
    
    public void SetBlockchainFolder()
    {
         try (Connection c = ConnectionDB.getConnection("properties"))
        {
             JOptionPane.showMessageDialog(BackgroundService.GUI,
                        Utilities.AllignCenterHTML(Main.BUNDLE.getString("locateBcFolder")),
                        Main.BUNDLE.getString("locateBcFolderTitle"), JOptionPane.QUESTION_MESSAGE);
             
                JFileChooser jfc = new JFileChooser();
                jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnValue = jfc.showSaveDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION)
                {
                    File selectedFile = jfc.getSelectedFile();

                    if (selectedFile.getName().equals("db"))
                    {
                        blockChainFolder = selectedFile.getAbsolutePath();
                        System.out.println("Setting blockchainfolder to " + blockChainFolder);

                        if(!TableExists("blockchain_folder", c))
                        {
                            CreateTable(new String[]{"blockchain_folder","id","tinyint","blockchain_folder","varchar(255)"}, c);
                            InsertIntoDB(new String[]{"blockchain_folder","id","0","blockchain_folder",Utilities.SingleQuotedString(blockChainFolder)}, c);                            
                        }
                        else
                            ChangeValue("blockchain_folder", "blockchain_folder", Utilities.SingleQuotedString(blockChainFolder), "id", "0", c);
                        
                        BackgroundService.GUI.monitorPanel.blockChainFolder = blockChainFolder;
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(BackgroundService.GUI,
                        Utilities.AllignCenterHTML(Main.BUNDLE.getString("invalidBcFolder")),
                        Main.BUNDLE.getString("invalidBcFolderTitle"), JOptionPane.WARNING_MESSAGE);
                    }
                }      

            c.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }   
    
    protected void CreateAlertsSettingsTable(Connection connection)
    {      
        //For any single row table that needs its row to be updated at times, we use an 'id' column as we need to provide
        //a key for inserting/updating the value. When a table is read only and single row we can just use GetFirstItem() 
        CreateTable(new String[]{"alerts_settings","id","tinyint","reqording","boolean","out_of_sync","boolean","minting","boolean",
            "core_update","boolean","levelling","boolean","name_reg","boolean","blockchainsize","boolean","blockchainvalue","long",
            "spaceleft","boolean","spaceleftvalue","long","usdprice","boolean","usdvalue","double","ltcprice","boolean","ltcvalue","long",
            "dogeprice","boolean","dogevalue","long","emailalerts","boolean","statusalerts","boolean","shownodeinfo","boolean","statusinterval","tinyint"}, connection);

        //set all values to default
        InsertIntoDB(new String[]{ "alerts_settings","id","0","reqording","false","out_of_sync","false","minting","false",
            "core_update","false","levelling","false","name_reg","false","blockchainsize","false","blockchainvalue","0",
            "spaceleft","false","spaceleftvalue","0","usdprice","false","usdvalue","0","ltcprice","false","ltcvalue","0",
            "dogeprice","false","dogevalue","0","emailalerts","false","statusalerts","false","shownodeinfo","false",
            "statusinterval","1"}, connection);               
    }
    
    /**When user saves alerts settings, we reset these flags so the alerts will be sent*/
    public void ResetAlertsSent()
    {        
        chainSizeAlertSent = false;
        spaceLeftAlertSent = false;
        outOfSyncAlertSent = false;
    }
    
    private Timer timer;// = new Timer();
    private long timestamp;
    private long lastGCTime;
    private final int GC_INTERVAL = 600000; //10 minutes
    private final double USD_UPDATE_DELTA = 0.009d;
    private final int LTC_UPDATE_DELTA = 10000;
    private final int DOGE_UPDATE_DELTA = 1000;
    private int myBlockHeight;
    private int chainHeight;
    private int numberofconnections;
    private int blocksMinted;
    private int level;
    private double balance;
    private long LTCprice;
    private long BTCprice;
    private double USDprice;
    private long DogePrice;
    private long uptime;
    private String buildVersion;
    private int allKnownPeers;
    private int allOnlineMinters;
    //only update balance to db if delta is larger than this variable
    private double balanceUpdateTreshold;
    private long startTime;
    private long totalBytesSent;
    private long totalBytesReceived;
    private long lastBytesSent;
    private long lastBytesReceived;
    long avrgReceivedPerMinute;
    long avrgSentPerMinute;  
    private int updateDelta;
    //for alerts we want to check more often than 
    //the updatedelta, which could be as long as 24 hours
    private final int alertsDelta = 60000;
    private int currentTick;
    private ArrayList<String> nodeDataColumns;
    private long lastBlockMintedTime;
    private ArrayList<String> alertedBlockAddresses;
    private boolean chainSizeAlertSent;
    private boolean spaceLeftAlertSent;
    private boolean outOfSyncAlertSent;
    private long lastStatusAlertTime;
    protected boolean usePriceTreshold = false;
    
    public void StopReqording()
    {     
        retries = -1;
        timer.cancel();
        System.out.println(Main.BUNDLE.getString("sessionStopped"));        
        System.gc();
    }
    
    public void Reqord(String selectedDatabase)
    {        
//        System.out.println("RESTORE ALERTSDELTA TO 60000");//FOR TESTING  
        
        timer = new Timer();
        
        //Initialisation of these variables should only happen on user initiated reqord (as opposed to retry)
        if(retries == -1)
        {            
            retries = 0;
            database = selectedDatabase;
            alertedBlockAddresses = new ArrayList<>();
            alertsPool = new ArrayList<>();
            chainSizeAlertSent = false;
            spaceLeftAlertSent = false;
            outOfSyncAlertSent = false;
            lastStatusAlertTime = System.currentTimeMillis();    
        
            //get the items to update and update delta
            try
            {
                dbConnection = ConnectionDB.getConnection(database);
                nodeDataColumns = GetColumnHeaders("node_data",dbConnection);
                updateDelta = (int) GetItemValue("node_prefs", "updatedelta", "id", "0",dbConnection);    
                
                //if there's a problem fetching data from poloniex (no internet, no prices returned) 
                //use the last entry
                if(TableExists("usdprice", dbConnection))
                {
                    Object usdObject = GetFirstItem("usdprice", "usdprice", "timestamp", "desc", dbConnection);
                    USDprice = usdObject == null ? 0 : (double)usdObject;
                }               
                
                dbConnection.close();
            }
            catch (NullPointerException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }   
            
            //updates to the database only occur on tick 1, alert checks occur every tick
            currentTick = 1;
            lastBlockMintedTime = System.currentTimeMillis();

//            System.out.println("UPDATE DELTA = " + updateDelta + " , SETTING TO 5000");//FOR TESTING
//            updateDelta = 5000;

            startTime = System.currentTimeMillis();
            totalBytesSent = 0;
            totalBytesReceived = 0;
            for(NetworkIF nif : interfaces)
            {
                nif.updateAttributes();
                lastBytesSent += nif.getBytesSent();
                lastBytesReceived += nif.getBytesRecv();
            }             
        }          
        
        timer.scheduleAtFixedRate(new TimerTask() 
        {
            long sessionTime = 0;
            int snapShots = 0;
            
            @Override
            public void run() 
            {                
                if(currentTick == 1)
                {
                    snapShots++;
                    String[] split = Main.BUNDLE.getString("sessionInfo").split("%%");
                    System.out.println(
                            String.format(split[0] + "%s" + split[1] + "%d" + split[2], Utilities.MillisToDayHrMin(sessionTime),snapShots));
                    sessionTime += updateDelta;                    
                }
//                System.out.println("Starting update @ " + Utilities.TimeFormat(System.currentTimeMillis()));
                  
                FindBandwidthUsage();
                FindQortalUsage();
                
//                System.out.println("Mb sent = " + (double) bytesSent / 1000000);
//                System.out.println("Mb received = " + (double) bytesReceived / 1000000); 
                
                try 
                {    
                    //open/close connections every update (open at start of try, when catching exceptions we need connection2 to be open)
                    dbConnection = ConnectionDB.getConnection(database);   
                    propertiesConnection = ConnectionDB.getConnection("properties");
                    
                    //We get all the variables regardless of if the user selected them, this is not very costly and saves us from
                    //checking for every  variable, which would probably result in more lines of code
                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/admin/status"); 
                    jSONObject = new JSONObject(jsonString);

//                    usePriceTreshold = (boolean)GetFirstItem(sql, sql, dbConnection)
                    timestamp = System.currentTimeMillis();
                    myBlockHeight = jSONObject.getInt("height");
                    numberofconnections = jSONObject.getInt("numberOfConnections");
                    chainHeight = Utilities.FindChainHeight();

                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/admin/info");
                    jSONObject = new JSONObject(jsonString);
                    uptime = jSONObject.getLong("uptime");
                    buildVersion = Utilities.SingleQuotedString(jSONObject.getString("buildVersion"));

                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/peers/known");
                    JSONArray jsonArray = new JSONArray(jsonString);
                    allKnownPeers = jsonArray.length();
                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/addresses/online");
                    //sometimes the Qortal API returns a file not found exception for addresses/online query
                    if(jsonString == null)
                        allOnlineMinters = 0;
                    else
                    {
                        jsonArray = new JSONArray(jsonString);
                        allOnlineMinters = jsonArray.length();                        
                    }       
                    
                    //Prices will return a long (probably due to floating point accuracy issues) which will have to be divided by
                    //100.000.000 to get the amount of QORT one LTC buys, to get the $ to QORT ratio we need to know the 
                    //LTC/$ ratio and extrapolate that to QORT
                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/crosschain/price/LITECOIN?maxtrades=10");
                    LTCprice = Long.parseLong(jsonString);                    
                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/crosschain/price/DOGECOIN?maxtrades=10");
                    DogePrice = Long.parseLong(jsonString);
                    // this url returns the price based on the last 10 trades, which apparently took place when btc was around
                    //$70.000. Until btc trades are re-instated we should not register the btc/qort ratio
//                    jsonString = Utilities.ReadStringFromURL("http://" + socket + "/crosschain/price/BITCOIN?maxtrades=10");
//                    BTCprice = Long.parseLong(jsonString);  


                    System.err.println(GetFirstItem("NODE_PREFS", "USDPRICE", dbConnection));

                    //Only fetch usd price if enabled by user
                    if((boolean)GetFirstItem("NODE_PREFS", "USDPRICE", dbConnection))
                    {
                        //fetch USD price after getting LTC price
                        long now = Instant.now().getEpochSecond();
                        jsonString = Utilities.ReadStringFromURL(
                             "https://api.coingecko.com/api/v3/coins/litecoin/market_chart/range?vs_currency=USD&from=" + (now - 600) + "&to=" + now);

                        //last known usdPrice at session start was fetched on session start
                        //if jsonString returns null -> use that price
                        if(jsonString != null)
                        {                        
                            JSONObject jsonResponse = new JSONObject(jsonString);
                            JSONArray pricesArray = jsonResponse.getJSONArray("prices");                        
                            JSONArray result = pricesArray.getJSONArray(pricesArray.length() - 1);                    
                            double LTC_USDprice = result.getDouble(1);
                            double LTCdouble = ((double)LTCprice / 100000000);
                            USDprice = LTC_USDprice * (1 / LTCdouble);
                        }                           
                    }                     
                    
                    StatusAlert();
                    OutOfSyncAlert();
                    SpaceLeftAlert();
                    BuildversionUpdate();
                    UsdPriceUpdate();
                    LtcPriceUpdate();
                    DogePriceUpdate();                    
                    
                    if(currentTick == 1)
                        //SQLStringList() creates the sql update string according to node_data table headers
                        InsertIntoDB(CreateSQL_StringList().toArray(new String[0]),dbConnection);                         
                    
                    //check all addresses in watchlist for change in registered name
                    if(TableExists("my_watchlist",dbConnection)) //GetFirstItem("my_watchlist", "id", dbConnection) != null)
                    {
                        for(Object addressObj : GetColumn("my_watchlist", "address","","", dbConnection))
                        {
                            String address = addressObj.toString();
                            jsonString = Utilities.ReadStringFromURL("http://" + socket + "/names/address/" + address);
                            jsonArray = new JSONArray(jsonString);
                            if(jsonArray.length() > 0)
                            {
                                jSONObject = jsonArray.getJSONObject(0);
                                String name = jSONObject.getString("name");     
                                if(name.contains("'"))
                                    name = name.replace("'", "''");
                                
                                String nameEntry = (String) GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address), dbConnection);
                                if(!name.equals(nameEntry))
                                {
                                    String[] split = Main.BUNDLE.getString("nameUpdate").split("%%");
                                    String update = String.format(split[0] + "%s" + split[1] + "%s'", nameEntry,name);
                                    ChangeValue("my_watchlist", "name", Utilities.SingleQuotedString(name), "address", Utilities.SingleQuotedString(address), dbConnection);
                                    System.out.println(update);
                                    BackgroundService.AppendLog(update); 
                                     //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                                    if ((boolean) GetFirstItem("alerts_settings", "name_reg",propertiesConnection))
                                    {
                                        split = Main.BUNDLE.getString("nameAlert").split("%%");
                                        PoolAlert(Main.BUNDLE.getString("nameAlertSubject"), String.format(
                                                split[0] + "%s" + split[1] + "%s" + split[2], 
                                                address,name));
                                    }
                                }
                            }
                        }  
                    }                          
                    
                    String address;
                    String ID;
                    ArrayList<String> addressTables = GetTables(dbConnection);
                    for(String table : addressTables)
                    {
                        if(table.startsWith("WL_ADDRESS"))
                        {                                                                
                            if(table.endsWith("BLOCKS"))
                            {
                                ID = table.substring(11, table.length() - 7); //extract ID from table name
                                address = (String) GetItemValue("my_watchlist", "address", "ID", ID,dbConnection);
                                jsonString = Utilities.ReadStringFromURL("http://" + socket + "/addresses/" + address);
                                jSONObject = new JSONObject(jsonString);
                                timestamp = System.currentTimeMillis();
                                blocksMinted = jSONObject.getInt("blocksMinted");
                                BlocksMintedUpdate(table, address);                                    
                            }
                            if(table.endsWith("LEVEL"))
                            {
                                ID = table.substring(11, table.length() - 6);
                                address = (String) GetItemValue("my_watchlist", "address", "ID", ID,dbConnection);
                                jsonString = Utilities.ReadStringFromURL("http://" + socket + "/addresses/" + address);
                                jSONObject = new JSONObject(jsonString);
                                timestamp = System.currentTimeMillis();
                                level = jSONObject.getInt("level");
                                LevelUpdate(table,address);                                    
                            }
                            if(table.endsWith("BALANCE"))
                            {
                                ID = table.substring(11, table.length() - 8);
                                address = (String) GetItemValue("my_watchlist", "address", "ID", ID,dbConnection);
                                balance = Double.parseDouble(Utilities.ReadStringFromURL("http://" + socket + "/addresses/balance/" + address));
                                timestamp = System.currentTimeMillis();
                                balanceUpdateTreshold = (double)GetItemValue("my_watchlist", "balancetreshold", "ID", ID,dbConnection);
                                BalanceUpdate(table,address);                                    
                            }
                        }
                    }//end for(String table)
                    
                    SendAlertPool();
                    dbConnection.close();  
                    propertiesConnection.close();
                    
                    //Collecting garbage every DB entry cycle will keep the heap low and steady, but will increment the
                    //surviving generations by 1 for every collection (due to forcing major collections?)
                    //Not calling the gc() results in a slow but steady increase of the heap size
                    //Calling the gc() sporadically (10 min or more interval) will keep the heap small and will give the
                    //surviving generations time to re-stabilize before the next gc (due to minor collections?)
                    if(System.currentTimeMillis() - lastGCTime > GC_INTERVAL)
                    {
//                        BackgroundService.AppendLog("COLLECTING GARBAGE @ " + Utilities.DateFormat(System.currentTimeMillis()));
                        lastGCTime = System.currentTimeMillis();
                        System.gc();
                    }
                    
                    currentTick++;
                    currentTick = (alertsDelta * currentTick) >= updateDelta ? 1 : currentTick;
                    retries = 0; //if no exceptions were thrown we can reset  retries to 0                       
                }
                catch (IOException | NullPointerException | NumberFormatException | SQLException | TimeoutException | JSONException e) 
                {
                    if(retries < 10)
                    {
                        timer.cancel();//important to close the timer (thread)
                        Retry();
                        SendAlertToGUI("", Main.BUNDLE.getString("retrySubject") + (retries + 1), 
                                Main.BUNDLE.getString("retry") + e.toString(), propertiesConnection);
                        return;
                    }
                    BackgroundService.AppendLog(e);
                    //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                    if ((boolean) GetFirstItem("alerts_settings", "reqording",propertiesConnection))
                    {
                        PoolAlert(Main.BUNDLE.getString("reqordingHaltedSubject"), 
                            Main.BUNDLE.getString("reqordingHalted")  + e.toString());      
                        SendAlertPool();
                    }
                    if(BackgroundService.GUI == null)
                    {
                        System.out.println(
                                Main.BUNDLE.getString("stoppedReqording") 
                                        + Utilities.DateFormat(System.currentTimeMillis()) + "\n" + e.toString());
                        StopReqording();
                        
                    }
                    else
                    {
                        BackgroundService.GUI.StopReqording();
                        JOptionPane.showMessageDialog(BackgroundService.GUI, 
                                Main.BUNDLE.getString("stoppedReqording")  
                                    + Utilities.DateFormat(System.currentTimeMillis()) + "\n" + e.toString(),
                                Main.BUNDLE.getString("stoppedReqordingTitle"), JOptionPane.ERROR_MESSAGE);
                    }   
                }
            }          
            
        }, 0, alertsDelta); 
    }
    
    /**
     * The Qortal API will sometimes take too long (more than alertsDelta time)<br>
     * Utilities.ReadStringFromURL will TimeOut after 45 seconds. This will cause<br>
     * the ReQording session to halt, inconveniencing the user. To mitigate this,<br> 
     * we retry reqording 30 seconds after an exception is thrown. If 5 consecutive<br> 
     * exceptions are thrown, we halt the Reqording session and send the user an<br>
     * alert (if enabled)
     */
    private void Retry()
    {
        Timer retryTimer = new Timer();
        TimerTask popupTask = new TimerTask()
            {                
                @Override
                public void run()
                {
                    retries++;
                    Reqord(database);
                }
            };    
            retryTimer.schedule(popupTask, 30000); 
    }
    
    /**creates a string arraylist that is populated with the args for InsertIntoDB() method*/
    private ArrayList<String> CreateSQL_StringList()
    {    
        //ONLY GETS CALLED IF CURRENTTICK == 1, CHANGE THIS IF ALERTS SHOULD USE ALERTSDELTA INSTEAD
        ArrayList<String> insertStringList = new ArrayList<>();
        insertStringList.add("node_data");
        insertStringList.add("TIMESTAMP");
        insertStringList.add(String.valueOf(timestamp));
        
        insertStringList.add("RAM_USAGE");
        //ATTENTION: REMOVE RAM USAGE WHEN DONE TESTING     
        if(systemInfo.getOperatingSystem().getProcess(myProcessID) != null) //causing null pointers for some (running jdk 16)
        {
            systemInfo.getOperatingSystem().getProcess(myProcessID).updateAttributes();
            insertStringList.add(String.valueOf(systemInfo.getOperatingSystem().getProcess(myProcessID).getResidentSetSize()));            
        }
        else
            insertStringList.add("0");
        
        for(String columnHeader : nodeDataColumns)
        {
            if(columnHeader.equals("MYBLOCKHEIGHT"))
            {
                insertStringList.add("MYBLOCKHEIGHT");
                insertStringList.add(String.valueOf(myBlockHeight));
                continue;
            }
            if(columnHeader.equals("BLOCKHEIGHT"))
            {
                insertStringList.add("BLOCKHEIGHT");
                insertStringList.add(String.valueOf(chainHeight));
                continue;
            }
            if(columnHeader.equals("NUMBEROFCONNECTIONS"))
            {
                insertStringList.add("NUMBEROFCONNECTIONS");
                insertStringList.add(String.valueOf(numberofconnections));
                continue;
            }
            if(columnHeader.equals("UPTIME"))
            {
                insertStringList.add("UPTIME");
                insertStringList.add(String.valueOf(uptime));
                continue;
            }
            if(columnHeader.equals("ALLKNOWNPEERS"))
            {
                insertStringList.add("ALLKNOWNPEERS");
                insertStringList.add(String.valueOf(allKnownPeers));
                continue;
            }
            if(columnHeader.equals("ALLONLINEMINTERS"))
            {
                insertStringList.add("ALLONLINEMINTERS");
                insertStringList.add(String.valueOf(allOnlineMinters));
                continue;
            }                                  
            if(columnHeader.equals("BYTES_SENT"))
            {
                insertStringList.add("BYTES_SENT");
                insertStringList.add(String.valueOf(totalBytesSent));
                continue;
            }
            if(columnHeader.equals("BYTES_RECEIVED"))
            {
                insertStringList.add("BYTES_RECEIVED");
                insertStringList.add(String.valueOf(totalBytesReceived));
                continue;
            }                             
            if(columnHeader.equals("AVG_BYTES_SENT"))
            {
                insertStringList.add("AVG_BYTES_SENT");
                insertStringList.add(String.valueOf(avrgSentPerMinute));                     
                continue;
            }
            if(columnHeader.equals("AVG_BYTES_RECEIVED"))
            {
                insertStringList.add("AVG_BYTES_RECEIVED");
                insertStringList.add(String.valueOf(avrgReceivedPerMinute));
                continue;
            }                             
            if(columnHeader.equals("CPU_TEMP"))
            {                
                insertStringList.add("CPU_TEMP");
                insertStringList.add(String.valueOf(sensors.getCpuTemperature()));                
                continue;
            }                           
            if(columnHeader.equals("CPU_USAGE"))
            {                
                insertStringList.add("CPU_USAGE");
                insertStringList.add(String.valueOf(cpu_usage));                
                continue;
            }                           
            if(columnHeader.equals("QORTAL_RAM"))
            {                
                insertStringList.add("QORTAL_RAM");
                insertStringList.add(String.valueOf(qortalRAM));                
                continue;
            }                           
            if(columnHeader.equals("BLOCKCHAINSIZE"))
            {
                insertStringList.add("BLOCKCHAINSIZE");
                
                if(blockChainFolder == null)
                    insertStringList.add(String.valueOf(0));
                else
                {
                    File folder = new File(blockChainFolder);
                    long size = Utilities.getDirectorySize(folder);
                    //check if alert enabled
                    if(!chainSizeAlertSent && (boolean)GetFirstItem("alerts_settings", "blockchainsize",propertiesConnection))
                    {
                        long alertSize = (long) GetFirstItem("alerts_settings", "blockchainvalue",propertiesConnection);
                        
                        if(size >= alertSize)
                        {
                            chainSizeAlertSent = true;
                            int sizeMb = (int)((double)size/1000000);
                            String[] split = Main.BUNDLE.getString("bcSizeAlert").split("%%");//0=folder,1=current,2=alert setting
                            PoolAlert(Main.BUNDLE.getString("bcSizeAlertSubject"), 
                                    String.format(split[0] + "%s" + split[1] + "%s" + split[2] + "%s" + split[3],
                                            blockChainFolder,
                                            NumberFormat.getIntegerInstance().format(sizeMb),
                                            NumberFormat.getIntegerInstance().format(alertSize/1000000)));
                        }                        
                    }                    
                    if(folder.isDirectory())
                        insertStringList.add(String.valueOf(size));
                    else
                        insertStringList.add(String.valueOf(0));
                }
            }
            //ATTENTION: IF MORE CODE BLOCKS GET ADDED, DON'T FORGET TO ADD CONTINUE TO PREVIOUSLY LAST CODE BLOCK
            
        }//end for(columnHeaders)   
        
        return insertStringList;
    }
    
    public void ExecuteUpdate(String statementString, Connection c)
    {
        try
        { 
            if(logDbEntries)
                BackgroundService.AppendLog(statementString);

            Statement stmt = c.createStatement();
            stmt.executeUpdate(statementString);
            c.commit();    
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }     
    }
    
    /**Sending multiple e-mails concurrently is not allowed by mail server, we need to pool all alerts and send
     each one with a 5 second delay*/
    private void PoolAlert(String subject, String message)
    {
        message += "\n\nAt time of alert:\n\n"
             + "Node blockheight: " + Utilities.numberFormat(myBlockHeight) +
                "\nChain height: " + Utilities.numberFormat(chainHeight) +
                "\nConnected peers: " + numberofconnections;
        
        alertsPool.add(new AlertItem(subject, message, 0, false));
    }    
    
    private void SendAlertPool()
    {   
        if(alertsPool.isEmpty())
            return;
        
        final Timer sendTimer = new Timer();
        sendTimer.scheduleAtFixedRate(new TimerTask()
        {            
            @Override
            public void run()
            {
                SendAlert(alertsPool.get(0).subject, alertsPool.get(0).message);
                alertsPool.remove(0);
                
                if(alertsPool.isEmpty())
                    sendTimer.cancel();
            }
        }, 0, 5000);
    }
    
    private void SendAlert(String subject, String message)
    {        
         try (Connection c = ConnectionDB.getConnection("properties"))
        {   
            String recipient = "               ";
            
            //if no mailserver was set up, recipient field will stay empty (padded)
            if(TableExists("mail_server", c))
                recipient = (String)GetItemValue("mail_server", "recipient", "id", "0", c);
            
            SendAlertToGUI(recipient, subject, message, c);
            
            if(!(boolean)GetFirstItem("alerts_settings", "emailalerts",c))
            {
                c.close();
                return;
            }           
            
//            System.out.println("UNCOMMENT @ SENDALERT");//DEBUGGING
            
            //send email
            char[] password = Utilities.DecryptPassword(
                    (String)GetItemValue("mail_server", "password", "id", "0", c),
                    (String)GetItemValue("mail_server", "key", "id", "0", c), 
                    (String)GetItemValue("mail_server", "salt", "id", "0", c));
            
            if(password != null)
            {
                if(!Utilities.SendEmail(
                        recipient,
                        (String)GetItemValue("mail_server", "username", "id", "0", c), 
                        String.copyValueOf(password),
                        (String)GetItemValue("mail_server", "smtp", "id", "0", c),
                        (String)GetItemValue("mail_server", "port", "id", "0", c),
                        subject,
                        message))
                {
                    String subject2 = Main.BUNDLE.getString("emailErrorSubject");
                    String message2 = String.format(Main.BUNDLE.getString("emailError") + "%s\n\n%s", subject,message);
                    SendAlertToGUI(recipient, subject2, message2, c);
                }
                Arrays.fill(password, '\0');
            }
            
            c.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        
    }
    
    /**
     * ATTENTION: When inserting a string (varchar) into a H2 database it needs to be
     * encapsulated by single quotes.<br>
     * This means that adding single quotes into the string that we're inserting
     * will cause an SQL exception due to <br>
     * invalid parsing of the statement. Always use double quotes or two single
     * quotes when using quotes here.
     */
    private void SendAlertToGUI(String recipient,String subject,String message, Connection c)
    {
         if(!TableExists("alerts", c))
                CreateTable(new String[]{"alerts","timestamp","long","recipient","varchar(255)",
                    "subject","varchar(255)","message","varchar(MAX)","read","boolean"}, c);
         
         if(message.contains("'"))
                message = message.replace("'", "''");
            
            InsertIntoDB(new String[]{"alerts","timestamp",String.valueOf(System.currentTimeMillis()),
                "recipient",Utilities.SingleQuotedString(recipient),"subject",Utilities.SingleQuotedString(subject),"message",Utilities.SingleQuotedString(message),"read","false"}, c); 
            
            if(BackgroundService.GUI != null)
                BackgroundService.GUI.alertsPanel.PopuateAlertsList();
    }
    
    private void BlocksMintedUpdate(String table, String address) throws  SQLException
    {
        //find out if blocksMinted has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from " + table + " order by timestamp desc"); //gets last inserted by timestamp
        if(!rs.first())
        {
            if(currentTick == 1)
            {
                lastBlockMintedTime = timestamp;
                alertedBlockAddresses.remove(address);
                sql = "insert into " + table + " (timestamp,blocksminted) values ("
                        + timestamp + "," + blocksMinted + ")";
                ExecuteUpdate(sql,dbConnection);                 
            }                               
        }
        else
        {
            int oldValue = rs.getInt("blocksminted");
            if(blocksMinted != oldValue)
            {
                if(currentTick == 1)
                {
                    lastBlockMintedTime = timestamp;
                    
                    if(alertedBlockAddresses.remove(address))
                    {
                        String name = (String) GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address), dbConnection);
                        name = name.isEmpty() ? address : name;
                        PoolAlert("Minting has resumed", "ReQorder has detected that account ''" + name + "'' is minting again. Minting was resumed"
                                + " at " + Utilities.DateFormatShort(System.currentTimeMillis()) + ", current blocks minted is " + blocksMinted + ".");
                    }
                    
                    sql = "insert into " + table + " (timestamp,blocksminted) values (" + 
                            timestamp + "," + blocksMinted + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }
            }
            else
            {
                //only send minting halted alerts if node is not les than 30 blocks behind
                if(chainHeight > 0 && chainHeight - myBlockHeight < 30)
                {
                    //check if alert enabled
                    if ((boolean) GetFirstItem("alerts_settings", "minting",propertiesConnection))
                    {
                        long WARNING_TIME = 900000;//warn if not minted for this amount of time (15 minutes)
                        long timeNotMinted = System.currentTimeMillis() - lastBlockMintedTime;
                        //using a list of addresses to make sure only one alert is sent, using time as flag was not reliable enough
                        if (timeNotMinted > WARNING_TIME && !alertedBlockAddresses.contains(address))
                        {
                            alertedBlockAddresses.add(address);
                            String name = (String) GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address), dbConnection);
                            name = name.isEmpty() ? address : name;
                            String[] split = Main.BUNDLE.getString("mintingHaltedAlert").split("%%");
                            PoolAlert(Main.BUNDLE.getString("mintingHaltedSubject"),
                                    String.format(split[0] + "%s" + split[1] + "%s" + split[2],name, Utilities.MillisToDayHrMin(WARNING_TIME)));
                        }
                    }
                }                    
            }
        }
    }
    
    private void FindBandwidthUsage()
    {       
        long currentBytesSent = 0;
        long currentBytesReceived = 0;

        //these 2 variables are cumulative for as long as the app is running
        for (NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            currentBytesSent += nif.getBytesSent();
            currentBytesReceived += nif.getBytesRecv();
        }        
        
        //Current bytes sent should always be bigger than lastbytes sent
        //If, for some reason Oshi returns a faulty value for getBytes this would result in a negative value for 
        //bytesSent/Rec, which would result in a negative value for totalBytesSent/Rec as well as averageBytesSent/Rec
        long bytesSent = currentBytesSent > lastBytesSent ? currentBytesSent - lastBytesSent : lastBytesSent;
        long bytesReceived = currentBytesReceived > lastBytesReceived ? currentBytesReceived - lastBytesReceived : lastBytesReceived;
        //due to the cumulative nature of OSHI core bytes registration, the first iteration on a subsequent reqording session will 
        //return all the bytes that were sent in between the two sessions. We add 1 to totBytesSent to signal to the next iteration that it can add the
        //bytesSent/Rec calculated above
        totalBytesSent += totalBytesSent == 0 ? 1 : bytesSent;
        totalBytesReceived += totalBytesReceived == 0 ? 1 : bytesReceived;
        
        lastBytesSent = currentBytesSent;
        lastBytesReceived = currentBytesReceived;
        double tempSent = (double)totalBytesReceived / (System.currentTimeMillis() - startTime);
        avrgReceivedPerMinute = (long) tempSent * 60000;
        double tempRec = (double) totalBytesSent / (System.currentTimeMillis() - startTime);
        avrgSentPerMinute = (long) tempRec * 60000;          
    }
    
    private void FindQortalUsage()
    {           
        double d = processor.getSystemCpuLoadBetweenTicks(oldTicks);
        oldTicks = processor.getSystemCpuLoadTicks();        
        cpu_usage = (double) (100d * d);  
        //round to 2 decimals
        double scale = Math.pow(10, 2);
        cpu_usage = Math.round(cpu_usage * scale) / scale;
        
        List<OSProcess> processes = os.getProcesses(0, null);

        for (OSProcess process : processes)
        {
            if (process.getName().equals("java"))
            {
                String dir = process.getCurrentWorkingDirectory();
                if (dir != null && dir.contains("qortal"))
                {
                    process.updateAttributes();
                    qortalRAM = process.getResidentSetSize() / 1000000;
                }

            }
        }
    }
    
    private void BalanceUpdate(String table,String address) throws SQLException
    {            
        //find out if balance has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from " + table + " order by timestamp desc"); //gets last inserted by timestamp
        //if rs.first() -> make sure there is data in the table, if empty insert current data
        if(!rs.first())
        {
            if(currentTick == 1)
            {
                sql = "insert into " + table + " (timestamp,balance) values (" + 
                        timestamp + "," + balance + ")";
                    ExecuteUpdate(sql,dbConnection);                   
            }                          
        }
        else 
        {
            if(currentTick == 1)
            {
//                treshold/epsilon value to avoid frequent balance updates for miniscule amounts
//                however, we'll need to make the epsilon adjustable through the UI, if Qort's value were to drastically
//                increase, those miniscule values could become quite significant
                if(Math.abs(balance - rs.getDouble("balance")) > balanceUpdateTreshold)
                {
                    sql = "insert into " + table + " (timestamp,balance) values (" + 
                            timestamp + "," + balance + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }       
            }
            if((boolean)GetItemValue("my_watchlist", "alert", "address", Utilities.SingleQuotedString(address), dbConnection))
            {
                String name = (String)GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address), dbConnection);
                name = name.isEmpty() ? address : name;
                
                double alertValue = (double)GetItemValue("my_watchlist", "alertvalue", "address", Utilities.SingleQuotedString(address), dbConnection);
                //alertvalue is stored as a negative value if alerting for below indicated value
                boolean alertForExceed = alertValue >= 0;
                alertValue = Math.abs(alertValue);
                
                if(alertForExceed && balance >= alertValue)
                {
                    String[] split = Main.BUNDLE.getString("balanceAlertMessage").split("%%");
                    PoolAlert(String.format(Main.BUNDLE.getString("balanceAlertSubject") + "%.5f QORT",alertValue), 
                            String.format(split[0] + "%s" + split[1] + "%.5f" + split[2] + "%.5f" + split[3],name,alertValue,balance));
                    //disable alert
                    ChangeValue("my_watchlist", "alert", "false", "address", Utilities.SingleQuotedString(address), dbConnection);
                }
                if(!alertForExceed && balance <= alertValue)
                {                    
                    String[] split = Main.BUNDLE.getString("balanceAlertMessage2").split("%%");
                    PoolAlert(String.format(Main.BUNDLE.getString("balanceAlertSubject2") + "%.5f QORT",alertValue), 
                            String.format(split[0] + "%s" + split[1] + "%.5f" + split[2] + "%.5f" + split[3],name,alertValue,balance));
                    //disable alert
                    ChangeValue("my_watchlist", "alert", "false", "address", Utilities.SingleQuotedString(address), dbConnection);
                }                
            }
        }        
    }
    
    private void LevelUpdate(String table, String address)throws SQLException
    {   
        //Levelling alerts only get sent on database inserts, so only continue on tick 1
        if(currentTick != 1)
            return;
        
        //find out if level has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from " + table + " order by timestamp desc"); //gets last inserted by timestamp
        if(!rs.first())
        {
                sql = "insert into " + table + " (timestamp,level) values (" + 
                        timestamp + "," + level + ")";
                ExecuteUpdate(sql,dbConnection);                               
        }
        else
        {
            int oldLevel = rs.getInt("level");
            if(level != oldLevel)
            {
                sql = "insert into " + table + " (timestamp,level) values (" + 
                        timestamp + "," + level + ")";
                ExecuteUpdate(sql,dbConnection);  
                String name = (String) GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address), dbConnection);
                name = name.isEmpty() ? address : name;
                   //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                if ((boolean) GetFirstItem("alerts_settings", "levelling",propertiesConnection))
                {
                    String[] split = Main.BUNDLE.getString("levellingAlert").split("%%");
                    PoolAlert(Main.BUNDLE.getString("levellingSubject"), String.format(
                            split[0] + "%d" + split[1] + "%d" + split[2] + "%s" + split[3], oldLevel,level,name));
                }
            }                                
        }
    }
    
    private void BuildversionUpdate() throws SQLException
    {
        //Buildversion alerts only get sent on database inserts, so only continue on tick 1
        if(currentTick != 1)
            return;     
        
        //find out if buildversion has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from buildversion order by timestamp desc"); //gets last inserted by timestamp
        //if there are no rows in table
        if(!rs.first())
        {
                sql = "insert into buildversion (timestamp,buildversion) values (" + 
                        timestamp + "," + buildVersion + ")";
                ExecuteUpdate(sql,dbConnection);                              
        }
        else
        {
            String oldversion = Utilities.SingleQuotedString(rs.getString("buildversion"));
            if (!buildVersion.equals(oldversion))
            {
                sql = "insert into buildversion (timestamp,buildversion) values ("
                        + timestamp + "," + buildVersion + ")";
                ExecuteUpdate(sql, dbConnection);
                //check if alert enabled (no need for alert sent flag as buildversion is rarely updated)
                if((boolean) GetFirstItem("alerts_settings", "core_update",propertiesConnection))
                {
                    String[] split = Main.BUNDLE.getString("coreUpdateAlert").split("%%");
                    //use 2x single quote to escape varchar required single quote
                    PoolAlert(Main.BUNDLE.getString("coreUpdateSubject"), String.format(
                            split[0] + "%s" + split[1] + "%s" + split[2], oldversion, buildVersion));
                }
            }                                 
        } 
    }
    
    private void LtcPriceUpdate() throws SQLException
    {   
        if(!TableExists("ltcprice", dbConnection))
            return;
        
        //find out if ltcprice has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from ltcprice order by timestamp desc"); //gets last inserted by timestamp
        //if there are no rows in table
        if(!rs.first())
        {
            if(currentTick == 1)
            {
                sql = "insert into ltcprice (timestamp,ltcprice) values (" + 
                        timestamp + "," + LTCprice + ")";
                ExecuteUpdate(sql,dbConnection);                   
            }                           
        }
        else
        {       
            long lastPrice = rs.getLong("ltcprice");
            if(currentTick == 1)
            {    
                boolean insertEntry = !usePriceTreshold || usePriceTreshold && Math.abs(lastPrice - LTCprice) > LTC_UPDATE_DELTA;
                if(insertEntry)
                {
                    sql = "insert into ltcprice (timestamp,ltcprice) values (" + 
                            timestamp + "," + LTCprice + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }  
            }
            //will only be triggered once, after which user will have to set a new alert
            if((boolean)GetFirstItem("alerts_settings", "ltcprice",propertiesConnection))
            {                
                long alertValue = (long)GetFirstItem("alerts_settings", "ltcvalue",propertiesConnection);
                //if stored alert value is negative, it means the user wants to be informed that the price has 
                //gone below specified (absolute) value, this saves us keeping an extra flag for increase/decrease
                boolean alertForExceed = alertValue >= 0;
                alertValue = Math.abs(alertValue);
                
                boolean sendAlert = false;
                
                if(alertForExceed && LTCprice - alertValue > 0)//ltcprice has increased above alertvalue
                    sendAlert = true;
                if(!alertForExceed && LTCprice - alertValue < 0)//ltcprice has decreased below alertvalue
                    sendAlert = true;
                
                if(sendAlert)
                {
                    String[] split = Main.BUNDLE.getString("ltcAlertMessage").split("%%");//0=up/down type,1=last price,2=current price,3=alertvalue
                    PoolAlert(Main.BUNDLE.getString("ltcAlertSubject"), String.format(
                            split[0] + "%s" + split[1] + "%.5f" + split[2] + "%.5f" + split[3] + "%.5f" + split[4],
                                alertForExceed ? Main.BUNDLE.getString("hasExceeded") : Main.BUNDLE.getString("isNowBelow"),
                                ((double)lastPrice/100000000),((double)LTCprice/100000000),((double)alertValue/100000000)));
                    //disable alert
                    ChangeValue("alerts_settings", "ltcprice", "false", "id", "0", propertiesConnection);
                    if(BackgroundService.GUI != null)
                        BackgroundService.GUI.alertsPanel.ltcAlertBox.setSelected(false);
                }
            }            
        } 
    }
        
    private void UsdPriceUpdate() throws SQLException
    {   
        if(!TableExists("usdprice", dbConnection))
            return;
        
        //find out if ltcprice has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from usdprice order by timestamp desc"); //gets last inserted by timestamp
        //if there are no rows in table
        if(!rs.first())
        {
            if(currentTick == 1)
            {
                sql = "insert into usdprice (timestamp,usdprice) values (" + 
                        timestamp + "," + USDprice + ")"; 
                ExecuteUpdate(sql,dbConnection);                   
            }                           
        }
        else
        {       
            double lastPrice = rs.getDouble("usdprice");
            if(currentTick == 1)
            {    
                boolean insertEntry = !usePriceTreshold || usePriceTreshold && Math.abs(lastPrice - USDprice) > USD_UPDATE_DELTA;
                if(insertEntry)
                {
                    sql = "insert into usdprice (timestamp,usdprice) values (" + 
                            timestamp + "," + USDprice + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }  
            }
            //will only be triggered once, after which user will have to set a new alert
            if((boolean)GetFirstItem("alerts_settings", "usdprice",propertiesConnection))
            {                
                double alertValue = (double)GetFirstItem("alerts_settings", "usdvalue",propertiesConnection);
                //if stored alert value is negative, it means the user wants to be informed that the price has 
                //gone below specified (absolute) value, this saves us keeping an extra flag for increase/decrease
                boolean alertForExceed = alertValue >= 0;
                alertValue = Math.abs(alertValue);
                
                boolean sendAlert = false;
                
                if(alertForExceed && USDprice - alertValue > 0)//usd price has increased above alertvalue
                    sendAlert = true;
                if(!alertForExceed && USDprice - alertValue < 0)//usd price has decreased below alertvalue
                    sendAlert = true;
                
                if(sendAlert)
                {
                    String[] split = Main.BUNDLE.getString("usdAlertMessage").split("%%");//0=up/down type,1=last price,2=current price,3=alertvalue
                    PoolAlert(Main.BUNDLE.getString("usdAlertSubject"), String.format(
                            split[0] + "%s" + split[1] + "%.5f" + split[2] + "%.5f" + split[3] + "%.5f" + split[4],
                                alertForExceed ? Main.BUNDLE.getString("hasExceeded") : Main.BUNDLE.getString("isNowBelow"),
                                lastPrice,USDprice,alertValue));
                    //disable alert
                    ChangeValue("alerts_settings", "usdprice", "false", "id", "0", propertiesConnection);
                    if(BackgroundService.GUI != null)
                        BackgroundService.GUI.alertsPanel.ltcAlertBox.setSelected(false);
                }
            }            
        } 
    }
    
    private void DogePriceUpdate() throws SQLException
    {  
        if(!TableExists("dogeprice", dbConnection))
            return;
        
        //find out if ltcprice has changed since last entry
        statement = dbConnection.createStatement();
        rs = statement.executeQuery("select top(1) * from dogeprice order by timestamp desc"); //gets last inserted by timestamp
        //if there are no rows in table
        if(!rs.first())
        {
            if(currentTick == 1)
            {
                sql = "insert into dogeprice (timestamp,dogeprice) values (" + 
                        timestamp + "," + DogePrice + ")";
                ExecuteUpdate(sql,dbConnection);                   
            }                           
        }
        else
        {       
            long lastPrice = rs.getLong("dogeprice");
            if(currentTick == 1)
            {    
                boolean insertEntry = !usePriceTreshold || usePriceTreshold && Math.abs(lastPrice - DogePrice) > DOGE_UPDATE_DELTA;
                if(insertEntry)
                {
                    sql = "insert into dogeprice (timestamp,dogeprice) values (" + 
                            timestamp + "," + DogePrice + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }  
            }
            //will only be triggered once, after which user will have to set a new alert
            if((boolean)GetFirstItem("alerts_settings", "dogeprice",propertiesConnection))
            {   
                long alertValue = (long)GetFirstItem("alerts_settings", "dogevalue",propertiesConnection);
                //if stored alert value is negative, it means the user wants to be informed that the price has 
                //gone below specified (absolute) value, this saves us keeping an extra flag for increase/decrease
                boolean alertForExceed = alertValue >= 0;
                alertValue = Math.abs(alertValue);
                
                boolean sendAlert = false;
                
                if(alertForExceed && DogePrice - alertValue > 0)//DogePrice has increased above alertvalue
                    sendAlert = true;
                if(!alertForExceed && DogePrice - alertValue < 0)//DogePrice has decreased below alertvalue
                    sendAlert = true;                
                
                if(sendAlert)
                {
                    String[] split = Main.BUNDLE.getString("dogeAlertMessage").split("%%");//0=up/down type,1=last price,2=current price,3=alertvalue
                    PoolAlert(Main.BUNDLE.getString("dogeAlertSubject"), String.format(
                            split[0] + "%s" + split[1] + "%.5f" + split[2] + "%.5f" + split[3] + "%.5f" + split[4],
                                alertForExceed ? Main.BUNDLE.getString("hasExceeded") : Main.BUNDLE.getString("isNowBelow"),
                                    ((double)lastPrice/100000000),((double)DogePrice/100000000),((double)alertValue/100000000)));
                    //disable alert
                    ChangeValue("alerts_settings", "dogeprice", "false", "id", "0", propertiesConnection);
                    if(BackgroundService.GUI != null)
                        BackgroundService.GUI.alertsPanel.dogeAlertBox.setSelected(false);
                }
            }            
        } 
    }
    
}//end class