package reqorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.swing.table.DefaultTableModel;
import org.json.JSONArray;
import org.json.JSONException;

public class Utilities
{
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int TIMEOUT_DURATION = 45;
    private static final String WEEKS =Main.BUNDLE.getString("weeks");
    private static final String DAYS =Main.BUNDLE.getString("days");
    private static final String HOURS =Main.BUNDLE.getString("hours");
    private static final String MINUTES =Main.BUNDLE.getString("minutes");
    private static final String SECONDS =Main.BUNDLE.getString("seconds");
    
    public static String CreateRandomString(int length)
    {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();    
    }
    
    private static String ReadString(String requestURL) throws  ConnectException, IOException
    {   
        try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),StandardCharsets.UTF_8.toString()))
        {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    } 
    
    public static String ReadStringFromURL(String requestURL) throws TimeoutException, IOException, ConnectException
    {
        Future<String> result = executor.submit(() -> ReadString(requestURL));

        try
        {
            return result.get(TIMEOUT_DURATION,TimeUnit.SECONDS);
        }
        catch(ExecutionException e)
        {
            if(e.getCause().toString().startsWith("java.net.ConnectException"))
                throw new ConnectException();
            if(e.getCause().toString().startsWith("java.io.IOException"))
                throw new IOException();
            
            BackgroundService.AppendLog(e.toString() + " for " + requestURL);
            BackgroundService.AppendLog(e);
        }
        catch( InterruptedException  e)
        { 
            BackgroundService.AppendLog(e.toString() + " for " + requestURL);
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }
    
    public static String MillisToDayHrMin(long milliseconds)
    {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);  
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));  
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
//        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
//        long ms = TimeUnit.MILLISECONDS.toMillis(milliseconds) - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(milliseconds));
        
        if(days > 6)
        {
            long weeks = days / 7;
            return String.format("%d %s %d %s %d %s %d %s",weeks,WEEKS, days % 7,DAYS, hours,HOURS, minutes,MINUTES);
        }
        if(days >= 1)
            return String.format("%d %s %d %s %d %s", days % 7, DAYS, hours, HOURS, minutes, MINUTES);            
        if(days < 1 && hours > 0)
            return String.format("%d %s %d %s",hours, HOURS, minutes, MINUTES);
        if(hours < 1)
            return String.format("%d %s", minutes, MINUTES);
        
        return "invalid duration format"; //String.format("%d days %d hours %d minutes", days, hours, minutes);
     }
    
    public static String MillisToDayHrMinShort(long milliseconds)
    {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);  
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));  
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
//        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
//        long ms = TimeUnit.MILLISECONDS.toMillis(milliseconds) - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(milliseconds));
        
        if(days > 6)
        {
            long weeks = days / 7;
            return String.format("%d w %d d %d hrs %d min",weeks, days % 7, hours, minutes);
        }
        if(days >= 1)
            return String.format("%d d %d hrs %d min", days % 7, hours, minutes);            
        if(days < 1 && hours > 0)
            return String.format("%d hrs %d min",hours,minutes);
        if(hours < 1)
            return String.format("%d min", minutes);
        
        return "invalid duration format"; //String.format("%d days %d hours %d minutes", days, hours, minutes);
     }
    
    public static String MillisToDayHrMinShortFormat(long milliseconds)
    {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);  
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));  
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
        
        if(days > 6)
        {
            long weeks = days / 7;
            return String.format("%d wk %d d %d hr %d min",weeks, days % 7, hours, minutes);
        }
        if(days >= 1)
            return String.format("%d days %d hrs %d min", days % 7, hours, minutes);            
        if(days < 1 && hours > 0)
            return String.format("%d %s %d %s",hours,HOURS,minutes, MINUTES);
        if(hours < 1)
            return String.format("%d %s", minutes, MINUTES);
        
        return "invalid duration format"; //String.format("%d days %d hours %d minutes", days, hours, minutes);
     }
    
    public static String MillisToDayHrMinSec(long milliseconds)
    {
       long days = TimeUnit.MILLISECONDS.toDays(milliseconds);  
       long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));  
       long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds));
