package reqorder;

import java.awt.CardLayout;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JOptionPane;

public class NewUserPanel extends javax.swing.JPanel
{
    private GUI gui;
    public NewUserPanel()
    {
        initComponents();
    }
    
    public void Intialise(GUI gui)
    {
        this.gui = gui;
    }  
    
    private void CreateDBA_file(char [] dbPassword)
    {
         //The dbPassword is created randomly and is not known to anyone. It is stored in a seperate database
        //that is accessible with the reqorder password created above.
        DatabaseManager.dbPassword = dbPassword;
        DatabaseManager.reqorderPassword = passwordField1.getPassword();
        File oldFile = new File(System.getProperty("user.dir") + "/bin/dba.mv.db");
        if (oldFile.exists())
            oldFile.delete();
        
        //Database will get created on connection attempt
        try (Connection c = ConnectionDB.get_DBA_Connection(passwordField1.getPassword()))
        {       
            gui.dbManager.CreateTable(new String[]{"dba", "value", "varchar(50)"}, c);
            gui.dbManager.InsertIntoDB(new String[]{"dba", "value", Utilities.SingleQuotedString(String.copyValueOf(dbPassword))}, c);
            c.createStatement().execute("SHUTDOWN");
            c.close();
        }
        catch (SQLException e)
        {
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            BackgroundService.AppendLog(e);
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], "dba")));
            throw new NullPointerException();
        }
    }
    
    protected void CheckForCapsLock()
    {
        capsLockLabel.setVisible(Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK));
    }
    
    protected void CheckForRestoreFile()
    {
        File restoreFile = new File(System.getProperty("user.dir") + "/restore/restore.zip");
        importAccountButton.setEnabled(restoreFile.exists());
    }
    
    //changes the password for dba database
    private void SetReqorderPassword(char [] newPassword)
    {
        try
        {         
            char[] currentPassword = DatabaseManager.reqorderPassword;
            
            try (Connection c = ConnectionDB.get_DBA_Connection(currentPassword))
            {
                //don't want to log this ,dont use ExecuteUpdate
                c.createStatement().execute(String.format(
                        "alter user %s set password '%s' ", "reqorder", String.copyValueOf(newPassword)));
                c.close();
            }
            DatabaseManager.reqorderPassword = newPassword;
            //When user changes his password, we need to create a new dba file, we need to set its filePw and userPw to
            //reqorderPw, but it has to always contain the dbPw that was generated on account creation (which is also the filePw)
            CreateDBA_file(DatabaseManager.dbPassword);
            
            if(!String.copyValueOf(newPassword).equals(String.copyValueOf(currentPassword)))
                JOptionPane.showMessageDialog(this, Main.BUNDLE.getString("passwordChanged"), 
                        Main.BUNDLE.getString("success"), JOptionPane.PLAIN_MESSAGE);
            else                
                JOptionPane.showMessageDialog(this, Main.BUNDLE.getString("noChangeDetected"), 
                        Main.BUNDLE.getString("success"), JOptionPane.PLAIN_MESSAGE);
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    protected void CreateHashFile(char [] password, File hashFile)
    {
        try
        {
            //hash for the password that will unlock reqorder
            String hash = Utilities.GeneratePasswordHash(passwordField1.getPassword(), 655236);
            //encrypt the hash using reqorder pw as key and reversed reqorder pw as salt
            StringBuilder sb=new StringBuilder(String.copyValueOf(password));  
            String reverse = sb.reverse().toString();  
            String encryptedHash = Utilities.EncryptPassword(hash.toCharArray(), String.copyValueOf(password), reverse)[0];               
            
            byte[] hashBytes = encryptedHash.getBytes(StandardCharsets.UTF_8);
            hashFile = new File(System.getProperty("user.dir") + "/bin/auth");
            if(hashFile.exists())
                hashFile.delete();
            Files.write(hashFile.toPath(), hashBytes);             
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }           
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        scrollPane = new javax.swing.JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(10);
        container = new javax.swing.JPanel();
        mainTextLabel = new javax.swing.JLabel();
        enterPasswordLabel = new javax.swing.JLabel();
        passwordField1 = new javax.swing.JPasswordField();
        passwordStatusLabel = new javax.swing.JLabel();
        passwordField2 = new javax.swing.JPasswordField();
        savePasswordButton = new javax.swing.JButton();
        noPasswordButton = new javax.swing.JButton();
        reEnterPasswordLabel = new javax.swing.JLabel();
        changePasswordText = new javax.swing.JLabel();
        changePasswordText.setVisible(false);
        capsLockLabel = new javax.swing.JLabel();
        capsLockLabel.setVisible(false);
        importAccountButton = new javax.swing.JButton();
        backButton = new javax.swing.JButton();
        backButton.setVisible(false);

        container.setLayout(new java.awt.GridBagLayout());

        mainTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        mainTextLabel.setText(bundle.getString("mainTextLabel")); // NOI18N
        mainTextLabel.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 15, 0);
        container.add(mainTextLabel, gridBagConstraints);

        enterPasswordLabel.setText(bundle.getString("enterPasswordLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(enterPasswordLabel, gridBagConstraints);

        passwordField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        passwordField1.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                passwordField1KeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(passwordField1, gridBagConstraints);

        passwordStatusLabel.setText(bundle.getString("passwordStatusLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(passwordStatusLabel, gridBagConstraints);

        passwordField2.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        passwordField2.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                passwordField1KeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(passwordField2, gridBagConstraints);

        savePasswordButton.setText(bundle.getString("savePasswordButton")); // NOI18N
        savePasswordButton.setEnabled(false);
        savePasswordButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                savePasswordButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        container.add(savePasswordButton, gridBagConstraints);

        noPasswordButton.setText(bundle.getString("noPasswordButton")); // NOI18N
        noPasswordButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                noPasswordButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        container.add(noPasswordButton, gridBagConstraints);

        reEnterPasswordLabel.setText(bundle.getString("reEnterPasswordLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(reEnterPasswordLabel, gridBagConstraints);

        changePasswordText.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        changePasswordText.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        changePasswordText.setText("<html><div style='margin: 5px 20px 0px 20px;'>\nPlease enter your new password.</div><html/>");
        changePasswordText.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipady = 25;
        container.add(changePasswordText, gridBagConstraints);

        capsLockLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        capsLockLabel.setForeground(new java.awt.Color(161, 0, 40));
        capsLockLabel.setText(bundle.getString("capsLockLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        container.add(capsLockLabel, gridBagConstraints);

        importAccountButton.setText(bundle.getString("importAccountButton")); // NOI18N
        importAccountButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                importAccountButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        container.add(importAccountButton, gridBagConstraints);

        backButton.setText(bundle.getString("backButton")); // NOI18N
        backButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                backButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        container.add(backButton, gridBagConstraints);

        scrollPane.setViewportView(container);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 696, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 716, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void passwordField1KeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_passwordField1KeyReleased
    {//GEN-HEADEREND:event_passwordField1KeyReleased
        CheckForCapsLock();
        
        if(passwordField1.getPassword().length == 0 && passwordField2.getPassword().length == 0)
        {
            savePasswordButton.setEnabled(false);
            passwordStatusLabel.setText(Main.BUNDLE.getString("passwordStatusLabel"));
            return;
        }

        if(String.copyValueOf(passwordField1.getPassword()).equals(String.copyValueOf(passwordField2.getPassword())))
        {
            savePasswordButton.setEnabled(true);
            passwordStatusLabel.setText(Main.BUNDLE.getString("passwordsIdentical"));
            if(evt.getKeyCode() == KeyEvent.VK_ENTER)
                savePasswordButtonActionPerformed(null);
        }
        else
        {
            savePasswordButton.setEnabled(false);
            passwordStatusLabel.setText(Main.BUNDLE.getString("passwordsNotIdentical"));
        }
    }//GEN-LAST:event_passwordField1KeyReleased

    private void noPasswordButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_noPasswordButtonActionPerformed
    {//GEN-HEADEREND:event_noPasswordButtonActionPerformed
        try
        {       
            File authDir = new File(System.getProperty("user.dir") + "/bin");
            if (!authDir.isDirectory())
                authDir.mkdir();
            
            String hash = "";
            byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);
            //Even with empty password we want a hash file to tell if user has opted for this option
            File hashFile = new File(System.getProperty("user.dir") + "/bin/auth");
            if(hashFile.exists())
            {
                //delete old hashfile which could contain a previous password hash (new blank file written below)
                hashFile.delete();
                hashFile = new File(System.getProperty("user.dir") + "/bin/auth");
            }
            Files.write(hashFile.toPath(), hashBytes);
            
            File dba = new File(System.getProperty("user.dir") + "/bin/dba.mv.db");
            //If this is the first time account there will be no dba file
            //otherwise the user decided to set password to blank
            if(dba.exists())
                SetReqorderPassword(("").toCharArray());
            else
            {                
                String dbPassword = Utilities.CreateRandomString(30);//the password that will unlock the databases
                CreateDBA_file(dbPassword.toCharArray());
            }
            
            gui.LoginComplete();
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_noPasswordButtonActionPerformed

    private void savePasswordButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_savePasswordButtonActionPerformed
    {//GEN-HEADEREND:event_savePasswordButtonActionPerformed
        //user can't set empty string, save button will not be enabled, no need to check for that case
        if(String.copyValueOf(passwordField1.getPassword()).contains(" "))
        {
            JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML(
                    Main.BUNDLE.getString("tryAgainPw")), Main.BUNDLE.getString("tryAgainPwTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        //<editor-fold defaultstate="collapsed" desc="The user creates a reqorder password ">
        /**
         * The user creates a reqorder password and reqorder creates a randomly generated strong dbPassword for db access.
         * We generate and encrypt a hash of the reqorder password with the reqorder password as key and reversed pw as salt in 
         * a file inside user.dir. The randomly created dbPassword gets stored in an encrypted database using the users password.
         * On login, we check the users password for validity using the hash in order to retrieve the databases password.
         * The db password will not change if user changes his password, only the hash and the dba password will. The only way  
         * db password can be changed is by creating a new account (for instance after encountering a deleted or corrupted 
         * dba,auth or init file)
         * 
         * Advantages of this approach:
         * -Assuring the use of a strong password for the databases containing sensitive data or data that the user prefers to stay
         *  private while still allowing the user to choose their own password
         * -Properties db containing sensitive data will still be encrypted, even if user opted for no password (dba db won't be in this case)
         * -User can still choose to encrypt databases without having to log in (not ideal, but better than the alternative)
         * -Assuring the only way to access encrypted databases is through this app.
         * -It mitigates malicious actors swapping the users hash which is accessible to anyone for  their own, in which
         *  case the attacker can access the app but not the databases, they were locked with the real password.
         * -Avoids users carelessly or unknowingly sharing the password for databases containing sensitive data, since
         *  they don't know the password to the database
         * -Remote access to databases will require the user to place the key on the terminal machine, no key no access
         * 
         * Disadvantages:
         * -More complex than just encrypting the databases with the user password which would mean dealing with 
         *  only one password and no need for authorisation files auth and dba 
         * -If user loses access to a database by deletion or corruption of dba file it is irreversible, even 
         *  though they still remember the reqorder password, which could be frustrating and inconceivable to them.
         * -Hinders users from sharing their databases, they'll need to unlock them first.
         * 
         * For now this approach will be used, it shouldn't be too hard switching to single password implementation if needed
         */
        //</editor-fold>
           
        File authDir = new File(System.getProperty("user.dir") + "/bin/");
        if(!authDir.isDirectory())
            authDir.mkdir();
        //hashfile should always exist once user has created an account (same goes for dba)
        File hashFile = new File(System.getProperty("user.dir") + "/bin/auth");

        //if changing existing password
        if (hashFile.exists())
        {
            hashFile.delete();
            CreateHashFile(passwordField1.getPassword(), hashFile);
            SetReqorderPassword(passwordField1.getPassword());
            passwordField1.setText("");
            passwordField2.setText("");
        }
        //if creating new account
        else
        {
            CreateHashFile(passwordField1.getPassword(), hashFile);
            String dbPassword = Utilities.CreateRandomString(30);//the password that will unlock the databases
            CreateDBA_file(dbPassword.toCharArray());
            passwordField1.setText("");
            passwordField2.setText("");
        }
        gui.LoginComplete();  
    }//GEN-LAST:event_savePasswordButtonActionPerformed

    private void importAccountButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_importAccountButtonActionPerformed
    {//GEN-HEADEREND:event_importAccountButtonActionPerformed
        if(gui.dbManager.RestoreAccount())
        {
            gui.dbManager.FindDbFiles();
            gui.Login();            
        }
    }//GEN-LAST:event_importAccountButtonActionPerformed

    private void backButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_backButtonActionPerformed
    {//GEN-HEADEREND:event_backButtonActionPerformed
        CardLayout card = (CardLayout) gui.mainPanel.getLayout();
        card.show(gui.mainPanel, "reqorderPanel");  
    }//GEN-LAST:event_backButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JButton backButton;
    protected javax.swing.JLabel capsLockLabel;
    protected javax.swing.JLabel changePasswordText;
    private javax.swing.JPanel container;
    private javax.swing.JLabel enterPasswordLabel;
    private javax.swing.JButton importAccountButton;
    protected javax.swing.JLabel mainTextLabel;
    private javax.swing.JButton noPasswordButton;
    protected javax.swing.JPasswordField passwordField1;
    protected javax.swing.JPasswordField passwordField2;
    protected javax.swing.JLabel passwordStatusLabel;
    private javax.swing.JLabel reEnterPasswordLabel;
    protected javax.swing.JButton savePasswordButton;
    private javax.swing.JScrollPane scrollPane;
    // End of variables declaration//GEN-END:variables
}
