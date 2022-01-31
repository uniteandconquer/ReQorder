package reqorder;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class GUI extends javax.swing.JFrame
{
    protected final BackgroundService backgroundService;
    protected final DatabaseManager dbManager;    
    private String currentCard = "reqorderPanel";   
    protected final static int LOCAL_MODE = 1;
    protected final static int REMOTE_MODE = 2;    
    protected static boolean REQORDING;
    
    public GUI(BackgroundService bgs)
    {        
        SetLookAndFeel("Nimbus");//do this before initComponents()
        backgroundService = bgs;
        dbManager = bgs.dbManager;
        dbManager.FindDbFiles();
        initComponents();
        InitFrame();    
        InitTaskbar();   
        
        Timer splashTimer = new Timer();
        TimerTask splashTask = new TimerTask()
        {
            @Override
            public void run()
            {
                Login();
            }
        };
        splashTimer.schedule(splashTask, 2000);  
        
    }//end constructor
    
    private void InitFrame()
    {
        //put the frame at middle of the screen,add icon and set visible
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        URL imageURL = GUI.class.getClassLoader().getResource("Images/icon.png");
        Image icon = Toolkit.getDefaultToolkit().getImage(imageURL);
        setIconImage(icon);        
        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        setVisible(true);
    }
    
    private void InitTaskbar()
    {           
        for (LookAndFeelInfo LFI : UIManager.getInstalledLookAndFeels())
        {
            JRadioButtonMenuItem radioButtonMenuItem = new JRadioButtonMenuItem(LFI.getName());
            if(LFI.getName().equals("Nimbus"))
                radioButtonMenuItem.setSelected(true); 
                
            radioButtonMenuItem.addActionListener((ActionEvent e) ->
            {     
                appearanceMenu.setVisible(false);
                SetLookAndFeel(e.getActionCommand());
            });
            appearanceGroup.add(radioButtonMenuItem);
            appearanceMenu.add(radioButtonMenuItem);
        }
    }
    
    protected void Login()
    {           
        try
        {          
            CardLayout card = (CardLayout) mainPanel.getLayout();  
            File hashFile = new File(System.getProperty("user.dir") + "/bin/auth");
            File dbaFile = new File(System.getProperty("user.dir") + "/bin/dba.mv.db");
            if(hashFile.exists())
            {            
                String storedHash = Files.readString(hashFile.toPath(), StandardCharsets.UTF_8);
                //if no password was set by user
                if(storedHash.isEmpty())
                {
                    //if the hash is empty, the properties db will not be encrypted. So there is no additional security
                    //risk in case the auth file was swapped    
                     File dba = new File(System.getProperty("user.dir") + "/bin/dba.mv.db");
                    if(dba.exists())
                    {     
                        //Reqorderpassword is empty string                   
                        DatabaseManager.dbPassword = dbManager.RetrieveDbPassword(("").toCharArray());
                        DatabaseManager.reqorderPassword = ("").toCharArray();
                        LoginComplete();
                    } 
                    else
                        AuthenticatorMissing();
                }
                else
                {
                    //if the hash in the authfile was swapped, a malicious actor could bypass the login screen, but
                    //since the dba database is encrypted with the password that only the user should know there is no
                    //security risk to keeping the auth file accessible to anyone      
                    File dba = new File(System.getProperty("user.dir") + "/bin/dba.mv.db");
                    if(dba.exists())
                    {
                        card.show(mainPanel, "loginPanel"); 
                        loginPanel.CheckForCapsLock();
                        loginPanel.passwordField.requestFocus();                        
                    }
                    else
                        AuthenticatorMissing();
                }
            }
            //if hashFile doesn't exist
            else
            {
                //if dba file exists but auth file doesn't, it means auth has been deleted by someone
                if(dbaFile.exists())
                    AuthenticatorMissing();
                else
                {                 
                    card.show(mainPanel, "newUserPanel");
                    newUserPanel.passwordField1.requestFocus();
                }
            }
        }
        catch (IOException e)
        {
            BackgroundService.AppendLog(e);
        }
    }  
    
    private void AuthenticatorMissing()
    {
        JOptionPane.showMessageDialog(this,
                Utilities.AllignCenterHTML(Main.BUNDLE.getString("authError")),
                 Main.BUNDLE.getString("authErrorTitle"), JOptionPane.WARNING_MESSAGE);
        
        if(JOptionPane.showConfirmDialog(this,
                Utilities.AllignCenterHTML(Main.BUNDLE.getString("saveInaccessibleMessage")), 
                Main.BUNDLE.getString("saveInaccessibleTitle"), 
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
        {            
            dbManager.MoveInaccessibleFiles(true);
        }
        else
            dbManager.MoveInaccessibleFiles(false);
            
        ShowNewUserPanel();
    }
    
    /**
     * The first step to authentication is Login() which is called in the<br>
     * constructor of this class. That method checks for hash file, if not found<br>
     * it opens the NewUserPanel, if it is found and the password is an empty<br>
     * string it calls this method, if the hash is not empty it switches to the<br>
     * login panel for authentication and calls this method if validated.<br><br>
     *
     * There are 6 cases in which this method gets called: <br><br>
     * 1. after user has created a new password in NewUserPanel <br>
     * 2. after user has opted for no password in NewUserPanel<br>
     * 3. after user has clicked login with empty password field in GUI.Login()<br>
     * 4. after user has clicked login with correct password in LoginPanel.loginButtonActionPerformed()<br>
     * 5. after user has created a new properties database in createPropertiesBtnActionPerformed()<br>
     * 6. after user has imported a properties database in importButtonActionPerformed()<br><br>
     *
     * The second statement in this method is a call to InitDB(), which looks for databases<br>
     * in the database folder and stores them in a list If does not find a<br>
     * properties db, it switches to create properties panel where the user can<br>
     * create or import a properties db. After which it will call this method<br>
     * again, and thus InitDB again, but this time there will be a properties<br>
     * db. Cases 5 and 6 thus call this function a second time after<br>
     * initialising the databases.
     */
    protected void LoginComplete()
    {       
        //must be done after login, password has to be initialised
        dbManager.CheckDbFiles();
        dbManager.SetSocket();
        
        //returns false if databases were not initialised
        if(!InitDB(LOCAL_MODE))
            return;
        
        //must be done after initDB has populated the dbFiles
        alertsPanel.balanceAlertsPanel.Intitialise(this, dbManager);
        //needs to be done after dbPassword was initialised
        monitorPanel.CreateMonitorTree();
        mainToolbar.setVisible(true); 
        reqorderPanel.SelectDocumentationNode();        
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "reqorderPanel");        
        System.gc();   
    } 
    
    //RE-IMPLEMENT THIS TO ADD REMOTE FUNCTIONALITY
    /** 
     * This function first checks if properties db exists and is accessible. If so, it then inserts all db's in 
     * db folder into db lists, and populates the databases tree.
     * @return properties found and accessible
     * @param modeType 1 = local, 2 = remote (0 = choose, for switching purposes)
     */
    protected boolean InitDB(int modeType)
    {
        //temporary before remote implementation
        if(modeType == GUI.REMOTE_MODE)
            return false;
        
        File checkFile = new File(System.getProperty("user.dir") + "/databases/properties.mv.db");
        if(checkFile.exists())
        {
            if(ConnectionDB.CanConnect( "properties", DatabaseManager.dbPassword))
            {
                dbManager.InsertDbFiles();
                reqorderPanel.ShowDatabasesNode(); 
                return true;
            }
            else
            {
                JOptionPane.showMessageDialog(this, 
                            Utilities.AllignCenterHTML(Main.BUNDLE.getString("propertiesErrorRedirect")),
                            Main.BUNDLE.getString("propertiesErrorTitle"), JOptionPane.WARNING_MESSAGE);
                
                dbManager.MoveInaccessibleFile("properties");
                ShowNewUserPanel();
                return false;
            }
        }
          //show create or import properties database option panel
        else
        {          
            CardLayout card = (CardLayout) mainPanel.getLayout();
            card.show(mainPanel, "reqorderPanel");
            reqorderPanel.ShowCreatePropertiesPanel();
            return false;
        }        
    }   
    
    protected void ShowNewUserPanel()
    {
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "newUserPanel");
        newUserPanel.CheckForCapsLock();
        newUserPanel.passwordField1.requestFocus();
    }
    
    protected void ShowCreatePropertiesPanel()
    {
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "reqorderPanel");
        reqorderPanel.ShowCreatePropertiesPanel();
    }
     
    protected void ShowLoadScreen()
    {      
        //setting the label to visible will make the logo jump up. Label start text is 3 line breaks.
        statusLabel.setText(Utilities.AllignCenterHTML(Main.BUNDLE.getString("loginSuccess")));
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "splashPanel");
    }
    
    protected void ShowChangePasswordPanel()
    {
        newUserPanel.mainTextLabel.setVisible(false);
        newUserPanel.changePasswordText.setVisible(true);
        newUserPanel.passwordField1.setText("");
        newUserPanel.passwordField2.setText("");
        newUserPanel.savePasswordButton.setEnabled(false);
        newUserPanel.passwordStatusLabel.setText(Main.BUNDLE.getString("waitingForInput"));
        newUserPanel.backButton.setVisible(true);
        ShowNewUserPanel();
    }
    
    protected void PopulateDatabasesTree()
    {
        reqorderPanel.PopulateDatabasesTree();
    }
    
    protected void GoToSettings()
    {
        reqorderPanel.GoToSettings();
    }
    
    protected void StopReqording()
    {
        reqorderPanel.StopReqording();
    }
    
    protected void ExpandTree(JTree tree, int nodeLevel)
    {
        var currentNode = (DefaultMutableTreeNode) tree.getModel().getRoot();        
        
        do
        {    
            if (currentNode.getLevel() == nodeLevel) 
            {
                tree.expandPath(new TreePath(currentNode.getPath()));
            }
            
            currentNode = currentNode.getNextNode();
        } 
        while (currentNode != null);
    }
    
    protected void ExpandNode(JTree tree, DefaultMutableTreeNode currentNode,int nodeLevel)
    {        
        DefaultMutableTreeNode original = currentNode;
        do
        {
            if (currentNode.getLevel() == nodeLevel) 
                tree.expandPath(new TreePath(currentNode.getPath()));
            
            currentNode = currentNode.getNextNode().isNodeAncestor(original) ? currentNode.getNextNode() : null;            
        } 
        while (currentNode != null);
    }

    private void SetLookAndFeel(String styleString)
    {
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if (styleString.equals(info.getName()))
                {
                    //in case nimbus dark mode button text is not visible
//                    if(styleString.equals("Nimbus"))
//                        UIManager.getLookAndFeelDefaults().put("Button.textForeground", Color.BLACK);  
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    SwingUtilities.updateComponentTreeUI(this);
                    if(alertsPanel != null)
                        SwingUtilities.updateComponentTreeUI(alertsPanel.balanceAlertsDialog);
                    if(appearanceMenu != null)
                        SwingUtilities.updateComponentTreeUI(appearanceMenu);
                    break;
                }
            }
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }
    
    protected void exitButtonActionPerformed(java.awt.event.ActionEvent evt)                                           
    {                                               
        if(REQORDING)
        {
            if(JOptionPane.showConfirmDialog(
                    this,
                    Utilities.AllignCenterHTML(Main.BUNDLE.getString("exitConfirm")),
                    Main.BUNDLE.getString("exitConfirmTitle"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION)
            {
                reqorderPanel.StopReqording();
                backgroundService.BackupAndExit();
            }
        }
        else
            backgroundService.BackupAndExit();
    }       
    
    protected void reqorderButtonActionPerformed(java.awt.event.ActionEvent evt)                                               
    {                                                   
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "reqorderPanel";
        card.show(mainPanel, currentCard);
        if (monitorPanel.timer != null)
            monitorPanel.timer.cancel();
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);
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

        appearanceGroup = new javax.swing.ButtonGroup();
        appearanceMenu = new javax.swing.JPopupMenu();
        trayPopup = new javax.swing.JDialog();
        jLabel11 = new javax.swing.JLabel();
        mainToolbar = new javax.swing.JToolBar();
        mainToolbar.setVisible(false);
        reqorderButton = new javax.swing.JButton();
        chartsButton = new javax.swing.JButton();
        alertsButton = new javax.swing.JButton();
        nodeMonitorButton = new javax.swing.JButton();
        appearanceButton = new javax.swing.JButton();
        logButton = new javax.swing.JButton();
        donateButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        mainPanel = new javax.swing.JPanel();
        splashPanel = new javax.swing.JPanel();
        logoLabel = new javax.swing.JLabel();
        statusLabel = new javax.swing.JLabel();
        reqorderPanel = new reqorder.ReqorderPanel();
        reqorderPanel.Initialise(this);
        newUserPanel = new reqorder.NewUserPanel();
        newUserPanel.Intialise(this);
        loginPanel = new reqorder.LoginPanel();
        loginPanel.Initialise(this);
        chartsPanel = new reqorder.ChartsPanel();
        chartsPanel.Intialise(this);
        alertsPanel = new reqorder.AlertsPanel();
        alertsPanel.Initialise(this);
        monitorPanel = new reqorder.MonitorPanel();
        monitorPanel.Initialise(this);
        logPanel = new reqorder.LogPanel();

        appearanceMenu.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseExited(java.awt.event.MouseEvent evt)
            {
                appearanceMenuMouseExited(evt);
            }
        });

        trayPopup.setUndecorated(true);

        jLabel11.setBackground(new java.awt.Color(159, 159, 159));
        jLabel11.setFont(new java.awt.Font("Segoe UI", 3, 12)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(0, 0, 0));
        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("<html><div style='text-align: center;'>ReQorder is running in the background<br/>\nDouble click on the system tray icon to open the UI<br/><br/>\nTo exit the program, click 'Exit' in the menu bar<br/>\nYou can also right click the system tray icon and click 'Exit'</div><html>");
        jLabel11.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(49, 0, 0), 4, true), new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED, new java.awt.Color(54, 56, 72), new java.awt.Color(84, 55, 55), new java.awt.Color(58, 77, 96), new java.awt.Color(72, 50, 50))));
        jLabel11.setOpaque(true);
        jLabel11.setPreferredSize(new java.awt.Dimension(380, 120));

        javax.swing.GroupLayout trayPopupLayout = new javax.swing.GroupLayout(trayPopup.getContentPane());
        trayPopup.getContentPane().setLayout(trayPopupLayout);
        trayPopupLayout.setHorizontalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel11, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
        );
        trayPopupLayout.setVerticalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel11, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
        );

        setTitle("ReQorder");
        setMinimumSize(new java.awt.Dimension(500, 600));
        setPreferredSize(new java.awt.Dimension(600, 600));
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                windowHandler(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        mainToolbar.setFloatable(false);
        mainToolbar.setRollover(true);

        reqorderButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/reqorder.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        reqorderButton.setText(bundle.getString("reqorderButton")); // NOI18N
        reqorderButton.setToolTipText("");
        reqorderButton.setFocusable(false);
        reqorderButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        reqorderButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        reqorderButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    reqorderButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(reqorderButton);

            chartsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/charts.png"))); // NOI18N
            chartsButton.setText(bundle.getString("chartsButton")); // NOI18N
            chartsButton.setFocusable(false);
            chartsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            chartsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            chartsButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    chartsButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(chartsButton);

            alertsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/alerts.png"))); // NOI18N
            alertsButton.setText(bundle.getString("alertsButton")); // NOI18N
            alertsButton.setFocusable(false);
            alertsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            alertsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            alertsButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    alertsButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(alertsButton);

            nodeMonitorButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/monitor.png"))); // NOI18N
            nodeMonitorButton.setText(bundle.getString("nodeMonitorButton")); // NOI18N
            nodeMonitorButton.setToolTipText("Current info on you node's status");
            nodeMonitorButton.setFocusable(false);
            nodeMonitorButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            nodeMonitorButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            nodeMonitorButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    nodeMonitorButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(nodeMonitorButton);

            appearanceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/Appearance.png"))); // NOI18N
            appearanceButton.setText(bundle.getString("appearanceButton")); // NOI18N
            appearanceButton.setFocusable(false);
            appearanceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            appearanceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            appearanceButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    appearanceButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(appearanceButton);

            logButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/log.png"))); // NOI18N
            logButton.setText(bundle.getString("logButton")); // NOI18N
            logButton.setToolTipText("");
            logButton.setFocusable(false);
            logButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            logButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            logButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    logButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(logButton);

            donateButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/donate.png"))); // NOI18N
            donateButton.setText(bundle.getString("donateButton")); // NOI18N
            donateButton.setFocusable(false);
            donateButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            donateButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            donateButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    donateButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(donateButton);

            exitButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/exit.png"))); // NOI18N
            exitButton.setText(bundle.getString("exitButton")); // NOI18N
            exitButton.setFocusable(false);
            exitButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            exitButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

            exitButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        exitButtonActionPerformed(evt);
                    }
                });
                mainToolbar.add(exitButton);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.ipady = 11;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                gridBagConstraints.weightx = 1.0;
                getContentPane().add(mainToolbar, gridBagConstraints);

                mainPanel.setLayout(new java.awt.CardLayout());

                splashPanel.setBackground(new java.awt.Color(51, 51, 51));
                splashPanel.setLayout(new java.awt.GridBagLayout());

                logoLabel.setBackground(new java.awt.Color(51, 51, 51));
                logoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/splash_logo.png"))); // NOI18N
                splashPanel.add(logoLabel, new java.awt.GridBagConstraints());

                statusLabel.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
                statusLabel.setForeground(new java.awt.Color(166, 166, 166));
                statusLabel.setText("<html><div style='text-align: center;'<br/><br/><br/></div><html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                splashPanel.add(statusLabel, gridBagConstraints);

                mainPanel.add(splashPanel, "splashPanel");
                mainPanel.add(reqorderPanel, "reqorderPanel");
                mainPanel.add(newUserPanel, "newUserPanel");
                mainPanel.add(loginPanel, "loginPanel");
                mainPanel.add(chartsPanel, "chartsPanel");
                mainPanel.add(alertsPanel, "alertsPanel");
                mainPanel.add(monitorPanel, "monitorPanel");
                mainPanel.add(logPanel, "logPanel");

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                getContentPane().add(mainPanel, gridBagConstraints);

                pack();
                setLocationRelativeTo(null);
            }// </editor-fold>//GEN-END:initComponents

    private void nodeMonitorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nodeMonitorButtonActionPerformed
    {//GEN-HEADEREND:event_nodeMonitorButtonActionPerformed
        monitorPanel.isSynced = true; //first ping this flag must be true to activate time approximation
        CardLayout card = (CardLayout) mainPanel.getLayout();
        //We only need to run the GUI timer if monitorPanel is selected/in focus
        if (!currentCard.equals("monitorPanel"))
            monitorPanel.RestartTimer();
        
        currentCard = "monitorPanel";
        card.show(mainPanel, currentCard);
        if(monitorPanel.startTime == 0)
            monitorPanel.startTime = System.currentTimeMillis();
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_nodeMonitorButtonActionPerformed

    private void logButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_logButtonActionPerformed
    {//GEN-HEADEREND:event_logButtonActionPerformed
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "logPanel";
        card.show(mainPanel, currentCard);
        if (monitorPanel.timer != null)
            monitorPanel.timer.cancel();
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_logButtonActionPerformed

    private void windowHandler(java.awt.event.WindowEvent evt)//GEN-FIRST:event_windowHandler
    {//GEN-HEADEREND:event_windowHandler
        backgroundService.SetGUIEnabled(false);
    }//GEN-LAST:event_windowHandler

    private void appearanceMenuMouseExited(java.awt.event.MouseEvent evt)//GEN-FIRST:event_appearanceMenuMouseExited
    {//GEN-HEADEREND:event_appearanceMenuMouseExited
        appearanceMenu.setVisible(false);
    }//GEN-LAST:event_appearanceMenuMouseExited

    private void appearanceButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_appearanceButtonActionPerformed
    {//GEN-HEADEREND:event_appearanceButtonActionPerformed
        //Menu bar did can not listen for mouse click on menu, only for menu items.This is a problem for the other buttons.
        //Using a custom pop up menu for setting look and feel. Tried many listeners (focus, mouseEntered and Exited etc.) show() works best
        //Using setVisible creates problems getting rid of the popup. Using the buttons location in show() would place the menu with an offset
        appearanceMenu.setLocation(appearanceButton.getLocationOnScreen().x,appearanceButton.getLocationOnScreen().y);
        appearanceMenu.show(appearanceButton, appearanceMenu.getX(),appearanceMenu.getY());
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_appearanceButtonActionPerformed

    private void chartsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_chartsButtonActionPerformed
    {//GEN-HEADEREND:event_chartsButtonActionPerformed
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "chartsPanel";
        card.show(mainPanel, currentCard);
        if (monitorPanel.timer != null)
            monitorPanel.timer.cancel();
        
        chartsPanel.PopulateChartsTree();
        chartsPanel.ResetChartPanelBoxes();
        chartsPanel.createChartButton.setEnabled(false);   
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);     
    }//GEN-LAST:event_chartsButtonActionPerformed

    private void alertsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_alertsButtonActionPerformed
    {//GEN-HEADEREND:event_alertsButtonActionPerformed
       alertsPanel.LoadPanel();        
        
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "alertsPanel";
        card.show(mainPanel, currentCard);
        if (monitorPanel.timer != null)
            monitorPanel.timer.cancel();   
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        chartsPanel.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_alertsButtonActionPerformed

    private void donateButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_donateButtonActionPerformed
    {//GEN-HEADEREND:event_donateButtonActionPerformed
        reqorderPanel.GoToDonatePage();
    }//GEN-LAST:event_donateButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton alertsButton;
    protected reqorder.AlertsPanel alertsPanel;
    private javax.swing.JButton appearanceButton;
    private javax.swing.ButtonGroup appearanceGroup;
    private javax.swing.JPopupMenu appearanceMenu;
    private javax.swing.JButton chartsButton;
    private reqorder.ChartsPanel chartsPanel;
    private javax.swing.JButton donateButton;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JButton logButton;
    protected reqorder.LogPanel logPanel;
    private reqorder.LoginPanel loginPanel;
    private javax.swing.JLabel logoLabel;
    protected javax.swing.JPanel mainPanel;
    protected javax.swing.JToolBar mainToolbar;
    protected reqorder.MonitorPanel monitorPanel;
    private reqorder.NewUserPanel newUserPanel;
    private javax.swing.JButton nodeMonitorButton;
    private javax.swing.JButton reqorderButton;
    private reqorder.ReqorderPanel reqorderPanel;
    private javax.swing.JPanel splashPanel;
    protected javax.swing.JLabel statusLabel;
    public javax.swing.JDialog trayPopup;
    // End of variables declaration//GEN-END:variables

        
}//end class GUI




