package reqorder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.swing.JOptionPane;

public class ConnectionDB 
{    
    private static final String DB_FOLDER_H2 = "jdbc:h2:./databases/";
    
    /** * This version of H2 automatically creates a database if it doesn't exist. <br>
     * IF_EXISTS argument provided by H2 was causing connection errors.<br>
     * Create all databases with this method, only connect to databases that are known to exist.
     * @param database
     * @param password
     * @param isEncrypted*/
    public static void CreateDatabase(String database,char[] password,boolean isEncrypted)
    {
        try 
        {
            Class.forName("org.h2.Driver");
            
            if(isEncrypted)
            {                
                char[] passwords = (String.copyValueOf(DatabaseManager.dbPassword) + " "
                        + String.copyValueOf(password)).toCharArray();
                String url = DB_FOLDER_H2 + database + ";CIPHER=AES"
                        + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";
                
                Properties prop = new Properties();
                prop.setProperty("user", "reqorder");
                prop.put("password", passwords);
                Connection c = DriverManager.getConnection(url, prop);
                c.close();                
            }
            else
            {
                Connection c = DriverManager.getConnection(DB_FOLDER_H2 + database + ";","reqorder","");   
                c.close();
            }
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
    }  
    
    /**Opening a connection will not throw exceptions, but closing a connection might throw an error<br>
     * that needs to be caught, therefore all calls to this function will have to be made from a try/catch block.
     * @param database
     * @return  */
    public static Connection getConnection(String database) throws NullPointerException
    {         
        //Using the catch clause to check for db's without password was causing unacceptable lag, using lists instead
        if(!DatabaseManager.encryptedFiles.contains(database))
            return getUnencryptedConnection(database);        
        
    //<editor-fold defaultstate="collapsed" desc="we can't use the parameter password">    
        //if we want to implement clearing the char[] (done automatically by getUnencryptedConnection(url,prop) )
        // we need to store (persist) it or encrypt it and decrypt when needed, so we can pass a new char[] every
        //time, if we clear the original char[], then the next time we call this method the password will be invalid
        //we can't use the password variable used by the program as it will be cleared by getUnencryptedConnection 
        //leaving us without a password in memory 
         //</editor-fold>
        char[] passwords = (String.copyValueOf(DatabaseManager.dbPassword) + " " 
                + String.copyValueOf(DatabaseManager.dbPassword)).toCharArray();         
        
         String url = DB_FOLDER_H2 +  database + ";CIPHER=AES" +
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";                 
        
        Properties prop = new Properties();
        prop.setProperty("user", "reqorder");
        prop.put("password", passwords);        
        try 
        {    
            Class.forName("org.h2.Driver");
            return DriverManager.getConnection(url,prop);
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            BackgroundService.GUI.dbManager.MoveInaccessibleFile(database);
            throw new NullPointerException();
        }
    } 
    
    /**this method is called when db is not encrypted
     * @param database
     * @return */
    public static Connection getUnencryptedConnection(String database) throws NullPointerException
    {
        try 
        {
            Class.forName("org.h2.Driver");
            Connection cn = DriverManager.getConnection(DB_FOLDER_H2 + database +
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0","reqorder","");
            return cn;     
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            BackgroundService.GUI.dbManager.MoveInaccessibleFile(database);
            throw new NullPointerException();
        }
    }   
    
    /**
     * Using a separate method for creating a dba file.<br>
     * We need this file to be encrypted using the reqorder password if we use the dbPassword <br>
     * (which is stored in dba file) it will not be accessible on a subsequent login<br>
     * attempt after the user changes his reqorder password
     *
     * @param reqorderPassword
     * @return connection
     */
    public static Connection get_DBA_Connection(char [] reqorderPassword)
    {        
        char[] passwords = (String.copyValueOf(reqorderPassword) + " "
                + String.copyValueOf(reqorderPassword)).toCharArray();
        String url = "jdbc:h2:./bin/dba;CIPHER=AES;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";
        Properties prop = new Properties();
        prop.setProperty("user", "reqorder");
        prop.put("password", passwords);        
        
        try
        {
            Connection c = DriverManager.getConnection(url, prop);
            return c;
        }
        catch(SQLException e)
        {
            BackgroundService.AppendLog(e);
            return null;
        }
    }
    
    /**used to add encrypted db's to a list to help refer non encrypted db's* to getUnencryptedConnection()     
     * @param database)
     * @return Boolean*/
    public static boolean IsEncrypted(String database)
    {
        try 
        {    
            String url = DB_FOLDER_H2 + database + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";    
            
            Class.forName("org.h2.Driver");
            Connection cn = DriverManager.getConnection(url,"reqorder","");
            cn.close();
            
            return false;     
        } 
        catch (ClassNotFoundException | SQLException e) 
        {            
            return true;
        }
    }
    
    public static boolean CanConnect(String database,char[] password)
    {            
        if (DatabaseManager.encryptedFiles.contains(database))
        {
            char[] passwords = (String.copyValueOf(DatabaseManager.dbPassword) + " "
                    + String.copyValueOf(password)).toCharArray();
            String url = DB_FOLDER_H2 + database +
                    ";CIPHER=AES;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";
            
            Properties prop = new Properties();
            prop.setProperty("user", "reqorder");
            prop.put("password", passwords);
            try
            {
                Class.forName("org.h2.Driver");
                Connection c = DriverManager.getConnection(url, prop);
                c.close();
                return true;
            }
            catch (ClassNotFoundException | SQLException e)
            {
                return false;
            }
        }
        else
        {
            String url = DB_FOLDER_H2 + database + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";

            char[] passwords = (String.copyValueOf(DatabaseManager.dbPassword) + " "
                    + String.copyValueOf(password)).toCharArray();
            
            Properties prop = new Properties();
            prop.setProperty("user", "reqorder");
            prop.put("password", passwords);
            try
            {
                Class.forName("org.h2.Driver");
                Connection c = DriverManager.getConnection(url, prop);
                c.close();
                return true;
            }
            catch (ClassNotFoundException | SQLException e)
            {
                return false;
            }
        }
    }      
}