//        long ms = TimeUnit.MILLISECONDS.toMillis(milliseconds) - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(milliseconds));

       if(days > 6)
       {
           long weeks = days / 7;
           return String.format("%d %s %d %s %d %s %d %s",weeks, WEEKS, days % 7, DAYS, hours, HOURS, minutes, MINUTES);
       }
       if(days >= 1)
           return String.format("%d %s %d %s %d %s", days % 7, DAYS, hours, HOURS, minutes, MINUTES);            
       if(days < 1 && hours > 0)
           return String.format("%d %s %d %s",hours, HOURS, minutes, MINUTES);
       if(hours < 1)
           return String.format("%d %s %d %s", minutes, MINUTES, seconds, SECONDS);
       if(minutes < 1)
           return String.format("%d %s", seconds, SECONDS);
           

       return "invalid duration format"; //String.format("%d days %d hours %d minutes", days, hours, minutes);
    }
    
    public static String TimeFormat(long timeMillisec)
    {        
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss z");
        return dateFormat.format(timeMillisec);
    }
    
    public static String DateFormat(long timeMillisec)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy 'at' HH:mm:ss z");
        return dateFormat.format(timeMillisec);
    }
    
    public static String DateFormatPath(long timeMillisec)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy 'at' HH.mm.ss");
        return dateFormat.format(timeMillisec);
    }
    
    public static String DateFormatShort(double timeMillisec)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd-yyyy 'at' HH:mm");
        return dateFormat.format(timeMillisec);
    }
    
    public static String AllignCenterHTML(String s)
    {
        return "<html><div style='text-align: center;'>" + s + "</div><html>";
    }
    
    /**When there aren't any peers that show their blockheight, this method will return 0
     * @return highest known blockheight of all connected peers*/ 
    public static int FindChainHeight()
    {    
        try
        {
            JSONArray jSONArray = new JSONArray(ReadStringFromURL("http://" + BackgroundService.GUI.dbManager.socket + "/peers"));
            int highest = 0;
            int current = 0;

            for (int i = 0; i < jSONArray.length(); i++)
            {
                    if (jSONArray.getJSONObject(i).has("lastHeight"))
                    {
                        current = jSONArray.getJSONObject(i).getInt("lastHeight");
                    }
                    if (current > highest)
                    {
                        highest = current;
                    }
                }

            return highest;
        }
        catch (IOException | TimeoutException | JSONException e)
        {
                    //some peers will not have the "lastHeight" value in their JSON, so the log will show that an error was thrown
                    BackgroundService.AppendLog(e.toString() + " @ FindChainHeight() (ignore lastHeight warning)");
                    BackgroundService.AppendLog(e);
        }    
        
        return 0;       
    }
    
    public static void CopyFile( File from, File to )
    {
        try 
        {
            Files.copy( from.toPath(), to.toPath() );            
        } 
        catch (IOException e) 
        {
            BackgroundService.AppendLog(e);
        }
    } 
    
    public static DefaultTableModel BuildTableModel(String table,ResultSet rs)
    {
        try
        {            
            ResultSetMetaData metaData = rs.getMetaData();

            int timeStampIndex = 0;
            int uptimeIndex = 0;
            // names of columns
            @SuppressWarnings("UseOfObsoleteCollectionType")
            Vector<String> columnNames = new Vector<>();
            int columnCount = metaData.getColumnCount();
            for (int column = 1; column <= columnCount; column++)
            {                
                columnNames.add(metaData.getColumnName(column));
                
                if(table.equals("NODE_PREFS"))//timestamp and uptime are booleans in this table
                    continue;
                
                if(metaData.getColumnName(column).equals("TIMESTAMP"))
                    timeStampIndex = column;
                if(metaData.getColumnName(column).equals("UPTIME"))
                    uptimeIndex = column;
            }

            // data of the table
            @SuppressWarnings("UseOfObsoleteCollectionType")
            Vector<Vector<Object>> data = new Vector<>();
            while (rs.next())
            {
                @SuppressWarnings("UseOfObsoleteCollectionType")
                Vector<Object> vector = new Vector<>();
                for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++)
                {
                    if(columnIndex == timeStampIndex)
                        vector.add(DateFormatShort((long) rs.getObject(columnIndex)));
                    else if (columnIndex == uptimeIndex)
                        vector.add(MillisToDayHrMinShort((long) rs.getObject(columnIndex)));
                    else
                        vector.add(rs.getObject(columnIndex));
                }
                data.add(vector);
            }

            return new DefaultTableModel(data, columnNames);
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        
        return  null;
    }
    
    /**
     * Adds a single quote to the beginning and end of the string<br>
     * Mostly used to insert a varchar into the H2 database, which needs <br>
     * to be encased in single quotes
     * @param s
     * @return supplied string in single quotes
     */
    public static String SingleQuotedString(String s)
    {
        return "'" + s + "'";
    }
    
    public static String GenerateLabelString(String label, double value)
    {
        String returnString = "";
        
        switch(label)
        {
            case "blockheight":
            case "myblockheight":
            case "numberOfConnections":
            case "allKnownPeers":
            case "allOnlineMinters":
            case "blocks":
            case "level":
            case "levelling":
                returnString = String.format("%s", NumberFormat.getIntegerInstance().format((int) value));
                break;
            case "moving average":
                returnString = String.valueOf(value);
                break;
            case "bytes_sent":
            case "bytes_received":
            case "bytes_sent_avg_min":
            case "bytes_sent_avg_hour":
            case "bytes_sent_avg_day":
            case "bytes_rec_avg_min":
            case "bytes_rec_avg_hour":
            case "bytes_rec_avg_day":
            case "ram_usage":
            case "blockchainsize":
            case "qortal_ram":
                returnString = String.format("%.2fMb", value);
                break;   
            case "ltc_to_qort_price":
            case "doge_to_qort_price":
            case "balance":
                returnString = String.format("%.5f QORT", value);
                break;
            case "qort_to_ltc_price":
                returnString = String.format("%.5f LTC", value);
                break;
            case "qort_to_doge_price":
                returnString = String.format("%.5f Doge", value);
                break;
            case "uptime":
                returnString = String.format("%s", Utilities.MillisToDayHrMinShortFormat((long)value));
                break;
            case "buildversion":
                returnString = String.format("%.6f", value);
                break;
            case "mintingrate":
                returnString = String.format("%d Blocks per hour", (int) value);
                break;
            case "balancedelta":
                returnString = String.format("%.5f QORT per hour", value);
                break;
            case "efficiency":
            case "cpu_usage":
                returnString = String.format("%.2f%%", value);
                break;
            case "cpu_temp":
                returnString = String.format("%.1f °C", value);
                break;
        }
        
        return returnString;
    }
    
    public static String[] EncryptPassword(char[] password, String SECRET_KEY, String SALT)
    {
        try
        {            
            SECRET_KEY = SECRET_KEY.isBlank() ? Utilities.CreateRandomString(10) : SECRET_KEY;            
            SALT = SALT.isBlank() ? Utilities.CreateRandomString(10) : SALT;
            SecureRandom random = new SecureRandom();
            
            File dir = new File(System.getProperty("user.dir") + "/bin");
            if (!dir.exists())
                dir.mkdir();            
            File outputFile = new File(dir + "/init");
            byte[] iv = new byte[16];
            //file init should only be created once, the first time a password or hash is encrypted
            //after that we just read the bytes from the file
            if(outputFile.exists())
            {
                iv = Files.readAllBytes(outputFile.toPath());
            }
            else               
            {
                random.nextBytes(iv);      
                Files.write(outputFile.toPath(), iv);
            }            
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            String encryptedPassword = Base64.getEncoder().encodeToString(
                    cipher.doFinal(charsToBytes(password)));
            
            return new String[]{encryptedPassword,SECRET_KEY,SALT};                
        }
        catch (IOException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException |
                InvalidKeySpecException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e)
        {
            BackgroundService.AppendLog("Error encrypting: " + e.toString());
            BackgroundService.AppendLog(e);
        }        
        return null;
    }
    
    public static char[] DecryptPassword(String encryptedPassword, String key, String salt)
    {
          try
        {                
            File newFile = new File(System.getProperty("user.dir") + "/bin/init");
            if(newFile.exists())
            {
                byte[] iv = Files.readAllBytes(newFile.toPath());

                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(key.toCharArray(), salt.getBytes(), 65536, 256);
                SecretKey tmp = factory.generateSecret(spec);
                SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
                IvParameterSpec ivspec = new IvParameterSpec(iv);
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
                
                return bytesToChars(cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)));                 
            }
            else
            {
                BackgroundService.AppendLog(newFile.getPath() + " not found");
            }            
        }
        catch (IOException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException |
                InvalidKeySpecException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e)
        {
            BackgroundService.AppendLog("Error decrypting: " + e.toString());
            BackgroundService.AppendLog(e);
        }   
        return  null;
    } 
        
    public static String GeneratePasswordHash(char [] password, int iterations)
    {
        try
        {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            //set hash length to 64, itertations to 655236
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 64 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            
            return iterations + ":" + toHex(salt) + ":" + toHex(hash);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            BackgroundService.AppendLog(e);            
        }
        
        return  null;
    }       
    
    private static String toHex(byte[] array) 
    {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if (paddingLength > 0)
        {
            return String.format("%0" + paddingLength + "d", 0) + hex;
        }
        else
        {
            return hex;
        }
    }
   
    protected static boolean PasswordValid(char [] originalPassword, String storedHash) 
    {
        try
        {
            String[] parts = storedHash.split(":");
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = fromHex(parts[1]);
            byte[] hash = fromHex(parts[2]);

            PBEKeySpec spec = new PBEKeySpec(originalPassword, salt, iterations, hash.length * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] testHash = skf.generateSecret(spec).getEncoded();

            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++)
            {
                diff |= hash[i] ^ testHash[i];
            }
            return diff == 0;

        }
        catch (NumberFormatException | NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            BackgroundService.AppendLog(e);
        }
        
        return false;
    }

    private static byte[] fromHex(String hex)
    {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++)
        {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
    
    public static byte[] charsToBytes(char[] chars)
    {
        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        return Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
    }

    public static char[] bytesToChars(byte[] bytes)
    {
        final CharBuffer charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        return Arrays.copyOf(charBuffer.array(), charBuffer.limit());
    }
    
    protected static long getDirectorySize(File folder)
    {
        long length = 0;
        File[] files = folder.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                if (file.isFile())
                {
                    length += file.length();
                }
                else
                {
                    length += getDirectorySize(file);
                }
            }
        }
        return length;
    }
    
    public static boolean SendEmail(String recipient, String username,String password,String smtp, String port,String subject, String message)
    {      
        
        message += Main.BUNDLE.getString("mailFooter");

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtp);
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator()
        {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                return new PasswordAuthentication(username, password);
            }
        });
//        session.setDebug(true);

        try
        {
            Message mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(username));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));  
            mimeMessage.setSubject(subject);
            mimeMessage.setText(message);

            Transport.send(mimeMessage);

            return true;
        }
        catch (MessagingException e)
        {
            BackgroundService.AppendLog(e);
        }        
        return false;
    }
    
    public static void ZipFiles(ArrayList<File> srcFiles, File zipFile) throws IOException
    {
        try (FileOutputStream fos = new FileOutputStream(zipFile); 
                ZipOutputStream zipOut = new ZipOutputStream(fos))
        {
            for (File fileToZip : srcFiles) 
            {
                try (FileInputStream fis = new FileInputStream(fileToZip))
                {
                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipOut.putNextEntry(zipEntry);
                    
                    byte[] bytes = new byte[1024];
                    int length;
                    while((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                }
            }
        }
    }    
}

