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
    private final Sensors sensors;
    protected static String dbFolderOS;
    private final int myProcessID;
    protected final String myOS;
    protected String blockChainFolder;
    protected static ArrayList<String> dbFiles;
    protected static ArrayList<String> encryptedFiles;
    protected static boolean logDbEntries;
    protected boolean backupEnabled = true;
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
        interfaces = new LinkedList<>();
        interfaces = systemInfo.getHardware().getNetworkIFs();
        myProcessID = systemInfo.getOperatingSystem().getProcessId();
        processor = systemInfo.getHardware().getProcessor();
        sensors = systemInfo.getHardware().getSensors();   
        myOS = systemInfo.getOperatingSystem().getFamily();
        
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
                InsertIntoDB(new String[]{"databases","database",Utilities.ToH2Char(file)},c);
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

            for(String database : encryptedFiles)
            {
                //don't move or delete db access file
                if(database.equals("dba"))
                    continue;
                
                File file = new File(dbFolderOS + "/" + database + ".mv.db");
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
                    removedFiles.add(database);
                }
            }
            removedFiles.forEach(database ->
            {
                encryptedFiles.remove(database);
                dbFiles.remove(database);
            }); 
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    protected void MoveInaccessibleFile(String fileName)
    {
        boolean keepFile = JOptionPane.showConfirmDialog(BackgroundService.GUI, 
                        Utilities.AllignCenterHTML(String.format(
                                "The database '%s' is inaccessible for this account<br/>"
                                        + "Do you want to save it to the 'inaccessible' folder?"
                                        + "<br/><br/>Choosing 'No' will delete this file", fileName)), 
                        "Delete or move?", 
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
                    Utilities.AllignCenterHTML(String.format("'%s' was moved to:<br/>%s",fileName,newFile)),
                    "Database inaccessible", JOptionPane.WARNING_MESSAGE);
            else
                JOptionPane.showMessageDialog(BackgroundService.GUI, 
                    Utilities.AllignCenterHTML(String.format("'%s' was deleted",fileName)),
                    "Database inaccessible", JOptionPane.WARNING_MESSAGE);
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
        encryptedFiles.stream().filter(database -> 
                !(database.equals("dba"))).filter(database -> 
                !(database.equals("properties"))).filter(database -> 
                        (!ConnectionDB.CanConnect(database, dbPassword))).forEachOrdered(database ->
        {
            removeFiles.add(database);
        });
        removeFiles.forEach(file ->
        {
            MoveInaccessibleFile(file);
        });  
        
        //if login was successfull and all databases were accessible, make a backup of auth files and databases
        if(backupEnabled && removeFiles.isEmpty())
            AccountBackup();        
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
        
        ArrayList<File> backupFiles = new ArrayList<>();
        File initFile = new File(System.getProperty("user.dir") + "/bin/init");
        //initFile could be non-existent if user has not set a password, we still want to be able to backup the account in that case
        if(initFile.exists())
            backupFiles.add(initFile);
        backupFiles.add(new File(System.getProperty("user.dir") + "/bin/auth"));
        backupFiles.add(new File(System.getProperty("user.dir") + "/bin/dba.mv.db"));
        
         for(String database : dbFiles)
         {
             //added above (different folder)
             if(database.equals("dba"))
                 continue;
             
             backupFiles.add(new File(dbFolderOS + "/" + database + ".mv.db"));
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
            System.out.println("Account backup complete");
        }
        catch (IOException e)
        {      
            newFile.delete();
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog("ACCOUNT BACKUP FAILED\n" + e.toString());
            System.out.println("ACCOUNT BACKUP FAILED");
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
                JOptionPane.showMessageDialog(null, "Invalid file: file must be named 'restore.zip'");
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
                    Utilities.AllignCenterHTML("Do you want to import this account?<br/><br/>"
                            + "This will replace your current properties file.<br/><br/>"
                            + "The imported account will not have access to any<br/>"
                            + "encrypted databases created with the current account."),
                    "Import account?",
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
                    JOptionPane.showMessageDialog(BackgroundService.GUI, "Account imported");     
                    return true;//success
                }
                catch (HeadlessException | IOException | NullPointerException e)
                {
                    BackgroundService.AppendLog(e);
                    JOptionPane.showMessageDialog(BackgroundService.GUI, "Error restoring account\n\n" + e.toString());
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
                "my_watchlist", "id", "name", Utilities.ToH2Char(nameOrAddress), connection);
        if (ID_Object == null)
        {
            ID_Object = GetItemValue(
                    "my_watchlist", "id", "address", Utilities.ToH2Char(nameOrAddress), connection);
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
                "username",Utilities.ToH2Char(username),
                "password",Utilities.ToH2Char(password),
                "smtp",Utilities.ToH2Char(smtp),
                "port",Utilities.ToH2Char(port),
                "recipient",Utilities.ToH2Char(recipient),
                "key",Utilities.ToH2Char(key),
                "salt",Utilities.ToH2Char(salt)}, connection);
            
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

                 int choice = JOptionPane.showConfirmDialog(
                         BackgroundService.GUI,
                         Utilities.AllignCenterHTML("Node settings have changed<br/>"
                            + "Choose 'OK' to continue (this will delete any node data stored in '" + args[0] + "')<br/>"
                            + "If you wish to keep your node data, choose 'Cancel' and create a new database"),
                         "Delete node data?",
                         JOptionPane.OK_CANCEL_OPTION,
                         JOptionPane.WARNING_MESSAGE
                 );

                 if(choice == JOptionPane.CANCEL_OPTION)
                     return false;

                //easier to just drop the entire table and re-create it than to change the values for every column
                ExecuteUpdate("delete from node_prefs",c);             
                ExecuteUpdate("drop table node_data",c);
                if(TableExists("ltcprice", c))
                    ExecuteUpdate("drop table ltcprice", c);   
                if(TableExists("dogeprice", c))
                    ExecuteUpdate("drop table dogeprice", c);
            }
            else
            {
                CreateTable(new String[]{"node_prefs","id","tinyint","blockheight","boolean","myblockheight","boolean",
                    "numberofconnections","boolean","uptime","boolean","allknownpeers","boolean","allonlineminters","boolean",
                    "ltcprice","boolean","dogeprice","boolean","data_usage","boolean","cpu_temp","boolean","blockchainsize","boolean","updatedelta","int"},c);
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
               "cpu_temp","double","blockchainsize","long","RAM_USAGE","LONG"},c);
           //ATTENTION: REMOVE RAM_USAGE,LONG WHEN DONE TESTING MEMORY USAGE??
           CreateTable(new String[]{"buildversion","timestamp","long","buildversion","varchar(50)"},c);

           //since data usage creates 2 seperate columns, we need to check for it seperately
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
    
                            //break from for args[] iterations
                            break;
                       }
                   }
                   boolean itemSelected = Boolean.parseBoolean(args[i+1]);
                   if(args[i].toUpperCase().equals(column))
                   {
                       if(!itemSelected)
                       {
                            ExecuteUpdate("alter table node_data drop column " + column,c);                   
                       }
                       break;
                   }
               }
            }            
            
           //check which price updates are set to true and create tables for them
           for(int i = 1;i<args.length - 1;i+=2)
           { 
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
             Utilities.ReadStringFromURL("http://localhost:12391/addresses/" + address);   

            //check if address exists, using the merge statement in h2 would be cleaner 
            //but this way we can notify the user that the address already exists
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery("select address from " + watchlist); //gets all stored addresses
            while(resultSet.next())
            {
                if(address.equals(resultSet.getString("address")))
                {
                    JOptionPane.showMessageDialog(null,"Address '" + address + "' already exists in watchlist '" + watchlist + "'");
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
            String jsString = Utilities.ReadStringFromURL("http://localhost:12391/names/address/" + address);
            JSONArray jSONArray = new JSONArray(jsString);
            String name = Utilities.ToH2Char("");
            if(jSONArray.length() > 0)
            {
                JSONObject jsObject = jSONArray.getJSONObject(0);          
                name =  Utilities.ToH2Char(jsObject.getString("name"));                
            }

            //add address
            String sqlString = "insert into "+ watchlist + " (address,id,name,blocksminted,level,balance,balancetreshold) values ('" + address + "'," + ID + "," + name + ",true,true,true,0.1);";
            ExecuteUpdate(sqlString,c); 
            
            c.close();
            return true;            
        }
        catch(ConnectException e)
        {
                JOptionPane.showMessageDialog(null,"Cannot connect to core\n Please make sure the Qortal core is running\n");
                return false;            
        }
        catch(IOException e)
        {            
            JOptionPane.showMessageDialog(null, "Error: invalid address.\n" + address + "\n");
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
                    "address",Utilities.ToH2Char(resultSet.getString("address")),
                    "name",Utilities.ToH2Char(resultSet.getString("name")),
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
                        Utilities.AllignCenterHTML(
                                    "Could not access CPU temperature sensors<br/>"
                                + "On Windows systems please make sure<br/>"
                                + "'Open Hardware Monitor' is installed and running<br/><br/>"
                                + "Otherwise, deselect the CPU temperature checkbox<br/><br/>"
                                + "Session will continue, snapshots will not include CPU temp data"), 
                        JOptionPane.PLAIN_MESSAGE);

                JDialog dlg = jOptionPane.createDialog("Please install and run 'Open Hardware Monitor'");
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
            if(TableExists("alerts_settings", propsConnection))
            {
                ArrayList<String> invalids = new ArrayList<>();
                
                try (Connection dbConn = ConnectionDB.getConnection(database))
                {
                    if ((boolean) GetFirstItem("alerts_settings", "blockchainsize", propsConnection))
                    {
                        if (!(boolean) GetFirstItem("node_prefs", "blockchainsize", dbConn))
                            {
                                invalids.add("Blockchain size (blockchain folder not set)");
                                invalids.add("Space left (blockchain folder not set)");
                            }           
                    }
                     if ((boolean) GetFirstItem("alerts_settings", "ltcprice", propsConnection))
                    {
                        if (!(boolean) GetFirstItem("node_prefs", "ltcprice", dbConn))
                            invalids.add("LTC price");              
                    }
                     if ((boolean) GetFirstItem("alerts_settings", "dogeprice", propsConnection))
                    {
                        if (!(boolean) GetFirstItem("node_prefs", "dogeprice", dbConn))
                            invalids.add("Doge price");              
                    }
                     if (!TableExists(" my_watchlist", dbConn))  //GetFirstItem("my_watchlist", "id", dbConn) == null)//if table is empty
                    {
                        if ((boolean) GetFirstItem("alerts_settings", "minting", propsConnection))
                            invalids.add("Minting halted (no watchlist applied)");
                        if ((boolean) GetFirstItem("alerts_settings", "levelling", propsConnection))
                            invalids.add("Levelling updates (no watchlist applied)");
                        if ((boolean) GetFirstItem("alerts_settings", "name_reg", propsConnection))
                            invalids.add("Name registration (no watchlist applied)");                 
                    }
                }
                
                if(invalids.isEmpty())
                    return;
                
                String paneMessage = "The following alerts are enabled but can not be triggered:<br/><br/>";
                paneMessage = invalids.stream().map(invalid -> invalid + "<br/>").reduce(paneMessage, String::concat);
                paneMessage += "<br/>In order to receive these alerts you'll<br/>need to reqord the corresponding data.";
                
                JOptionPane.showMessageDialog(BackgroundService.GUI, 
                        Utilities.AllignCenterHTML(paneMessage), "Alerts warning", JOptionPane.WARNING_MESSAGE);
            }
            
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
                            Utilities.AllignCenterHTML("Could not find blockchain folder, please locate and set it<br/>"
                                    + "or disable the checkmark in your database options panel<br/><br/>"
                                    + "The blockchain folder is inside your Qortal folder and is named 'db'"),
                            "Locate blockchain folder", JOptionPane.QUESTION_MESSAGE);
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

                            CreateTable(new String[]{"blockchain_folder","id","tinyint","blockchain_folder","varchar(255)"}, c);
                            InsertIntoDB(new String[]{"blockchain_folder","id","0","blockchain_folder",Utilities.ToH2Char(blockChainFolder)}, c);
                        }
                        else
                        {
                            JOptionPane.showMessageDialog(BackgroundService.GUI,
                            Utilities.AllignCenterHTML("Invalid folder, folder name must be 'db'<br/><br/>"
                                    + "Blockchain folder is not set. All 'Blockchain size' entries will default to '0'"),
                            "Invalid blockchain folder", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(BackgroundService.GUI,
                            "Blockchain folder is not set. All 'Blockchain size' entries will default to '0'",
                            "Blockchain folder not set", JOptionPane.WARNING_MESSAGE);
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
        
        String subject = "ReQorder status update";
        String message = "ReQorder status update:\n\n"
                + "Your Qortal node is online, ReQording session is active.\n\n";
        
        if((boolean)GetFirstItem("alerts_settings", "shownodeinfo", propertiesConnection))
        {
            if(blockHeight - myBlockHeight >= 30)
                message += String.format("WARNING: Your Qortal node blockheight is lagging by %d blocks.\n\n", blockHeight - myBlockHeight);
            
            message += String.format("Blockheight node: %s\n"
                    + "Blockheight chain: %s\n"
                    + "Qortal core uptime: %s\n"
                    + "Qortal core build version: %s\n"
                    + "Connected peers: %d\n"
                    + "Minters online: %d\n"
                    + "All known peers: %d\n"
                    + "Average download rate per day: %.2f Mb\n"
                    + "Average upload rate per day: %.2f Mb\n", 
                    NumberFormat.getIntegerInstance().format(myBlockHeight),
                    NumberFormat.getIntegerInstance().format(blockHeight),
                    Utilities.MillisToDayHrMin(uptime),
                    buildVersion.substring(1, buildVersion.length() - 2),
                    numberofconnections,allOnlineMinters,allKnownPeers,
                    (double)(avrgReceivedPerMinute * 1440) / 1000000, (double)(avrgSentPerMinute * 1440) / 1000000);

            if (blockChainFolder != null)
            {
                File folder = new File(blockChainFolder);
                long size = Utilities.getDirectorySize(folder);
                int sizeMb = (int) ((double) size / 1000000);
                message += String.format("Blockchain size: %s Mb\n"
                                                        + "Space left on disk : %s Mb\n", 
                                                        NumberFormat.getIntegerInstance().format(sizeMb),
                                                        NumberFormat.getIntegerInstance().format(folder.getFreeSpace() / 1000000));
            }

            try
            {        
                String jsString = Utilities.ReadStringFromURL("http://localhost:12391/admin/mintingaccounts");
                JSONArray jSONArray = new JSONArray(jsString);
                //If there's no minting account set we'll get a nullpointer exception
                if (jSONArray.length() > 0)
                {
                    JSONObject jso = jSONArray.getJSONObject(0);
                    String myMintingAddress = jso.getString("mintingAccount");
                    double myBalance = Double.parseDouble(Utilities.ReadStringFromURL("http://localhost:12391/addresses/balance/" + myMintingAddress));
                    jsString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/" + myMintingAddress);
                    jso = new JSONObject(jsString);
                    int myLevel = jso.getInt("level");
                    blocksMinted = jso.getInt("blocksMinted");

                    message += String.format("\n"
                            + "Active minting account: %s\n"
                            + "Blocks minted: %s\n"
                            + "Balance: %.5f QORT\n"
                            + "Level: %d", myMintingAddress, NumberFormat.getIntegerInstance().format(blocksMinted), myBalance, myLevel);
                }
                else
                    message += "\n\nNo active minting account found.";    
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
//            String filePath = (String) GetItemValue("blockchain_folder", "blockchain_folder", "id", "0", m_connection2);
            File file = new File(blockChainFolder);//new File(filePath);
            if (!spaceLeftAlertSent && file.getFreeSpace() <= spaceLeftValue)
            {
                PoolAlert("Disk space alert",
                        String.format("ReQorder has detected that the free space remaining on the disk drive containing "
                                + "your blockchain folder is below the amount indicated in your alert settings.\n\n"
                                + "Blockchain folder : ''%s''\n\nSpace remaining : %s Mb\nAlert setting : %s Mb",
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
            if (!outOfSyncAlertSent && blockHeight - myBlockHeight >= 30)
            {
                PoolAlert("Out of sync alert",
                        String.format("ReQorder has detected that your Qortal node blockheight is "
                                + "lagging %d blocks behind the chain blockheight.\n\n",
                                blockHeight - myBlockHeight));
                outOfSyncAlertSent = true;
            }
        }
    }
    
    public void SetBlockchainFolder()
    {
         try (Connection c = ConnectionDB.getConnection("properties"))
        {
             JOptionPane.showMessageDialog(BackgroundService.GUI,
                        Utilities.AllignCenterHTML("Pease locate and set your blockchain folder<br/><br/>"
                                + "The blockchain folder is inside your Qortal folder and is named 'db'"),
                        "Locate blockchain folder", JOptionPane.QUESTION_MESSAGE);
             
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
                            InsertIntoDB(new String[]{"blockchain_folder","id","0","blockchain_folder",Utilities.ToH2Char(blockChainFolder)}, c);                            
                        }
                        else
                            ChangeValue("blockchain_folder", "blockchain_folder", Utilities.ToH2Char(blockChainFolder), "id", "0", c);
                        
                        BackgroundService.GUI.monitorPanel.blockChainFolder = blockChainFolder;
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(BackgroundService.GUI,
                        Utilities.AllignCenterHTML("Invalid folder, folder name must be 'db'<br/><br/>"
                                + "Blockchain folder is not set. All 'Blockchain size' entries will default to '0'"),
                        "Invalid blockchain folder", JOptionPane.WARNING_MESSAGE);
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
            "spaceleft","boolean","spaceleftvalue","long","ltcprice","boolean","ltcvalue","long","dogeprice","boolean","dogevalue","long",
            "emailalerts","boolean","statusalerts","boolean","shownodeinfo","boolean","statusinterval","tinyint"}, connection);

        //set all values to default
        InsertIntoDB(new String[]{ "alerts_settings","id","0","reqording","false","out_of_sync","false","minting","false",
            "core_update","false","levelling","false","name_reg","false","blockchainsize","false","blockchainvalue","0",
            "spaceleft","false","spaceleftvalue","0","ltcprice","false","ltcvalue","0","dogeprice","false","dogevalue","0",
            "emailalerts","false","statusalerts","false","shownodeinfo","false","statusinterval","1"}, connection);               
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
    private final int LTC_UPDATE_DELTA = 10000;
    private final int DOGE_UPDATE_DELTA = 1000;
    private int myBlockHeight;
    private int blockHeight;
    private int numberofconnections;
    private int blocksMinted;
    private int level;
    private double balance;
    private long LTCprice;
    private long BTCprice;
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
    //for some alerts (like blocksminted, we want to check more often than 
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
    
    public void StopReqording()
    {        
        timer.cancel();
        System.out.println("ReQording Session has stopped");        
        System.gc();
    }
    
    public void Reqord(String database)
    {
        timer = new Timer();
        alertedBlockAddresses = new ArrayList<>();
        alertsPool = new ArrayList<>();
        chainSizeAlertSent = false;
        spaceLeftAlertSent = false;
        outOfSyncAlertSent = false;
        lastStatusAlertTime = System.currentTimeMillis();
        
//        System.out.println("RESTORE ALERTSDELTA TO 60000");//FOR TESTING 
        
        //get the items to update and update delta
        try
        {
            dbConnection = ConnectionDB.getConnection(database);
            nodeDataColumns = GetColumnHeaders("node_data",dbConnection);
            updateDelta = (int) GetItemValue("node_prefs", "updatedelta", "id", "0",dbConnection);            
            dbConnection.close();
            propertiesConnection = ConnectionDB.getConnection("properties");
            if(!TableExists("alerts_settings", propertiesConnection))
                CreateAlertsSettingsTable(propertiesConnection);
            propertiesConnection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }    
                
        //updates to the database only occur on tick 1, alert checks occur every tick
        currentTick = 1;
        lastBlockMintedTime = System.currentTimeMillis();
                
//        System.out.println("UPDATE DELTA = " + updateDelta + " , SETTING TO 30000");//FOR TESTING
//        updateDelta = 30000;
        
        startTime = System.currentTimeMillis();
        totalBytesSent = 0;
        totalBytesReceived = 0;
        for(NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            lastBytesSent += nif.getBytesSent();
            lastBytesReceived += nif.getBytesRecv();
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
                    System.out.println(
                            String.format("ReQording session time: %s  |  %d snapshots taken", Utilities.MillisToDayHrMin(sessionTime),snapShots));
                    sessionTime += updateDelta;                    
                }
//                System.out.println("Starting update @ " + Utilities.TimeFormat(System.currentTimeMillis()));
                  
                FindBandwidthUsage();
                
//                System.out.println("Mb sent = " + (double) bytesSent / 1000000);
//                System.out.println("Mb received = " + (double) bytesReceived / 1000000); 
                
                try 
                {    
                    //open/close connections every update (open at start of try, when catching exceptions we need connection2 to be open)
                    dbConnection = ConnectionDB.getConnection(database);   
                    propertiesConnection = ConnectionDB.getConnection("properties");
                    
                    //We get all the variables regardless of if the user selected them, this is not very costly and saves us from
                    //checking for every  variable, which would probably result in more lines of code
                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/admin/status"); 
                    jSONObject = new JSONObject(jsonString);

                    timestamp = System.currentTimeMillis();
                    myBlockHeight = jSONObject.getInt("height");
                    numberofconnections = jSONObject.getInt("numberOfConnections");
                    blockHeight = Utilities.FindChainHeight();

                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/admin/info");
                    jSONObject = new JSONObject(jsonString);
                    uptime = jSONObject.getLong("uptime");
                    buildVersion = Utilities.ToH2Char(jSONObject.getString("buildVersion"));

                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/peers/known");
                    JSONArray jsonArray = new JSONArray(jsonString);
                    allKnownPeers = jsonArray.length();
                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/online");
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
                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/crosschain/price/LITECOIN?maxtrades=10");
                    LTCprice = Long.parseLong(jsonString);                    
                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/crosschain/price/DOGECOIN?maxtrades=10");
                    DogePrice = Long.parseLong(jsonString);
//                        this url returns the price based on the last 10 trades, which apparently took place when btc was around
//                        $70.000. Until btc trades are re-instated we should not register the btc/qort ratio
//                        jsonString = Utilities.ReadStringFromURL("http://localhost:12391/crosschain/price/BITCOIN?maxtrades=10");
//                        BTCprice = Long.parseLong(jsonString);  
                    
                    StatusAlert();
                    OutOfSyncAlert();
                    SpaceLeftAlert();
                    BuildversionUpdate();
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
                            jsonString = Utilities.ReadStringFromURL("http://localhost:12391/names/address/" + address);
                            jsonArray = new JSONArray(jsonString);
                            if(jsonArray.length() > 0)
                            {
                                jSONObject = jsonArray.getJSONObject(0);
                                String name = jSONObject.getString("name");                            
                                String nameEntry = (String) GetItemValue("my_watchlist", "name", "address", Utilities.ToH2Char(address), dbConnection);
                                if(!name.equals(nameEntry))
                                {
                                    String update = String.format("Changing registered name entry from '%s' to '%s'", nameEntry,name);
                                    ChangeValue("my_watchlist", "name", name, "address", Utilities.ToH2Char(address), dbConnection);
                                    System.out.println(update);
                                    BackgroundService.AppendLog(update); 
                                     //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                                    if ((boolean) GetFirstItem("alerts_settings", "name_reg",propertiesConnection))
                                    {
                                        PoolAlert("Qortal account name registration detected", String.format(
                                                "ReQorder has detected a name registration for Qortal account '%s'. The newly registered name is '%s'.", 
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
                                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/" + address);
                                jSONObject = new JSONObject(jsonString);
                                timestamp = System.currentTimeMillis();
                                blocksMinted = jSONObject.getInt("blocksMinted");
                                BlocksMintedUpdate(table, address);                                    
                            }
                            if(table.endsWith("LEVEL"))
                            {
                                ID = table.substring(11, table.length() - 6);
                                address = (String) GetItemValue("my_watchlist", "address", "ID", ID,dbConnection);
                                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/" + address);
                                jSONObject = new JSONObject(jsonString);
                                timestamp = System.currentTimeMillis();
                                level = jSONObject.getInt("level");
                                LevelUpdate(table,address);                                    
                            }
                            if(table.endsWith("BALANCE"))
                            {
                                ID = table.substring(11, table.length() - 8);
                                address = (String) GetItemValue("my_watchlist", "address", "ID", ID,dbConnection);
                                balance = Double.parseDouble(Utilities.ReadStringFromURL("http://localhost:12391/addresses/balance/" + address));
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
                        BackgroundService.AppendLog("COLLECTING GARBAGE @ " + Utilities.DateFormat(System.currentTimeMillis()));
                        lastGCTime = System.currentTimeMillis();
                        System.gc();
                    }
                    
                    currentTick++;
                    currentTick = (alertsDelta * currentTick) >= updateDelta ? 1 : currentTick;
                        
                }
                catch(JSONException | ConnectException e)
                {
                    BackgroundService.AppendLog(e);
                    //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                    if ((boolean) GetFirstItem("alerts_settings", "reqording",propertiesConnection))
                    {
                        PoolAlert("WARNING: ReQording session was halted", 
                            "ReQorder has stopped reqording, the following error was encountered:\n\n"  + e.toString());                        
                    }
                    if(BackgroundService.GUI == null)
                    {
                        System.out.println(
                                "Stopped reqording @ " + Utilities.DateFormat(System.currentTimeMillis())
                                        + "\\n" + e.toString() + "\\nIs your Qortal core running?");
                        StopReqording();                        
                    }
                    else
                    {
                        BackgroundService.GUI.StopReqording();
                        JOptionPane.showMessageDialog(BackgroundService.GUI, 
                                "Stopped reqording @ " + Utilities.DateFormat(System.currentTimeMillis()) + "\n" 
                                        + e.toString()+ "\nIs your Qortal core running?",
                                "Error while reqording", JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (IOException | NullPointerException | NumberFormatException | SQLException | TimeoutException e) 
                {
                    BackgroundService.AppendLog(e);
                    //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                    if ((boolean) GetFirstItem("alerts_settings", "reqording",propertiesConnection))
                    {
                        PoolAlert("WARNING: ReQording session was halted", 
                            "ReQorder has stopped reqording, the following error was encountered:\n\n"  + e.toString());                        
                    }
                    if(BackgroundService.GUI == null)
                    {
                        System.out.println(
                                "Stopped reqording @ " + Utilities.DateFormat(System.currentTimeMillis()) + "\n" + e.toString());
                        StopReqording();
                        
                    }
                    else
                    {
                        BackgroundService.GUI.StopReqording();
                        JOptionPane.showMessageDialog(BackgroundService.GUI, 
                                "Stopped reqording @ " + Utilities.DateFormat(System.currentTimeMillis()) + "\n" + e.toString(),
                                "Error while reqording", JOptionPane.ERROR_MESSAGE);
                    }   
                }
            }          
            
        }, 0, alertsDelta); 
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
        systemInfo.getOperatingSystem().getProcess(myProcessID).updateAttributes();
        insertStringList.add(String.valueOf(systemInfo.getOperatingSystem().getProcess(myProcessID).getResidentSetSize()));
        
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
                insertStringList.add(String.valueOf(blockHeight));
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
                            PoolAlert("Qortal blockchain size alert", 
                                    String.format("ReQorder has detected that the size of your blockchain folder (%s) has exceeded the "
                                            + "treshold indicated in your alerts settings.\n\nThe current size of your blockchain folder "
                                            + "is %s Mb.\nThe alert setting was %s Mb.",blockChainFolder,
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
                    String subject2 = "Error sending e-mail alert";
                    String message2 = "ReQorder failed to send the following message. Please make sure that your mail "
                            + "server settings and log-in credentials correct. You can also disable e-mail alerts to stop recieving this message.\n\n"
                            + "Undelivered message : \n\nSubject : "+ subject + "\n\n" + message;
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
    
    private void SendAlertToGUI(String recipient,String subject,String message, Connection c)
    {
         if(!TableExists("alerts", c))
                CreateTable(new String[]{"alerts","timestamp","long","recipient","varchar(255)",
                    "subject","varchar(255)","message","varchar(MAX)","read","boolean"}, c);
            
            InsertIntoDB(new String[]{"alerts","timestamp",String.valueOf(System.currentTimeMillis()),
                "recipient",Utilities.ToH2Char(recipient),"subject",Utilities.ToH2Char(subject),"message",Utilities.ToH2Char(message),"read","false"}, c); 
            
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
                    alertedBlockAddresses.remove(address);
                    sql = "insert into " + table + " (timestamp,blocksminted) values (" + 
                            timestamp + "," + blocksMinted + ")";
                    ExecuteUpdate(sql,dbConnection);  
                }
            }
            else
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
                        String name = (String) GetItemValue("my_watchlist", "name", "address", Utilities.ToH2Char(address), dbConnection);
                        name = name.isEmpty() ? address : name;
                        PoolAlert("Minting has halted",
                                String.format("ReQorder has detected that Qortal account '%s' has not been minting for "
                                        + "%s.\n\nYou might want to check if your Qortal core is running, or alert the owner of that account.",
                                        name, Utilities.MillisToDayHrMin(WARNING_TIME)));
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
            if((boolean)GetItemValue("my_watchlist", "alert", "address", Utilities.ToH2Char(address), dbConnection))
            {
                String name = (String)GetItemValue("my_watchlist", "name", "address", Utilities.ToH2Char(address), dbConnection);
                name = name.isEmpty() ? address : name;
                
                double alertValue = (double)GetItemValue("my_watchlist", "alertvalue", "address", Utilities.ToH2Char(address), dbConnection);
                //alertvalue is stored as a negative value if alerting for below indicated value
                boolean alertForExceed = alertValue >= 0;
                alertValue = Math.abs(alertValue);
                
                if(alertForExceed && balance >= alertValue)
                {
                    PoolAlert(String.format("Balance has reached or exceeded %.5f QORT",alertValue), 
                            String.format("ReQorder has detected that the balance for account ''%s'' has reached or exceeded the indicated alert value of %.5f.\n"
                                    + "The current balance for this account is %.5f QORT.\n\nThis balance alert has automatically been disabled.", 
                                    name,alertValue,balance));
                    //disable alert
                    ChangeValue("my_watchlist", "alert", "false", "address", Utilities.ToH2Char(address), dbConnection);
                }
                if(!alertForExceed && balance <= alertValue)
                {
                    PoolAlert(String.format("Balance has reached or gone below %.5f QORT",alertValue), 
                            String.format("ReQorder has detected that the balance for account ''%s'' has reached or gone below the indicated alert value of %.5f.\n"
                                    + "The current balance for this account is %.5f QORT.\n\nThis balance alert has automatically been disabled.", 
                                    name,alertValue,balance));
                    //disable alert
                    ChangeValue("my_watchlist", "alert", "false", "address", Utilities.ToH2Char(address), dbConnection);
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
                String name = (String) GetItemValue("my_watchlist", "name", "address", Utilities.ToH2Char(address), dbConnection);
                name = name.isEmpty() ? address : name;
                   //check if alert enabled, no need for alertsent flag (update should only happen once, if at all)
                if ((boolean) GetFirstItem("alerts_settings", "levelling",propertiesConnection))
                {
                    PoolAlert("Qortal account level upgrade detected", String.format(
                            "Congratulations!\n\nReQorder has detected a levelling upgrade from level %d to level %d for Qortal account '%s'. "
                            + "Thank you for your contribution to the Qortal network.", oldLevel,level,name));
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
            String oldversion = Utilities.ToH2Char(rs.getString("buildversion"));
            if (!buildVersion.equals(oldversion))
            {
                sql = "insert into buildversion (timestamp,buildversion) values ("
                        + timestamp + "," + buildVersion + ")";
                ExecuteUpdate(sql, dbConnection);
                //check if alert enabled (no need for alert sent flag as buildversion is rarely updated)
                if((boolean) GetFirstItem("alerts_settings", "core_update",propertiesConnection))
                {
                    //use 2x single quote to escape varchar required single quote
                    PoolAlert("Qortal core update detected", String.format(
                            "ReQorder has detected an update of your Qortal core. "
                            + "Your buildversion has been updated from '%s' to '%s'.", oldversion, buildVersion));
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
                if(Math.abs(lastPrice - LTCprice) > LTC_UPDATE_DELTA)
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
                //if alertvalue is smaller than the lastknown price, we send an alert when current price is larger than alertvalue
                //otherwise we send an alert when price is smaller than alertvalue
                boolean alertForIncrease = lastPrice < alertValue;
                
                boolean sendAlert = false;
                
                if(alertForIncrease && LTCprice - alertValue > 0)//ltcprice has increased above alertvalue
                    sendAlert = true;
                if(!alertForIncrease && LTCprice - alertValue < 0)//ltcprice has decreased below alertvalue
                    sendAlert = true;
                
                if(sendAlert)
                {
                    PoolAlert("Litecoin price alert", String.format(
                            "ReQorder has detected that the Litecoin price %s the value set in your alerts settings.\n\n"
                                    + "Previous price: %.5f QORT\nCurrent price: %.5f QORT\nAlert price %.5f QORT\n\n"
                                    + "This alert has automatically been disabled",alertForIncrease?"has exceeded":"is now lower than",
                                    ((double)lastPrice/100000000),((double)LTCprice/100000000),((double)alertValue/100000000)));
                    //disable alert
                    ChangeValue("alerts_settings", "ltcprice", "false", "id", "0", propertiesConnection);
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
                if(Math.abs(lastPrice - DogePrice) > DOGE_UPDATE_DELTA)
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
                //if alertvalue is smaller than the lastknown price, we send an alert when price is larger than alertvalue
                //otherwise we send an alert when price is smaller than alertvalue
                boolean alertForIncrease = lastPrice < alertValue;
                
                boolean sendAlert = false;
                
                if(alertForIncrease && DogePrice - alertValue > 0)//DogePrice has increased above alertvalue
                    sendAlert = true;
                if(!alertForIncrease && DogePrice - alertValue < 0)//DogePrice has decreased below alertvalue
                    sendAlert = true;                
                
                if(sendAlert)
                {
                    PoolAlert("Dogecoin price alert", String.format(
                            "ReQorder has detected that the Dogecoin price %s the value set in your alerts settings.\n\n"
                                    + "Previous price: %.5f QORT\nCurrent price: %.5f QORT\nAlert price %.5f QORT\n\n"
                                    + "This alert has automatically been disabled",alertForIncrease?"has exceeded":"is now lower than",
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
