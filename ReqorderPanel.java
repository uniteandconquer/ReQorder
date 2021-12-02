package reqorder;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.h2.tools.ChangeFileEncryption;

public class ReqorderPanel extends javax.swing.JPanel
{    
    private GUI gui;
    private DatabaseManager dbManager;
    /**Used to check core status before StartReqording()*/
    private boolean online;  
    /**0 = choose , 1 = local, 2 = remote */
    private int modeType = 0;
    private  String selectedDatabase;
    private String databaseInSession;
    private String lastSelectedAddress;
    private String editedWatchlist;
    private final String TEMPWATCHLIST = "TEMP_WL_PLACEHOLDER";
    private DefaultMutableTreeNode documentationNode;
    private DefaultMutableTreeNode databasesNode;
    private DefaultMutableTreeNode propertiesNode;
    private ArrayList<KeyItemPair> valuePairs;
    private DefaultTreeModel databasesTreeModel;
    private final DefaultListModel addressListModel;
    private final DefaultListModel watchlistsListModel;
    private DefaultMutableTreeNode selectedNode;
    private String reqordingNode;
    private final ArrayList<DocuPage> guideHistory;   
    private Timer sessionTimer;   
    
    public ReqorderPanel()
    {
        initComponents();  
        addressListModel = (DefaultListModel) addressesList.getModel();
        watchlistsListModel = (DefaultListModel) watchlistsList.getModel();   
        guideHistory = new ArrayList<>();
    } 
    
    protected void Initialise(GUI gui)
    {
        this.gui = gui;
        this.dbManager = gui.dbManager;      
        buildVersionLabel.setText(BackgroundService.BUILDVERSION);
        InitListeners();
        InitTrees();
    }
    
    private void InitListeners()
    {
        //Used this listener to add double click functionality. Private class TreeSelector (under GUI class) is obsolete if we keep this method
        MouseListener ml = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                int selRow = databasesTree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = databasesTree.getPathForLocation(e.getX(), e.getY());
                if (selRow != -1)
                {
                    if (e.getClickCount() == 1)
                    {
                        TreeSelector(selPath, false);
                    }
                    else if (e.getClickCount() == 2)
                    {
                        TreeSelector(selPath, true);
                    }
                }
            }
        };
        databasesTree.addMouseListener(ml);

        guideEditorPane.addHyperlinkListener((HyperlinkEvent ev) ->
        {
            if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
            {
                try
                {
                    //to external browser
                    if (ev.getDescription().startsWith("http"))
                    {
                        OpenBrowser.OpenLink(ev.getURL().toURI());
                    }
                    //to internal documentation files
                    else
                    {
                        if (ev.getDescription().equals("BACK"))
                        {
                            int lastGuideScrollPos = guideHistory.get(guideHistory.size() - 1).scrollValue;
                            guideEditorPane.setPage(guideHistory.get(guideHistory.size() - 1).url);
                            guideHistory.remove(guideHistory.size() - 1);
                            //We want the scrollbar to return to the last position. Due to the event triggering getting performed on the EDT
                            //the setValue() gets performed before the page is set. SwingUtilities.invokeLater was not working, this timer with delay does the trick
                            TimerTask task = new TimerTask()
                            {
                                @Override
                                public void run()
                                {
                                    guideScrollPane.getVerticalScrollBar().setValue(lastGuideScrollPos);
                                }
                            };
                            Timer t = new Timer();
                            t.schedule(task, 10);
                        }
                        else
                        {
                            //We want to remember for every docu page in browse history what the scrollbar value was when user clicked on a link
                            guideHistory.add(new DocuPage(guideEditorPane.getPage(), guideScrollPane.getVerticalScrollBar().getValue()));
                            guideEditorPane.setPage(GUI.class.getClassLoader().getResource("Documentation/" + ev.getDescription()));
                        }
                    }
                }
                catch (IOException | URISyntaxException e)
                {
                    JOptionPane.showMessageDialog(null, e.toString());
                    BackgroundService.AppendLog(e);
                }
            }
        });
    }   
    
    private void InitTrees()
    {
        databasesTreeModel = (DefaultTreeModel) databasesTree.getModel();
        documentationNode = new DefaultMutableTreeNode(new NodeInfo("Documentation", "help.png"));  
        databasesNode = new DefaultMutableTreeNode(new NodeInfo("Databases", "server.png"));
        databasesTreeModel.setRoot(documentationNode);
        documentationNode.add(databasesNode);    
        databasesTreeModel.reload(databasesNode);        
        gui.ExpandTree(databasesTree,0);           
    }
    
    protected void ShowDatabasesNode()
    {
        CardLayout card = (CardLayout) reqorderPanel.getLayout();
        card.show(reqorderPanel, "DB_chooserPanel");
        card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "dbCreatePanel");

        PopulateDatabasesTree();
        //after populate, otherwise it will be set to default again (until RefreshTree() implementation)
        NodeInfo ni = (NodeInfo) databasesNode.getUserObject();
        ni.nodeName = "Databases (local mode)";
        var dmt = (DefaultTreeModel) databasesTree.getModel();
        dmt.reload(databasesNode);
        gui.ExpandNode(databasesTree,databasesNode,1);   
    }
    
    protected void ShowCreatePropertiesPanel()
    {        
        CardLayout card = (CardLayout) reqorderPanel.getLayout();
        card.show(reqorderPanel, "propertiesPanel");
    }
    
    protected void PopulateDatabasesTree()
    {       
        ArrayList<String> dbFiles = dbManager.GetDbFiles();
        
        documentationNode = new DefaultMutableTreeNode(new NodeInfo("Documentation", "help.png"));  
        databasesNode = new DefaultMutableTreeNode(new NodeInfo("Databases", "server.png"));
        databasesTreeModel.setRoot(documentationNode);
        documentationNode.add(databasesNode);   

        DefaultMutableTreeNode[] databaseNodes = new DefaultMutableTreeNode[dbFiles.size()];

        for (int i = 0; i < dbFiles.size(); i++)
        {
            String dbName = dbFiles.get(i);     
            
            if(dbName.equals("properties"))
            {
                databaseNodes[i] = new DefaultMutableTreeNode(new NodeInfo(dbName, "properties.png")); 
                documentationNode.add(databaseNodes[i]);    
                propertiesNode = databaseNodes[i];
            }
            else
            {                   
                if(dbName.equals(reqordingNode))
                    databaseNodes[i] = new DefaultMutableTreeNode(new NodeInfo(dbName, "reqording.png", Color.RED));
                else
                {
                    if(DatabaseManager.encryptedFiles.contains(dbName))
                       databaseNodes[i] = new DefaultMutableTreeNode(new NodeInfo(dbName, "database_locked.png"));
                    else
                       databaseNodes[i] = new DefaultMutableTreeNode(new NodeInfo(dbName, "database.png"));
                        
                } 
                databasesNode.add(databaseNodes[i]);                
            }
            
            ArrayList<DefaultMutableTreeNode> addressNodes = new ArrayList<>();
            DefaultMutableTreeNode watchlistNode = null;
            
            try (Connection connection = ConnectionDB.getConnection( dbName))
            {                
                for(var table : dbManager.GetTables(connection))
                {
                    var tableNode = new DefaultMutableTreeNode(new NodeInfo(table, "table.png"));
                    if(dbName.equals("properties"))
                    {
                        if(!table.equals("MAIL_SERVER"))
                            databaseNodes[i].add(tableNode);    
                    }
                    //For clarity, we want watchlist address to be children of my_watchlist                
                    else
                    {
                        if(table.startsWith("WL_"))
                            addressNodes.add(tableNode);
                        else if(table.equals("MY_WATCHLIST"))
                        {
                            watchlistNode = tableNode;
                            databaseNodes[i].add(tableNode);                    
                        }
                        else
                            databaseNodes[i].add(tableNode);                      
                    }  

                    dbManager.GetColumnHeaders(table,connection).forEach(item ->
                    {                
                        tableNode.add(new DefaultMutableTreeNode(new NodeInfo(item, "item.png")));
                    });
                } 
                
                connection.close();
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            } 
            
            if(watchlistNode != null)
                for(var node : addressNodes)
                    watchlistNode.add(node);
        }

        databasesTreeModel.reload(documentationNode);
        
        gui.ExpandNode(databasesTree,databasesNode,1);
        
    }     
    
    private void PopulateWatchlistsList()
    {
        watchlistsListModel.clear();
        
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {            
           dbManager.GetTables(connection).forEach(s ->
            {
                if(s.startsWith("WL_") && !s.equals(TEMPWATCHLIST))
                    watchlistsListModel.addElement(s.substring(3));
            });  
           
           connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }  
    }   
    
    //populates the data table panel when the watchlists manager is accessed 
    private void PopulateWatchlistTable()
    {
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {
            dbManager.FillJTable(editedWatchlist, "", itemsTable, connection);
            connection.close();            
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }   
    }

    protected void GoToSettings()
    {
        gui.reqorderButtonActionPerformed(null);
        TreePath path = new TreePath(propertiesNode.getPath());
        databasesTree.setSelectionPath(path);
        TreeSelector(path, false);
    }   
    
    private void AddressSelected()
    {       
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {            
            String address = addressesList.getSelectedValue();
            //this method gets callled after getValueIsAdjusting() is triggered, which will sometimes cause the arg to be null
            if(address == null)
                return;

            blocksMintedBox.setSelected(
                    (boolean)dbManager.GetItemValue(editedWatchlist,"blocksminted","address", Utilities.SingleQuotedString(address),connection));
            levelBox.setSelected(
                    (boolean)dbManager.GetItemValue(editedWatchlist,"level","address", Utilities.SingleQuotedString(address),connection));
            balanceBox.setSelected(
                    (boolean)dbManager.GetItemValue(editedWatchlist,"balance","address", Utilities.SingleQuotedString(address),connection));
            balanceSpinner.setValue(
                    (double)dbManager.GetItemValue(editedWatchlist,"balancetreshold","address", Utilities.SingleQuotedString(address),connection));

            dbManager.FillJTable(editedWatchlist, " where address=" + Utilities.SingleQuotedString(address), itemsTable,connection);
            
            connection.close();      

            RefreshAddressComponents(true);              
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }   
    }
    
    private  void SaveAddressChanges()
    {
        //When user clicks save watchlist and has not made any changes to address, or exited the watchlist editor through the 
        //tree, lastSelectedAddress will be null, default values will remain in the watchlist if it is saved
        if(lastSelectedAddress == null)
            return;
        
        valuePairs = new ArrayList<>();
        valuePairs.add(new KeyItemPair("blocksminted", String.valueOf(blocksMintedBox.isSelected()), "address", Utilities.SingleQuotedString(lastSelectedAddress)));
        valuePairs.add(new KeyItemPair("level", String.valueOf(levelBox.isSelected()), "address", Utilities.SingleQuotedString(lastSelectedAddress)));
        valuePairs.add(new KeyItemPair( "balance", String.valueOf(balanceBox.isSelected()), "address", Utilities.SingleQuotedString(lastSelectedAddress)));
        valuePairs.add(new KeyItemPair("balancetreshold", String.valueOf(balanceSpinner.getValue()), "address", Utilities.SingleQuotedString(lastSelectedAddress)));
        
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {
            {
                dbManager.ChangeValues(editedWatchlist, valuePairs,connection);
            }
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    private void WatchlistSelected()
    {
        //for some reason, the listener sometimes gets triggered without selecting an item in watchlistsList
        if(watchlistsList.getSelectedValue() == null)
        {
            editWatchlistButton.setEnabled(false);   
            applyWatchlistButton.setEnabled(false);
            deleteWatchlistButton.setEnabled(false);
            return;
        }
        
        deleteWatchlistButton.setEnabled(true);
        editWatchlistButton.setEnabled(true);
        applyWatchlistButton.setEnabled(true);
        
        PopulateWatchlistTable();
    }
    
    private void RefreshAddressComponents(boolean enableValue)
    {
        removeAddressButton.setEnabled(enableValue);
        blocksMintedBox.setEnabled(enableValue);
        levelBox.setEnabled(enableValue);
        balanceBox.setEnabled(enableValue);
        balanceSpinner.setEnabled(balanceBox.isSelected());    
    }
    
    private void RefreshNodePrefsComponents()
    {        
        try (Connection connection = ConnectionDB.getConnection( selectedDatabase))
        {        
            //ATTENTION: this error should not  be thrown but could pop up due to coding mistakes in versioning differences, remove later on
            if(!dbManager.TableExists("node_prefs", connection))
            {
                JOptionPane.showMessageDialog(this, "Error: Could not find 'node_prefs' table", "Database error", JOptionPane.ERROR_MESSAGE);
                connection.close();
                return;
            }
            blockHeightBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "blockheight", "id", "0",connection));
            myBlockHeightBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "myblockheight", "id", "0",connection));
            numberOfConnectionsBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "numberofconnections", "id", "0",connection));
            allKnownPeersBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "allknownpeers", "id", "0",connection));
            allOnlineMintersBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "allonlineminters", "id", "0",connection));
            uptimeBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "uptime", "id", "0",connection));
            ltcPriceBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "ltcprice", "id", "0",connection));
            dogePriceBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "dogeprice", "id", "0",connection));
            dataUsageBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "data_usage", "id", "0",connection));
            cpu_tempBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "cpu_temp", "id", "0",connection));
            blockchainSizeBox.setSelected((boolean)dbManager.GetItemValue("node_prefs", "blockchainsize", "id", "0",connection));

            int storedUpdateDelta = (int)dbManager.GetItemValue("node_prefs", "updatedelta", "id", "0",connection);
            hourSpinner.setValue((int)TimeUnit.MILLISECONDS.toHours(storedUpdateDelta));
            minuteSpinner.setValue((int)TimeUnit.MILLISECONDS.toMinutes(storedUpdateDelta) % 60);
            
            connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }   
    }
    
    private void AddressComponentsToDefault()
    {
            blocksMintedBox.setSelected(true); 
            levelBox.setSelected(true);
            balanceBox.setSelected(true);
            balanceSpinner.setValue(.1);          
    }
    
    private boolean savedDbPrefs()                                                  
    {                                                      
       var selectedItems = new ArrayList<String>();
        Component[] components = dbOptionsPanel.getComponents();
        JCheckBox checkBox;
        //get all selected checkboxes and add them to a string array
        for(Component c : components)
            if(c instanceof JCheckBox)
            {
                checkBox = (JCheckBox) c;
                if(checkBox.isSelected())
                    //used actioncommand in previous version of this code to crosscheck with database
                    selectedItems.add(checkBox.getActionCommand());
            }
        
        if(selectedItems.isEmpty())
        {
            JOptionPane.showMessageDialog(null, "You must select at least one item to reQord");
            return false;
        }

        int updatedelta = ((int) minuteSpinner.getValue() + ((int) hourSpinner.getValue() * 60)) * 60000;
        //if SNPreferences returns false, prefs were not saved and this method returns false
        return  dbManager.SaveNodePreferences(new String[]{
            selectedDatabase,
            "blockheight",String.valueOf(blockHeightBox.isSelected()),
            "myblockheight",String.valueOf(myBlockHeightBox.isSelected()),
            "numberofconnections",String.valueOf(numberOfConnectionsBox.isSelected()),
            "uptime",String.valueOf(uptimeBox.isSelected()),
            "allknownpeers",String.valueOf(allKnownPeersBox.isSelected()),
            "allonlineminters",String.valueOf(allOnlineMintersBox.isSelected()),
            "ltcprice",String.valueOf(ltcPriceBox.isSelected()),
            "dogeprice",String.valueOf(dogePriceBox.isSelected()),
            "data_usage",String.valueOf(dataUsageBox.isSelected()),
            "cpu_temp",String.valueOf(cpu_tempBox.isSelected()),
            "blockchainsize",String.valueOf(blockchainSizeBox.isSelected()),
            "updatedelta",String.valueOf(updatedelta) });        
    }    
    
    private void StartReqording()
    {   
        if(databasesTree.getSelectionPath() == null)
        {
            JOptionPane.showMessageDialog(this, 
                    Utilities.AllignCenterHTML("No database selected"), "", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        online = true;
        try
        {       
            Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/status");        
        }
        catch (IOException | TimeoutException e)
        {     
            online = false;
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(e.toString());
        }
        if(!online)
        {            
             JOptionPane.showMessageDialog(this,
                    Utilities.AllignCenterHTML("Could not connect to Qortal core<br/>Make sure your core or SSH tunnel is running"),
                    "Not connected", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        
        if(cpu_tempBox.isSelected())
            dbManager.CheckCPU_Monitor();
        if(blockchainSizeBox.isSelected())
            dbManager.CheckBlockchainFolder();
        dbManager.CheckAlertsValidity(selectedDatabase);
        
        //show user a list of the choices he made and ask for confirmation to reqord
        databaseInSession = selectedDatabase;
        GUI.REQORDING = true;  
        reqordButton.setText("Stop ReQording");
        reqordButton.setForeground(Color.red);
        reqordingNode = selectedDatabase;
        
        var node = (DefaultMutableTreeNode) databasesTree.getLastSelectedPathComponent();
        NodeInfo ni = (NodeInfo) node.getUserObject();
        ni.SetIconName("reqording.png");
        databasesTreeModel.nodeChanged(node);       
        dbManager.retries = -1;
        dbManager.Reqord(selectedDatabase);
        
        sessionTimer = new Timer();
        long sessionTimerTick = 60000;
        sessionTimer.scheduleAtFixedRate(new TimerTask()
        {
            long sessionTime = 0;
            
            @Override
            public void run()
            {
                sessionTimeLabel.setText("ReQording session: " + Utilities.MillisToDayHrMin(sessionTime));
                sessionTime += sessionTimerTick;
            }
        }, 0, sessionTimerTick);
        
    }
    
    public void StopReqording()
    {        
        dbManager.StopReqording();
        reqordButton.setText("Start ReQording");
        reqordButton.setForeground(Color.BLACK);
        sessionTimer.cancel();
        sessionTimeLabel.setText("Not ReQording");
        GUI.REQORDING = false;
        reqordingNode = null;
        PopulateDatabasesTree();
    }  
    
    protected void SelectDocumentationNode()
    {
        //TEMPORARY: SETS MODETYPE TO LOCAL
        localButtonActionPerformed(null);    
        
        TreePath path = new TreePath(documentationNode.getPath());
        databasesTree.setSelectionPath(path);
        TreeSelector(path, false);
    }
    
    private void TreeSelector(TreePath treePath, boolean doubleClicked)
    {
        //DONT USE TREE.GETPATH(), USE THE TREEPATH(WITH .GETPATH()) ARG PASSED TO THIS METHOD (AVOID NULL POINTER ON RIGHT CLICK)
//        System.out.println(treePath.toString() + " , " + doubleClicked);
        if(treePath == null)
            return;
        
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();

        boolean propertiesSelected = false;
        //due to properties node being one level up to database nodes, but requiring same selector behavior
        for (Object currentNode : treePath.getPath())
        {
            if (currentNode.toString().equals("properties"))
            {
                propertiesSelected = true;
                break;
            }
        }
        
        try
        {
            Connection connection; 

            switch (treePath.getPath().length)
            {            
                case 1://root                    
                    card.show(mainOptionsPanel, "guidePanel");
                    break;
                case 2://databases or properties
                    if (propertiesSelected)
                    {
                        card.show(mainOptionsPanel, "propertiesOptionsPanel");
                        connection = ConnectionDB.getConnection( "properties");
                        if(dbManager.TableExists("mail_server", connection))
                        {
                            recipientTextbox.setText((String)dbManager.GetItemValue("mail_server", "recipient", "id", "0", connection));
                            smtpServerTextfield.setText((String)dbManager.GetItemValue("mail_server", "smtp", "id", "0", connection));
                            portTextfield.setText((String)dbManager.GetItemValue("mail_server", "port", "id", "0", connection));
                            usernameTextfield.setText((String)dbManager.GetItemValue("mail_server", "username", "id", "0", connection));
                            
                            passwordField.setText("");
                            retrievePasswordButton.setEnabled(true);                    
                        }
                        if(dbManager.TableExists("socket", connection))
                        {
                            api_IP_inputField.setText((String)dbManager.GetFirstItem("socket", "ip", connection));
                            apiPortInputField.setText((String)dbManager.GetFirstItem("socket", "port", connection));
                        }
                        connection.close();
                    }
                    else
                    {
                        switch(modeType)
                        {
                            case 0:
                                card.show(mainOptionsPanel, "dbModePanel");
                                break;
                            case 1:
                            case 2:
                                card.show(mainOptionsPanel, "dbCreatePanel");
                                break;
                        }
                    }
                    break;
                case 3://database or propertiestables
                    if (propertiesSelected)
                    {
                        connection = ConnectionDB.getConnection( "properties");
                        card.show(mainOptionsPanel, "showPropsTablePanel");
                        dbManager.FillJTable(treePath.getLastPathComponent().toString(),"",itemsTable,connection);
                        connection.close();
                    }
                    else
                    {
                        card.show(mainOptionsPanel, "dbOptionsPanel");
                        selectedDatabase = treePath.getLastPathComponent().toString();
                        selectedNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                        connection = ConnectionDB.getConnection( selectedDatabase);
                        //doing this here, not in RefreshNodePrefsComponents(), will not update db's without node_prefs table
                        if(ConnectionDB.IsEncrypted(selectedDatabase))
                            encryptDbButton.setText("Decrypt database");
                        else
                            encryptDbButton.setText("Encrypt database");
                        
                        if(dbManager.TableExists("node_prefs",connection))
                            RefreshNodePrefsComponents();
                        
                        connection.close();
                    }
                    break;
                case 4://table or propertiesitems                                        
                    if (propertiesSelected)
                    {
                        card.show(mainOptionsPanel, "showPropsTablePanel");//replace with props item panel later on???
                    }
                    else
                    {
                        card.show(mainOptionsPanel, "tableOptionsPanel");
                        selectedDatabase = treePath.getPath()[treePath.getPath().length - 2].toString();
                        connection = ConnectionDB.getConnection( selectedDatabase);
                        dbManager.FillJTable(treePath.getLastPathComponent().toString(),"",itemsTable,connection);
                        connection.close();
                    }
                    break;
                case 5://item
                    //if props selected, case cannot be 5
                    card.show(mainOptionsPanel, "itemsOptionsPanel");
                    selectedDatabase = treePath.getPath()[treePath.getPath().length - 3].toString();
                    break;
            }   
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }      
    }   

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        reqorderPanel = new javax.swing.JPanel();
        DB_chooserPanel = new javax.swing.JPanel();
        mainSplitPane = new javax.swing.JSplitPane();
        treeScrollPane = new javax.swing.JScrollPane();
        databasesTree = new javax.swing.JTree();
        databasesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        optionsSplitPane = new javax.swing.JSplitPane();
        mainOptionsPanel = new javax.swing.JPanel();
        guidePanel = new javax.swing.JPanel();
        guideScrollPane = new javax.swing.JScrollPane();
        guideScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        guideEditorPane = new javax.swing.JEditorPane();
        dbOptionsScrollPane = new javax.swing.JScrollPane();
        dbOptionsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        dbOptionsPanel = new javax.swing.JPanel();
        saveDbPrefsButton = new javax.swing.JButton();
        deleteDbButton = new javax.swing.JButton();
        blockHeightBox = new javax.swing.JCheckBox();
        myBlockHeightBox = new javax.swing.JCheckBox();
        numberOfConnectionsBox = new javax.swing.JCheckBox();
        allKnownPeersBox = new javax.swing.JCheckBox();
        uptimeBox = new javax.swing.JCheckBox();
        ltcPriceBox = new javax.swing.JCheckBox();
        jSeparator1 = new javax.swing.JSeparator();
        dataUsageBox = new javax.swing.JCheckBox();
        cpu_tempBox = new javax.swing.JCheckBox();
        blockchainSizeBox = new javax.swing.JCheckBox();
        jSeparator2 = new javax.swing.JSeparator();
        watchlistsManagerButton = new javax.swing.JButton();
        jSeparator3 = new javax.swing.JSeparator();
        minuteSpinner = new javax.swing.JSpinner();
        hourIntervalLabel = new javax.swing.JLabel();
        minuteIntervalLabel = new javax.swing.JLabel();
        timeIntervalLabel1 = new javax.swing.JLabel();
        hourSpinner = new javax.swing.JSpinner();
        jSeparator4 = new javax.swing.JSeparator();
        jSeparator5 = new javax.swing.JSeparator();
        reqordButton = new javax.swing.JButton();
        allOnlineMintersBox = new javax.swing.JCheckBox();
        sessionTimeLabel = new javax.swing.JLabel();
        dogePriceBox = new javax.swing.JCheckBox();
        encryptDbButton = new javax.swing.JButton();
        dbModePanel = new javax.swing.JPanel();
        localButton = new javax.swing.JButton();
        remoteButton = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        localLabel = new javax.swing.JLabel();
        localLabel1 = new javax.swing.JLabel();
        jSeparator7 = new javax.swing.JSeparator();
        dbCreatePanel = new javax.swing.JPanel();
        createDbButton = new javax.swing.JButton();
        importDbButton = new javax.swing.JButton();
        switchModeButton = new javax.swing.JButton();
        tableOptionsPanel = new javax.swing.JPanel();
        itemsOptionsPanel = new javax.swing.JPanel();
        propOptionsScrollpane = new javax.swing.JScrollPane();
        propOptionsScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        propertiesOptionsPanel = new javax.swing.JPanel();
        setBlockchainFolderButton = new javax.swing.JButton();
        jSeparator11 = new javax.swing.JSeparator();
        smtpServerTextfield = new javax.swing.JTextField();
        portTextfield = new javax.swing.JTextField();
        passwordField = new javax.swing.JPasswordField();
        usernameTextfield = new javax.swing.JTextField();
        saveMailServerButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        testMailServerButton = new javax.swing.JButton();
        recipientTextbox = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        receivedMailCheckbox = new javax.swing.JCheckBox();
        retrievePasswordButton = new javax.swing.JButton();
        changePasswordButton = new javax.swing.JButton();
        backupAccountCheckbox = new javax.swing.JCheckBox();
        buildVersionLabel = new javax.swing.JLabel();
        importAccountButton = new javax.swing.JButton();
        jLabel9 = new javax.swing.JLabel();
        apiPortInputField = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        api_IP_inputField = new javax.swing.JTextField();
        jSeparator12 = new javax.swing.JSeparator();
        saveSocketButton = new javax.swing.JButton();
        resetDefaultButton = new javax.swing.JButton();
        showPropsTablePanel = new javax.swing.JPanel();
        deleteTableButton = new javax.swing.JButton();
        watchlistEditor = new javax.swing.JPanel();
        addressesListPane = new javax.swing.JScrollPane();
        addressesList = new javax.swing.JList(new DefaultListModel());
        addressesList.addListSelectionListener(new javax.swing.event.ListSelectionListener() 
            {
                @Override
                public void valueChanged(javax.swing.event.ListSelectionEvent event)
                {
                    if (addressListModel.getSize() > 0 && !event.getValueIsAdjusting()) 
                    {
                        if(addressesList.getSelectedValue() != null)
                        {
                            SaveAddressChanges();
                            lastSelectedAddress = addressesList.getSelectedValue();
                            AddressSelected();
                        }
                    }
                }
            });
            addAddressButton = new javax.swing.JButton();
            removeAddressButton = new javax.swing.JButton();
            saveWatchlistButton = new javax.swing.JButton();
            blocksMintedBox = new javax.swing.JCheckBox();
            balanceBox = new javax.swing.JCheckBox();
            levelBox = new javax.swing.JCheckBox();
            watchlistLabel = new javax.swing.JLabel();
            jSeparator6 = new javax.swing.JSeparator();
            balanceTresholdLabel = new javax.swing.JLabel();
            balanceSpinner = new javax.swing.JSpinner();
            watchlistEditorBackButton = new javax.swing.JButton();
            watchlistsManager = new javax.swing.JPanel();
            newWatchlistButton = new javax.swing.JButton();
            jScrollPane2 = new javax.swing.JScrollPane();
            watchlistsList = new javax.swing.JList(new DefaultListModel());
            watchlistsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() 
                {
                    @Override
                    public void valueChanged(javax.swing.event.ListSelectionEvent evt)
                    {
                        if (watchlistsListModel.getSize() > 0 && !evt.getValueIsAdjusting()) 
                        {
                            editedWatchlist = "WL_" + watchlistsList.getSelectedValue();
                            WatchlistSelected();
                        }
                    }
                });
                editWatchlistButton = new javax.swing.JButton();
                watchlistsManagerBackButton = new javax.swing.JButton();
                deleteWatchlistButton = new javax.swing.JButton();
                applyWatchlistButton = new javax.swing.JButton();
                itemsTableScrollPane = new javax.swing.JScrollPane();
                itemsTable = new javax.swing.JTable();
                createPropertiesPanel = new javax.swing.JPanel();
                propertiesLabel = new javax.swing.JLabel();
                createPropertiesBtn = new javax.swing.JButton();
                importPropertiesButton = new javax.swing.JButton();

                reqorderPanel.setLayout(new java.awt.CardLayout());

                mainSplitPane.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
                mainSplitPane.setDividerLocation(200);
                mainSplitPane.setDividerSize(7);
                mainSplitPane.setResizeWeight(0.3);
                mainSplitPane.setToolTipText("");

                databasesTree.setFont(new java.awt.Font("Serif", 0, 12)); // NOI18N
                javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
                databasesTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
                databasesTree.setCellRenderer(new NodeTreeCellRenderer());
                databasesTree.addKeyListener(new java.awt.event.KeyAdapter()
                {
                    public void keyReleased(java.awt.event.KeyEvent evt)
                    {
                        databasesTreeKeyReleased(evt);
                    }
                });
                treeScrollPane.setViewportView(databasesTree);

                mainSplitPane.setLeftComponent(treeScrollPane);

                optionsSplitPane.setDividerLocation(280);
                optionsSplitPane.setDividerSize(7);
                optionsSplitPane.setMinimumSize(new java.awt.Dimension(300, 300));

                mainOptionsPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                mainOptionsPanel.setMinimumSize(new java.awt.Dimension(280, 376));
                mainOptionsPanel.setLayout(new java.awt.CardLayout());

                guideEditorPane.setEditable(false);
                guideEditorPane.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
                guideEditorPane.setMargin(new java.awt.Insets(10, 10, 10, 10));
                try
                {
                    guideEditorPane.setPage(GUI.class.getClassLoader().getResource("Documentation/index.html"));
                } catch (java.io.IOException e1)
                {
                    e1.printStackTrace();
                }
                guideScrollPane.setViewportView(guideEditorPane);

                javax.swing.GroupLayout guidePanelLayout = new javax.swing.GroupLayout(guidePanel);
                guidePanel.setLayout(guidePanelLayout);
                guidePanelLayout.setHorizontalGroup(
                    guidePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(guideScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                );
                guidePanelLayout.setVerticalGroup(
                    guidePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(guideScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 931, Short.MAX_VALUE)
                );

                mainOptionsPanel.add(guidePanel, "guidePanel");

                dbOptionsScrollPane.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));

                dbOptionsPanel.setMinimumSize(new java.awt.Dimension(200, 545));
                dbOptionsPanel.setLayout(new java.awt.GridBagLayout());

                saveDbPrefsButton.setText("Save preferences");
                saveDbPrefsButton.setToolTipText("Save your node and watchlist settings to the properties file");
                saveDbPrefsButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        saveDbPrefsButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 23;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                dbOptionsPanel.add(saveDbPrefsButton, gridBagConstraints);

                deleteDbButton.setText("Delete database");
                deleteDbButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        deleteDbButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 24;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                dbOptionsPanel.add(deleteDbButton, gridBagConstraints);

                blockHeightBox.setSelected(true);
                blockHeightBox.setText("Blockheight (node)");
                blockHeightBox.setActionCommand("myblockheight");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 6;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(blockHeightBox, gridBagConstraints);

                myBlockHeightBox.setSelected(true);
                myBlockHeightBox.setText("Blockheight (chain)");
                myBlockHeightBox.setActionCommand("blockheight");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 7;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(myBlockHeightBox, gridBagConstraints);

                numberOfConnectionsBox.setSelected(true);
                numberOfConnectionsBox.setText("Connected peers");
                numberOfConnectionsBox.setActionCommand("numberOfConnections");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 8;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(numberOfConnectionsBox, gridBagConstraints);

                allKnownPeersBox.setSelected(true);
                allKnownPeersBox.setText("All known peers");
                allKnownPeersBox.setActionCommand("allKnownPeers");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 9;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(allKnownPeersBox, gridBagConstraints);

                uptimeBox.setSelected(true);
                uptimeBox.setText("Uptime");
                uptimeBox.setActionCommand("uptime");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 11;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(uptimeBox, gridBagConstraints);

                ltcPriceBox.setSelected(true);
                ltcPriceBox.setText("Litecoin price");
                ltcPriceBox.setActionCommand("ltcPrice");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 12;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(ltcPriceBox, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 14;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                dbOptionsPanel.add(jSeparator1, gridBagConstraints);

                dataUsageBox.setSelected(true);
                dataUsageBox.setText("Data usage");
                dataUsageBox.setActionCommand("data_usage");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 15;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(dataUsageBox, gridBagConstraints);

                cpu_tempBox.setSelected(true);
                cpu_tempBox.setText("CPU temperature");
                cpu_tempBox.setToolTipText("On Windows systems, Open Hardware Monitor needs to be installed and running on your system in order to fetch CPU temperature data");
                cpu_tempBox.setActionCommand("cpu_temp");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 16;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(cpu_tempBox, gridBagConstraints);

                blockchainSizeBox.setSelected(true);
                blockchainSizeBox.setText("Blockchain size");
                blockchainSizeBox.setActionCommand("blockchainsize");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 17;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(blockchainSizeBox, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 5;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                dbOptionsPanel.add(jSeparator2, gridBagConstraints);

                watchlistsManagerButton.setText("Manage watchlists");
                watchlistsManagerButton.setToolTipText("Add or remove addresses to your watchlist and choose which of their data to ReQord");
                watchlistsManagerButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        watchlistsManagerButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.gridwidth = 2;
                dbOptionsPanel.add(watchlistsManagerButton, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 18;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                dbOptionsPanel.add(jSeparator3, gridBagConstraints);

                minuteSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 59, 1));
                minuteSpinner.setToolTipText("maximum = 59 minutes");
                minuteSpinner.setValue(5);
                minuteSpinner.addChangeListener(new javax.swing.event.ChangeListener()
                {
                    public void stateChanged(javax.swing.event.ChangeEvent evt)
                    {
                        minuteSpinnerStateChanged(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 2;
                gridBagConstraints.gridy = 19;
                gridBagConstraints.ipadx = 14;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
                dbOptionsPanel.add(minuteSpinner, gridBagConstraints);

                hourIntervalLabel.setText("Snapshot interval in hours :");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 20;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                dbOptionsPanel.add(hourIntervalLabel, gridBagConstraints);

                minuteIntervalLabel.setText("Snapshot interval in minutes : ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 19;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                dbOptionsPanel.add(minuteIntervalLabel, gridBagConstraints);

                timeIntervalLabel1.setText("<html><div style='text-align: center;'>Total time interval : 5 minutes<br/>Entries per day: 288,0</div><html>");
                timeIntervalLabel1.setToolTipText("The time interval that will be used between reQorder snapshots");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 21;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
                dbOptionsPanel.add(timeIntervalLabel1, gridBagConstraints);

                hourSpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 24, 1));
                hourSpinner.setToolTipText("maximum = 24 hours");
                hourSpinner.addChangeListener(new javax.swing.event.ChangeListener()
                {
                    public void stateChanged(javax.swing.event.ChangeEvent evt)
                    {
                        hourSpinnerStateChanged(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 2;
                gridBagConstraints.gridy = 20;
                gridBagConstraints.ipadx = 14;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
                dbOptionsPanel.add(hourSpinner, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 22;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                dbOptionsPanel.add(jSeparator4, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 15, 0);
                dbOptionsPanel.add(jSeparator5, gridBagConstraints);

                reqordButton.setText("Start ReQording");
                reqordButton.setToolTipText("Save your preferences and start reQording");
                reqordButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        reqordButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
                dbOptionsPanel.add(reqordButton, gridBagConstraints);

                allOnlineMintersBox.setSelected(true);
                allOnlineMintersBox.setText("All online minters");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 10;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(allOnlineMintersBox, gridBagConstraints);

                sessionTimeLabel.setText("Not ReQording");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
                dbOptionsPanel.add(sessionTimeLabel, gridBagConstraints);

                dogePriceBox.setSelected(true);
                dogePriceBox.setText("Dogecoin price");
                dogePriceBox.setActionCommand("dogeprice");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 13;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 35, 0, 0);
                dbOptionsPanel.add(dogePriceBox, gridBagConstraints);

                encryptDbButton.setText("Encrypt database");
                encryptDbButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        encryptDbButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 25;
                gridBagConstraints.gridwidth = 2;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                dbOptionsPanel.add(encryptDbButton, gridBagConstraints);

                dbOptionsScrollPane.setViewportView(dbOptionsPanel);

                mainOptionsPanel.add(dbOptionsScrollPane, "dbOptionsPanel");

                dbModePanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                dbModePanel.setLayout(new java.awt.GridBagLayout());

                localButton.setText("Local");
                localButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        localButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 3;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                dbModePanel.add(localButton, gridBagConstraints);

                remoteButton.setText("Remote");
                remoteButton.setEnabled(false);
                remoteButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        remoteButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 3;
                gridBagConstraints.gridy = 5;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                dbModePanel.add(remoteButton, gridBagConstraints);

                jLabel5.setText("Coming soon...");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 3;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                dbModePanel.add(jLabel5, gridBagConstraints);

                localLabel.setText("<html><div style='text-align: center;'>Choose 'Local' if you're reqording from the same machine as your node</div><html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.gridwidth = 4;
                gridBagConstraints.ipadx = 150;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
                dbModePanel.add(localLabel, gridBagConstraints);

                localLabel1.setText("<html><div style='text-align: center;'>Choose 'Remote' if you're linking this session to a running ReQorder session on a different machine</div><html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 4;
                gridBagConstraints.gridwidth = 4;
                gridBagConstraints.ipadx = 150;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                dbModePanel.add(localLabel1, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.gridwidth = 4;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.insets = new java.awt.Insets(11, 0, 11, 0);
                dbModePanel.add(jSeparator7, gridBagConstraints);

                mainOptionsPanel.add(dbModePanel, "dbModePanel");

                dbCreatePanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                dbCreatePanel.setLayout(new java.awt.GridBagLayout());

                createDbButton.setText("Create database");
                createDbButton.setFocusable(false);
                createDbButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        createDbButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
                dbCreatePanel.add(createDbButton, gridBagConstraints);

                importDbButton.setText("Import database");
                importDbButton.setFocusable(false);
                importDbButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        importDbButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
                dbCreatePanel.add(importDbButton, gridBagConstraints);

                switchModeButton.setText("Switch mode");
                switchModeButton.setEnabled(false);
                switchModeButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        switchModeButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
                dbCreatePanel.add(switchModeButton, gridBagConstraints);

                mainOptionsPanel.add(dbCreatePanel, "dbCreatePanel");

                tableOptionsPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));

                javax.swing.GroupLayout tableOptionsPanelLayout = new javax.swing.GroupLayout(tableOptionsPanel);
                tableOptionsPanel.setLayout(tableOptionsPanelLayout);
                tableOptionsPanelLayout.setHorizontalGroup(
                    tableOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 260, Short.MAX_VALUE)
                );
                tableOptionsPanelLayout.setVerticalGroup(
                    tableOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 921, Short.MAX_VALUE)
                );

                mainOptionsPanel.add(tableOptionsPanel, "tableOptionsPanel");

                itemsOptionsPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));

                javax.swing.GroupLayout itemsOptionsPanelLayout = new javax.swing.GroupLayout(itemsOptionsPanel);
                itemsOptionsPanel.setLayout(itemsOptionsPanelLayout);
                itemsOptionsPanelLayout.setHorizontalGroup(
                    itemsOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 260, Short.MAX_VALUE)
                );
                itemsOptionsPanelLayout.setVerticalGroup(
                    itemsOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 921, Short.MAX_VALUE)
                );

                mainOptionsPanel.add(itemsOptionsPanel, "itemsOptionsPanel");

                propertiesOptionsPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                propertiesOptionsPanel.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusLost(java.awt.event.FocusEvent evt)
                    {
                        propertiesOptionsPanelFocusLost(evt);
                    }
                });
                propertiesOptionsPanel.setLayout(new java.awt.GridBagLayout());

                setBlockchainFolderButton.setText("Set blockchain folder");
                setBlockchainFolderButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        setBlockchainFolderButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                propertiesOptionsPanel.add(setBlockchainFolderButton, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 21;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 15, 0);
                propertiesOptionsPanel.add(jSeparator11, gridBagConstraints);

                smtpServerTextfield.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                smtpServerTextfield.setText("smtp server");
                smtpServerTextfield.setMinimumSize(new java.awt.Dimension(250, 22));
                smtpServerTextfield.setPreferredSize(new java.awt.Dimension(175, 30));
                smtpServerTextfield.setSelectionStart(0);
                smtpServerTextfield.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusGained(java.awt.event.FocusEvent evt)
                    {
                        smtpServerTextfieldFocusGained(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 10;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(smtpServerTextfield, gridBagConstraints);

                portTextfield.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                portTextfield.setText("port number");
                portTextfield.setMinimumSize(new java.awt.Dimension(250, 22));
                portTextfield.setPreferredSize(new java.awt.Dimension(175, 30));
                portTextfield.setSelectionStart(0);
                portTextfield.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusGained(java.awt.event.FocusEvent evt)
                    {
                        portTextfieldFocusGained(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 12;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(portTextfield, gridBagConstraints);

                passwordField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                passwordField.setText("jPasswordField1");
                passwordField.setMinimumSize(new java.awt.Dimension(250, 22));
                passwordField.setPreferredSize(new java.awt.Dimension(175, 30));
                passwordField.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusGained(java.awt.event.FocusEvent evt)
                    {
                        passwordFieldFocusGained(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 16;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(passwordField, gridBagConstraints);

                usernameTextfield.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                usernameTextfield.setText("username");
                usernameTextfield.setMinimumSize(new java.awt.Dimension(250, 22));
                usernameTextfield.setPreferredSize(new java.awt.Dimension(175, 30));
                usernameTextfield.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusGained(java.awt.event.FocusEvent evt)
                    {
                        usernameTextfieldFocusGained(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 14;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(usernameTextfield, gridBagConstraints);

                saveMailServerButton.setText("Save mail settings");
                saveMailServerButton.setEnabled(false);
                saveMailServerButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        saveMailServerButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 20;
                gridBagConstraints.ipadx = 37;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                propertiesOptionsPanel.add(saveMailServerButton, gridBagConstraints);

                jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
                jLabel1.setText("Setup mail server");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 6;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 9, 0);
                propertiesOptionsPanel.add(jLabel1, gridBagConstraints);

                testMailServerButton.setText("Send test mail");
                testMailServerButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        testMailServerButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 18;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(testMailServerButton, gridBagConstraints);

                recipientTextbox.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                recipientTextbox.setText("recipient e-mail address");
                recipientTextbox.setMinimumSize(new java.awt.Dimension(250, 22));
                recipientTextbox.setPreferredSize(new java.awt.Dimension(175, 30));
                recipientTextbox.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    public void focusGained(java.awt.event.FocusEvent evt)
                    {
                        recipientTextboxFocusGained(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 8;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(recipientTextbox, gridBagConstraints);

                jLabel2.setText("Recipient");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 7;
                propertiesOptionsPanel.add(jLabel2, gridBagConstraints);

                jLabel3.setText("SMTP server (gmail not supported)");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 9;
                propertiesOptionsPanel.add(jLabel3, gridBagConstraints);

                jLabel4.setText("Port");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 11;
                propertiesOptionsPanel.add(jLabel4, gridBagConstraints);

                jLabel6.setText("Username");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 13;
                propertiesOptionsPanel.add(jLabel6, gridBagConstraints);

                jLabel7.setText("Password");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 15;
                propertiesOptionsPanel.add(jLabel7, gridBagConstraints);

                receivedMailCheckbox.setText("I have received the test email");
                receivedMailCheckbox.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        receivedMailCheckboxActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 19;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                propertiesOptionsPanel.add(receivedMailCheckbox, gridBagConstraints);

                retrievePasswordButton.setText("Retrieve password");
                retrievePasswordButton.setToolTipText("<html><div style='text-align: center;'>Click this button to decrypt and retrieve the password stored in your database to the password field.<br/>The password field will automatically clear after 2 minutes.</div><html>\n");
                retrievePasswordButton.setEnabled(false);
                retrievePasswordButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        retrievePasswordButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 17;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                propertiesOptionsPanel.add(retrievePasswordButton, gridBagConstraints);

                changePasswordButton.setText("Change password");
                changePasswordButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        changePasswordButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 7, 0);
                propertiesOptionsPanel.add(changePasswordButton, gridBagConstraints);

                backupAccountCheckbox.setSelected(true);
                backupAccountCheckbox.setText("Auto backup account");
                backupAccountCheckbox.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        backupAccountCheckboxActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 4;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 1, 0);
                propertiesOptionsPanel.add(backupAccountCheckbox, gridBagConstraints);

                buildVersionLabel.setText("ReQorder buildversion");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 5, 0);
                propertiesOptionsPanel.add(buildVersionLabel, gridBagConstraints);

                importAccountButton.setText("Import account");
                importAccountButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        importAccountButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 7, 0);
                propertiesOptionsPanel.add(importAccountButton, gridBagConstraints);

                jLabel9.setText("Custom API IP address");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 22;
                propertiesOptionsPanel.add(jLabel9, gridBagConstraints);

                apiPortInputField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                apiPortInputField.setMinimumSize(new java.awt.Dimension(250, 22));
                apiPortInputField.setPreferredSize(new java.awt.Dimension(175, 30));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 25;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(apiPortInputField, gridBagConstraints);

                jLabel10.setText("Custom API port");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 24;
                propertiesOptionsPanel.add(jLabel10, gridBagConstraints);

                api_IP_inputField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
                api_IP_inputField.setMinimumSize(new java.awt.Dimension(250, 22));
                api_IP_inputField.setPreferredSize(new java.awt.Dimension(175, 30));
                api_IP_inputField.setSelectionStart(0);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 23;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(api_IP_inputField, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 5;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                propertiesOptionsPanel.add(jSeparator12, gridBagConstraints);

                saveSocketButton.setText("Save");
                saveSocketButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        saveSocketButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 26;
                gridBagConstraints.ipadx = 37;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                propertiesOptionsPanel.add(saveSocketButton, gridBagConstraints);

                resetDefaultButton.setText("Reset to default");
                resetDefaultButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        resetDefaultButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 27;
                gridBagConstraints.ipadx = 37;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                propertiesOptionsPanel.add(resetDefaultButton, gridBagConstraints);

                propOptionsScrollpane.setViewportView(propertiesOptionsPanel);

                mainOptionsPanel.add(propOptionsScrollpane, "propertiesOptionsPanel");

                showPropsTablePanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                showPropsTablePanel.setLayout(new java.awt.GridBagLayout());

                deleteTableButton.setText("Delete selected table");
                deleteTableButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        deleteTableButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                showPropsTablePanel.add(deleteTableButton, gridBagConstraints);

                mainOptionsPanel.add(showPropsTablePanel, "showPropsTablePanel");

                watchlistEditor.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
                watchlistEditor.setLayout(new java.awt.GridBagLayout());

                addressesListPane.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED), new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED)));

                addressesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                addressesListPane.setViewportView(addressesList);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 12;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
                watchlistEditor.add(addressesListPane, gridBagConstraints);

                addAddressButton.setText("Add new address");
                addAddressButton.setToolTipText("Add a new address to your watchlist in this database");
                addAddressButton.setMaximumSize(new java.awt.Dimension(125, 25));
                addAddressButton.setMinimumSize(new java.awt.Dimension(125, 25));
                addAddressButton.setPreferredSize(new java.awt.Dimension(125, 25));
                addAddressButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        addAddressButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 8, 0);
                watchlistEditor.add(addAddressButton, gridBagConstraints);

                removeAddressButton.setText("Remove address");
                removeAddressButton.setToolTipText("Remove selected address from your watchlist in this database");
                removeAddressButton.setEnabled(false);
                removeAddressButton.setMaximumSize(new java.awt.Dimension(125, 25));
                removeAddressButton.setMinimumSize(new java.awt.Dimension(125, 25));
                removeAddressButton.setPreferredSize(new java.awt.Dimension(125, 25));
                removeAddressButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        removeAddressButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
                watchlistEditor.add(removeAddressButton, gridBagConstraints);

                saveWatchlistButton.setText("Save watchlist");
                saveWatchlistButton.setMaximumSize(new java.awt.Dimension(125, 25));
                saveWatchlistButton.setMinimumSize(new java.awt.Dimension(125, 25));
                saveWatchlistButton.setPreferredSize(new java.awt.Dimension(125, 25));
                saveWatchlistButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        saveWatchlistButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
                watchlistEditor.add(saveWatchlistButton, gridBagConstraints);

                blocksMintedBox.setSelected(true);
                blocksMintedBox.setText("Blocks minted");
                blocksMintedBox.setActionCommand("blocksminted");
                blocksMintedBox.setEnabled(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 6;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 80, 4, 0);
                watchlistEditor.add(blocksMintedBox, gridBagConstraints);

                balanceBox.setSelected(true);
                balanceBox.setText("Balance");
                balanceBox.setActionCommand("balance");
                balanceBox.setEnabled(false);
                balanceBox.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        balanceBoxActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 8;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 80, 4, 0);
                watchlistEditor.add(balanceBox, gridBagConstraints);

                levelBox.setSelected(true);
                levelBox.setText("Level");
                levelBox.setActionCommand("level");
                levelBox.setEnabled(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 7;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(4, 80, 4, 0);
                watchlistEditor.add(levelBox, gridBagConstraints);

                watchlistLabel.setText("Choose which items to ReQord");
                watchlistLabel.setToolTipText("These items will only be reQorded when their value changes on the next update interval");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 5;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
                watchlistEditor.add(watchlistLabel, gridBagConstraints);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 4;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                watchlistEditor.add(jSeparator6, gridBagConstraints);

                balanceTresholdLabel.setText("Balance update treshold:");
                balanceTresholdLabel.setToolTipText("Balance will only be reqorded if its value has changed by this amount");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 9;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                watchlistEditor.add(balanceTresholdLabel, gridBagConstraints);

                balanceSpinner.setModel(new javax.swing.SpinnerNumberModel(0.1d, 0.001d, null, 0.01d));
                balanceSpinner.setToolTipText("Balance will only be reqorded if its value has changed by this amount");
                balanceSpinner.setEnabled(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 10;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
                watchlistEditor.add(balanceSpinner, gridBagConstraints);

                watchlistEditorBackButton.setText("Back");
                watchlistEditorBackButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        watchlistEditorBackButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.gridwidth = 3;
                gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
                watchlistEditor.add(watchlistEditorBackButton, gridBagConstraints);

                mainOptionsPanel.add(watchlistEditor, "editWatchlistPanel");

                watchlistsManager.setLayout(new java.awt.GridBagLayout());

                newWatchlistButton.setText("New");
                newWatchlistButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                newWatchlistButton.setMaximumSize(new java.awt.Dimension(80, 25));
                newWatchlistButton.setMinimumSize(new java.awt.Dimension(80, 25));
                newWatchlistButton.setPreferredSize(new java.awt.Dimension(80, 25));
                newWatchlistButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        newWatchlistButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(25, 0, 7, 0);
                watchlistsManager.add(newWatchlistButton, gridBagConstraints);

                watchlistsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                jScrollPane2.setViewportView(watchlistsList);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 5;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.ipady = 262;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
                gridBagConstraints.weightx = 0.1;
                gridBagConstraints.weighty = 0.1;
                watchlistsManager.add(jScrollPane2, gridBagConstraints);

                editWatchlistButton.setText("Edit");
                editWatchlistButton.setEnabled(false);
                editWatchlistButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                editWatchlistButton.setMaximumSize(new java.awt.Dimension(80, 25));
                editWatchlistButton.setMinimumSize(new java.awt.Dimension(80, 25));
                editWatchlistButton.setPreferredSize(new java.awt.Dimension(80, 25));
                editWatchlistButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        editWatchlistButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                watchlistsManager.add(editWatchlistButton, gridBagConstraints);

                watchlistsManagerBackButton.setText("Back");
                watchlistsManagerBackButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                watchlistsManagerBackButton.setMaximumSize(new java.awt.Dimension(80, 25));
                watchlistsManagerBackButton.setMinimumSize(new java.awt.Dimension(80, 25));
                watchlistsManagerBackButton.setPreferredSize(new java.awt.Dimension(80, 25));
                watchlistsManagerBackButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        watchlistsManagerBackButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                watchlistsManager.add(watchlistsManagerBackButton, gridBagConstraints);

                deleteWatchlistButton.setText("Delete");
                deleteWatchlistButton.setEnabled(false);
                deleteWatchlistButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                deleteWatchlistButton.setMaximumSize(new java.awt.Dimension(80, 25));
                deleteWatchlistButton.setMinimumSize(new java.awt.Dimension(80, 25));
                deleteWatchlistButton.setPreferredSize(new java.awt.Dimension(80, 25));
                deleteWatchlistButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        deleteWatchlistButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
                watchlistsManager.add(deleteWatchlistButton, gridBagConstraints);

                applyWatchlistButton.setText("Apply to database");
                applyWatchlistButton.setEnabled(false);
                applyWatchlistButton.setMaximumSize(new java.awt.Dimension(125, 25));
                applyWatchlistButton.setMinimumSize(new java.awt.Dimension(125, 25));
                applyWatchlistButton.setPreferredSize(new java.awt.Dimension(125, 25));
                applyWatchlistButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        applyWatchlistButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 4;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(20, 0, 7, 0);
                watchlistsManager.add(applyWatchlistButton, gridBagConstraints);

                mainOptionsPanel.add(watchlistsManager, "watchlistsManager");

                optionsSplitPane.setLeftComponent(mainOptionsPanel);

                itemsTable.setModel(new javax.swing.table.DefaultTableModel(
                    new Object [][]
                    {

                    },
                    new String []
                    {

                    }
                ));
                itemsTableScrollPane.setViewportView(itemsTable);

                optionsSplitPane.setRightComponent(itemsTableScrollPane);

                mainSplitPane.setRightComponent(optionsSplitPane);

                javax.swing.GroupLayout DB_chooserPanelLayout = new javax.swing.GroupLayout(DB_chooserPanel);
                DB_chooserPanel.setLayout(DB_chooserPanelLayout);
                DB_chooserPanelLayout.setHorizontalGroup(
                    DB_chooserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(mainSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 768, Short.MAX_VALUE)
                );
                DB_chooserPanelLayout.setVerticalGroup(
                    DB_chooserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, DB_chooserPanelLayout.createSequentialGroup()
                        .addGap(0, 0, 0)
                        .addComponent(mainSplitPane))
                );

                reqorderPanel.add(DB_chooserPanel, "DB_chooserPanel");

                createPropertiesPanel.setLayout(new java.awt.GridBagLayout());

                propertiesLabel.setText("<html><div style='text-align: center;'>Could not find properties file (properties.mv.db)<br/><br/>This file stores your settings, preferences and watchlists<br/><br/>You can either create a new one or import an existing one</div><html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.gridwidth = 4;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 50, 0);
                createPropertiesPanel.add(propertiesLabel, gridBagConstraints);

                createPropertiesBtn.setText("Create");
                createPropertiesBtn.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        createPropertiesBtnActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                createPropertiesPanel.add(createPropertiesBtn, gridBagConstraints);

                importPropertiesButton.setText("Import");
                importPropertiesButton.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        importPropertiesButtonActionPerformed(evt);
                    }
                });
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 3;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
                createPropertiesPanel.add(importPropertiesButton, gridBagConstraints);

                reqorderPanel.add(createPropertiesPanel, "propertiesPanel");

                javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 768, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(reqorderPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                    layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGap(0, 947, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(reqorderPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
            }// </editor-fold>//GEN-END:initComponents

    private void databasesTreeKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_databasesTreeKeyReleased
    {//GEN-HEADEREND:event_databasesTreeKeyReleased
        TreeSelector(databasesTree.getSelectionPath(), false);
    }//GEN-LAST:event_databasesTreeKeyReleased

    private void saveDbPrefsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveDbPrefsButtonActionPerformed
    {//GEN-HEADEREND:event_saveDbPrefsButtonActionPerformed
        if(GUI.REQORDING)
        {
            JOptionPane.showMessageDialog(gui,Utilities.AllignCenterHTML(
                    "Cannot save preferences while ReQording session is active<br/><br/>"
                            + "Please stop the session before saving"));
            return;
        }
        
        if(savedDbPrefs())
            PopulateDatabasesTree();
    }//GEN-LAST:event_saveDbPrefsButtonActionPerformed

    private void deleteDbButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteDbButtonActionPerformed
    {//GEN-HEADEREND:event_deleteDbButtonActionPerformed
        if(JOptionPane.showConfirmDialog(this,
                "Do you want to delete database '" + selectedDatabase + "'?",
                "Confirm deletion", 
                JOptionPane.YES_NO_OPTION, 
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION)
            return;
        
        try
        {
            File file = new File(System.getProperty("user.dir") + "/databases/" + selectedDatabase + ".mv.db");
            File file2 = new File(System.getProperty("user.dir") + "/databases/" + selectedDatabase + ".trace.db");
            if(file.exists())
                file.delete();
            if(file2.exists())
                file2.delete();
            DatabaseManager.dbFiles.remove(selectedDatabase);
            DatabaseManager.encryptedFiles.remove(selectedDatabase);
            databasesTree.getSelectionPath();
            databasesTreeModel.removeNodeFromParent(selectedNode);
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_deleteDbButtonActionPerformed

    private void watchlistsManagerButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_watchlistsManagerButtonActionPerformed
    {//GEN-HEADEREND:event_watchlistsManagerButtonActionPerformed
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "watchlistsManager");

        editWatchlistButton.setEnabled(false);
        deleteWatchlistButton.setEnabled(false);
        applyWatchlistButton.setEnabled(false);
        watchlistsListModel.clear();
        try
        {
            try (Connection connection = ConnectionDB.getConnection("properties"))
            {
                if (dbManager.TableExists(TEMPWATCHLIST, connection))
                    dbManager.ExecuteUpdate("drop table " + TEMPWATCHLIST, connection);
                
                connection.close();
            }
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        PopulateWatchlistsList();
    }//GEN-LAST:event_watchlistsManagerButtonActionPerformed

    private void minuteSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_minuteSpinnerStateChanged
    {//GEN-HEADEREND:event_minuteSpinnerStateChanged
        long interval = ((int) minuteSpinner.getValue() + ((int) hourSpinner.getValue() * 60)) * 60000;
        if(interval == 0)
        {
            interval = 60000;
            minuteSpinner.setValue(1);
        }
        if(interval > 86400000)
        {
            minuteSpinner.setValue(0);
            interval = 86400000;
        }
        timeIntervalLabel1.setText(Utilities.AllignCenterHTML(
            String.format("Total time interval: %s<br/>Entries per day: %.1f",  Utilities.MillisToDayHrMin(interval),((double) 86400000 / interval))));
    }//GEN-LAST:event_minuteSpinnerStateChanged

    private void hourSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_hourSpinnerStateChanged
    {//GEN-HEADEREND:event_hourSpinnerStateChanged
        long interval = ((int) minuteSpinner.getValue() + ((int) hourSpinner.getValue() * 60)) * 60000;
        if(interval == 0)
        {
            interval = 60000;
            minuteSpinner.setValue(1);
        }
        if(interval > 86400000)
        {
            minuteSpinner.setValue(0);
            interval = 86400000;
        }
        timeIntervalLabel1.setText(Utilities.AllignCenterHTML(
            String.format("Total time interval: %s<br/>Entries per day: %.1f",  Utilities.MillisToDayHrMin(interval),((double) 86400000 / interval))));
    }//GEN-LAST:event_hourSpinnerStateChanged

    private void reqordButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_reqordButtonActionPerformed
    {//GEN-HEADEREND:event_reqordButtonActionPerformed
        if(reqordButton.getText().equals("Stop ReQording"))
        {
            StopReqording();
            return;
        }

        //we save the changes made by user and ask for confirmation before we start REQORDING
        if(savedDbPrefs())
        {
            if(!GUI.REQORDING)
            {
                StartReqording();
            }
            else
            {
                int choice = JOptionPane.showConfirmDialog(
                    this,
                    Utilities.AllignCenterHTML(String.format("A reqording session is already active for database '%s'"
                        + "<br/>Do you want to stop that session and start a new one?", databaseInSession)),
                "Switch database?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if(choice == JOptionPane.YES_OPTION)
            {
                StartReqording();
            }
        }
        }
    }//GEN-LAST:event_reqordButtonActionPerformed

    private void encryptDbButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_encryptDbButtonActionPerformed
    {//GEN-HEADEREND:event_encryptDbButtonActionPerformed
        if(selectedDatabase.equals(reqordingNode))
        {
            JOptionPane.showMessageDialog(this, "Cannot encrypt/decrypt database while ReQording to it");
            return;
        }
        try
        {
            boolean passedCheck = false;
            String dir = System.getProperty("user.dir") + "/databases/";

            //double check, could use just one, but if only one is true, this would mean there's an error in the code somewhere
            //for debug purposes we check both and show a message if we find inconsistencies
            if(DatabaseManager.encryptedFiles.contains(selectedDatabase) && ConnectionDB.IsEncrypted(selectedDatabase))
            {
                //decrypt encrypted database
                try (Connection c = ConnectionDB.getConnection(selectedDatabase))
                {
                    //detect if user renamed properties db and tried to decrypt it
                    if(dbManager.TableExists("mail_server", c))
                    {
                        JOptionPane.showMessageDialog(this,
                            Utilities.AllignCenterHTML("Cannot decrypt a renamed 'properties' database <br/>"
                                + "as it could possibly contain sensitive information.<br/><br/>Decryption aborted."), "Not allowed", JOptionPane.WARNING_MESSAGE);
                        c.close();
                        return;
                    }

                    //don't want to log this due to password ,dont use ExecuteUpdate
                    c.createStatement().execute(String.format("alter user %s set password '%s' ", "reqorder", ""));
                    c.close();
                }

                ChangeFileEncryption.main("-quiet","-cipher", "", "-dir", dir, "-db", selectedDatabase,
                    "-decrypt", String.copyValueOf(DatabaseManager.dbPassword));

                DatabaseManager.encryptedFiles.remove(selectedDatabase);
                encryptDbButton.setText("Encrypt database");

                var node = (DefaultMutableTreeNode) databasesTree.getLastSelectedPathComponent();
                NodeInfo ni = (NodeInfo) node.getUserObject();
                ni.SetIconName("database.png");
                databasesTreeModel.nodeChanged(node);

                passedCheck = true;
            }
            else if(!DatabaseManager.encryptedFiles.contains(selectedDatabase) && !ConnectionDB.IsEncrypted(selectedDatabase))
            {
                //encrypt un-encrypted database
                try (Connection c = ConnectionDB.getConnection(selectedDatabase))
                {
                    //don't want to log this ,dont use ExecuteUpdate
                    c.createStatement().execute(String.format(
                        "alter user %s set password '%s' ", "reqorder", String.copyValueOf(DatabaseManager.dbPassword)));

                c.close();
            }

            ChangeFileEncryption.main("-quiet","-cipher", "AES", "-dir", dir, "-db", selectedDatabase,
                "-encrypt", String.copyValueOf(DatabaseManager.dbPassword));

            DatabaseManager.encryptedFiles.add(selectedDatabase);
            encryptDbButton.setText("Decrypt database");

            var node = (DefaultMutableTreeNode) databasesTree.getLastSelectedPathComponent();
            NodeInfo ni = (NodeInfo) node.getUserObject();
            ni.SetIconName("database_locked.png");
            databasesTreeModel.nodeChanged(node);

            passedCheck = true;
        }

        if(!passedCheck)
        {
            JOptionPane.showMessageDialog(this,
                "Failed: Database is improperly cataloged", "Failed", JOptionPane.WARNING_MESSAGE);
        }
        }
        catch (HeadlessException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_encryptDbButtonActionPerformed

    private void localButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_localButtonActionPerformed
    {//GEN-HEADEREND:event_localButtonActionPerformed
        modeType = 1;
        switchModeButton.setText("Switch to remote mode");
    }//GEN-LAST:event_localButtonActionPerformed

    private void remoteButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_remoteButtonActionPerformed
    {//GEN-HEADEREND:event_remoteButtonActionPerformed
        modeType = 2;
        switchModeButton.setText("Switch to local mode");
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "dbCreatePanel");
        NodeInfo ni = (NodeInfo) databasesNode.getUserObject();
        ni.nodeName = "Databases (remote mode)";
        var dmt = (DefaultTreeModel) databasesTree.getModel();
        dmt.reload(databasesNode);
        //populate databasesTree from remote properties file
    }//GEN-LAST:event_remoteButtonActionPerformed

    private void createDbButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_createDbButtonActionPerformed
    {//GEN-HEADEREND:event_createDbButtonActionPerformed
        String dbName = JOptionPane.showInputDialog("Enter new database name:");
        if (dbName == null)
            return;
        
        if (dbName.equals(""))
        {
            JOptionPane.showMessageDialog(null, "The database name should have at least one character");
            return;
        }
        if(dbName.contains("."))
        {
            JOptionPane.showMessageDialog(null, "The database name should not contain any periods'");
            return;
        }
        if (dbName.equals("properties"))
        {
            JOptionPane.showMessageDialog(null, "The database cannot be named 'properties'");
            return;
        }
        File checkFile = new File(System.getProperty("user.dir") + "/databases/" + dbName + ".mv.db");
        if (checkFile.exists())
        {
            JOptionPane.showMessageDialog(null, "The database '" + dbName + "' already exists");
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                Utilities.AllignCenterHTML(
                        "Do you want to encrypt this database?<br/><br/>"
                        + "Encrypting a database will restrict unauthorised users or accounts from accessing it.<br/>"
                        + "An encrypted database will keep your data safer, but will be bit slower to interact with.<br/><br/>"
                        + "You can unlock the database later if you wish to share it with another ReQorder account."),
                "Please choose", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
        {
            ConnectionDB.CreateDatabase(dbName, DatabaseManager.dbPassword, true);
        }
        else
        {
            ConnectionDB.CreateDatabase(dbName, ("").toCharArray(), false);
        }

        dbManager.FindDbFiles();
        dbManager.InsertDbFiles();
        PopulateDatabasesTree();
    }//GEN-LAST:event_createDbButtonActionPerformed

    private void importDbButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_importDbButtonActionPerformed
    {//GEN-HEADEREND:event_importDbButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.home"));
        FileNameExtensionFilter filter = new FileNameExtensionFilter("database files (*.mv.db)", "db");
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);//only allow *.db files
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = jfc.getSelectedFile();
            boolean fileExists = false;
            
            if(selectedFile.getName().equals("properties.mv.db"))
            {
                JOptionPane.showMessageDialog(this, 
                        "Importing properties file is not allowed.\nUse 'Import account' to change your properties file", 
                        "Illegal operation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (selectedFile.getName().endsWith("mv.db"))
            {
                File checkFile = new File(System.getProperty("user.dir") + "/databases/" + selectedFile.getName());
                if(checkFile.exists())
                {
                    fileExists = true;
                    int choice = JOptionPane.showConfirmDialog(
                            this, 
                            Utilities.AllignCenterHTML(String.format(
                                    "Database '%s' already exists<br/><br/>Do you want to overwrite it?", selectedFile.getName().split("\\.",2)[0])),
                            "Delete old file?",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    
                    if(choice == JOptionPane.YES_OPTION)
                    {
                        checkFile.delete();
                    }
                    else
                        return;
                }
                
                int choice = JOptionPane.showConfirmDialog(this, "Do you want to delete the source file?\n'" + selectedFile.getAbsolutePath() + "'",
                        "Copy or move?", JOptionPane.YES_NO_OPTION);

                File newFile = new File(System.getProperty("user.dir") + "/databases/" + selectedFile.getName());

                if (choice == JOptionPane.NO_OPTION)
                {
                    Utilities.CopyFile(selectedFile, newFile);
                }
                else
                {
                    if (choice == JOptionPane.YES_OPTION && selectedFile.renameTo(newFile))
                    {
                        selectedFile.delete();
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(null, "Failed to import file '" + selectedFile.getName() + "'");
                        return;
                    }
                }

                //only add new db to dbFiles if a db with that name did not already exist
                if(!fileExists)
                {
                    String dbName = selectedFile.getName().split("\\.",2)[0];
                    if(ConnectionDB.IsEncrypted(dbName))
                        DatabaseManager.encryptedFiles.add(dbName);
                    DatabaseManager.dbFiles.add(dbName);
                    PopulateDatabasesTree();                    
                }                
            }
            else
            {
                JOptionPane.showMessageDialog(null, "Invalid file: file must end with 'mv.db'");
            }
        }
        else
        {
            BackgroundService.AppendLog("Could not open file chooser @ importDbButtonAP");
        }
    }//GEN-LAST:event_importDbButtonActionPerformed

    private void switchModeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_switchModeButtonActionPerformed
    {//GEN-HEADEREND:event_switchModeButtonActionPerformed
        switch (modeType)
        {
            case 1:
                modeType = 2;
                switchModeButton.setText("Switch to local mode");
                NodeInfo ni = (NodeInfo) databasesNode.getUserObject();
                ni.nodeName = "Databases (remote mode)";
                var dmt = (DefaultTreeModel) databasesTree.getModel();
                dmt.reload(databasesNode);
                break;
            case 2:
                modeType = 1;
                switchModeButton.setText("Switch to remote mode");
                ni = (NodeInfo) databasesNode.getUserObject();
                ni.nodeName = "Databases (local mode)";
                dmt = (DefaultTreeModel) databasesTree.getModel();
                dmt.reload(databasesNode);
                //ADD REMOTE CODE IMPLEMENTATION
                break;
        }
    }//GEN-LAST:event_switchModeButtonActionPerformed

    private void setBlockchainFolderButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_setBlockchainFolderButtonActionPerformed
    {//GEN-HEADEREND:event_setBlockchainFolderButtonActionPerformed
        dbManager.SetBlockchainFolder();
    }//GEN-LAST:event_setBlockchainFolderButtonActionPerformed

    private void smtpServerTextfieldFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_smtpServerTextfieldFocusGained
    {//GEN-HEADEREND:event_smtpServerTextfieldFocusGained
        smtpServerTextfield.selectAll();
    }//GEN-LAST:event_smtpServerTextfieldFocusGained

    private void portTextfieldFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_portTextfieldFocusGained
    {//GEN-HEADEREND:event_portTextfieldFocusGained
        portTextfield.selectAll();
    }//GEN-LAST:event_portTextfieldFocusGained

    private void passwordFieldFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_passwordFieldFocusGained
    {//GEN-HEADEREND:event_passwordFieldFocusGained
        passwordField.selectAll();
    }//GEN-LAST:event_passwordFieldFocusGained

    private void usernameTextfieldFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_usernameTextfieldFocusGained
    {//GEN-HEADEREND:event_usernameTextfieldFocusGained
        usernameTextfield.selectAll();
    }//GEN-LAST:event_usernameTextfieldFocusGained

    private void saveMailServerButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveMailServerButtonActionPerformed
    {//GEN-HEADEREND:event_saveMailServerButtonActionPerformed
        int choice = JOptionPane.showConfirmDialog(this,
                Utilities.AllignCenterHTML("DISCLAIMER:<br/><br/>"
                        + "The log-in credentials for your e-mail account will be stored in a locked and encrypted database.<br/>"
                        + "Your password will also be encrypted before it is stored.<br/><br/>"
                        + "Saving this information is at your own risk, the developer will  not be held liable for any damages<br/>"
                        + "resulting from the use of this feature. By clicking 'OK' you acknowledge agreeing to these conditions.<br/><br/>"
                        + " Read more at 'www.reqorder.org/privacy' or in the documentation"),
                "DISCLAIMER", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.CANCEL_OPTION)
            return;
        

        //must check after choice in case field has emptied before user choice due to passwordfield text timer
        if (passwordField.getPassword().length == 0)
        {
            JOptionPane.showMessageDialog(this,
                    Utilities.AllignCenterHTML("The password field is empty<br/>"
                            + "Please enter your password or retrieve the previously saved one"),
                    "Re-enter or retrieve password", JOptionPane.PLAIN_MESSAGE);
            return;
        }

        String[] keys = Utilities.EncryptPassword(passwordField.getPassword(), "", "");
        if (keys != null)
        {
            dbManager.SaveCredentials(usernameTextfield.getText(),
                    keys[0],
                    smtpServerTextfield.getText(), portTextfield.getText(),
                    recipientTextbox.getText(), keys[1], keys[2]);
        }
        else
            JOptionPane.showMessageDialog(this, "Error encrypting password", "Encryption error", JOptionPane.ERROR_MESSAGE);
    }//GEN-LAST:event_saveMailServerButtonActionPerformed

    private void testMailServerButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_testMailServerButtonActionPerformed
    {//GEN-HEADEREND:event_testMailServerButtonActionPerformed
        testMailServerButton.setEnabled(false);
        Timer buttonTimer = new Timer();
        TimerTask task = new TimerTask()
        {
            @Override
            public void run()
            {
                testMailServerButton.setEnabled(true);
            }
        };
        buttonTimer.schedule(task, 8000);

        if (Utilities.SendEmail(
                recipientTextbox.getText(),
                usernameTextfield.getText(),
                String.copyValueOf(passwordField.getPassword()),
                smtpServerTextfield.getText(),
                portTextfield.getText(),
                "ReQorder test mail",
                "Congratulations, your ReQorder mail alert settings are working properly."))
        {
            int choice = JOptionPane.showConfirmDialog(this,
                    Utilities.AllignCenterHTML("Mail sent sucessfully<br/>Do you want to save these settings?<br/><br/>Check your inbox or spam folder first"),
                    "E-mail sent", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice == JOptionPane.YES_OPTION)
            {
                saveMailServerButtonActionPerformed(null);
            }
        }
        else
        {
            JOptionPane.showMessageDialog(this,
                    Utilities.AllignCenterHTML("Failed to send e-mail<br/>Are your server settings correct?"),
                    "Failed to send e-mail", JOptionPane.PLAIN_MESSAGE);
        }
    }//GEN-LAST:event_testMailServerButtonActionPerformed

    private void recipientTextboxFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_recipientTextboxFocusGained
    {//GEN-HEADEREND:event_recipientTextboxFocusGained
        recipientTextbox.selectAll();
    }//GEN-LAST:event_recipientTextboxFocusGained

    private void receivedMailCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_receivedMailCheckboxActionPerformed
    {//GEN-HEADEREND:event_receivedMailCheckboxActionPerformed
        saveMailServerButton.setEnabled(receivedMailCheckbox.isSelected());
    }//GEN-LAST:event_receivedMailCheckboxActionPerformed

    private void retrievePasswordButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_retrievePasswordButtonActionPerformed
    {//GEN-HEADEREND:event_retrievePasswordButtonActionPerformed
        //We want to avoid keeping the decrypted password inside the field (and in the heap)for too long,
        //but don't want the user to have to re-enter the password every time. This functionality seems like a good middle ground
        try(Connection connection = ConnectionDB.getConnection( "properties"))
        {
            char[] pw = Utilities.DecryptPassword(
                (String) dbManager.GetItemValue("mail_server", "password", "id", "0", connection),
                (String) dbManager.GetItemValue("mail_server", "key", "id", "0", connection),
                (String) dbManager.GetItemValue("mail_server", "salt", "id", "0", connection));

            if (pw != null)
            {
                passwordField.setText(String.copyValueOf(pw));
            }
            else
            {
                JOptionPane.showMessageDialog(this, "Error decrypting password", "Decryption error", JOptionPane.ERROR_MESSAGE);
                passwordField.setText("");
            }
            retrievePasswordButton.setEnabled(false);

            Timer buttonTimer = new Timer();
            TimerTask buttonTask = new TimerTask()
            {
                @Override
                public void run()
                {
                    passwordField.setText("");
                    retrievePasswordButton.setEnabled(true);
                }
            };
            buttonTimer.schedule(buttonTask, 120000);
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_retrievePasswordButtonActionPerformed

    private void changePasswordButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_changePasswordButtonActionPerformed
    {//GEN-HEADEREND:event_changePasswordButtonActionPerformed
       gui.ShowChangePasswordPanel();
    }//GEN-LAST:event_changePasswordButtonActionPerformed

    private void backupAccountCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_backupAccountCheckboxActionPerformed
    {//GEN-HEADEREND:event_backupAccountCheckboxActionPerformed
        dbManager.backupEnabled = backupAccountCheckbox.isSelected();
    }//GEN-LAST:event_backupAccountCheckboxActionPerformed

    private void propertiesOptionsPanelFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_propertiesOptionsPanelFocusLost
    {//GEN-HEADEREND:event_propertiesOptionsPanelFocusLost
        passwordField.setText("");
    }//GEN-LAST:event_propertiesOptionsPanelFocusLost

    private void deleteTableButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteTableButtonActionPerformed
    {//GEN-HEADEREND:event_deleteTableButtonActionPerformed
        String table = databasesTree.getSelectionPath().getLastPathComponent().toString();
        if (JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the table '" + table + "' from the properties database?",
                "Confirm table deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
        {
            try (Connection connection = ConnectionDB.getConnection("properties"))
            {
                dbManager.ExecuteUpdate("drop table " + table, connection);
                connection.close();
                PopulateDatabasesTree();
            }
            catch (NullPointerException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }//GEN-LAST:event_deleteTableButtonActionPerformed

    private void addAddressButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addAddressButtonActionPerformed
    {//GEN-HEADEREND:event_addAddressButtonActionPerformed
        String address = JOptionPane.showInputDialog(this, "Please enter the address to add to your watchlist", "Address input", JOptionPane.QUESTION_MESSAGE);

        //when user clicks cancel
        if (address == null)
            return;

        if (dbManager.AddAddress(editedWatchlist, address))
        {
            addressListModel.addElement(address);
            saveWatchlistButton.setEnabled(true);
        }
    }//GEN-LAST:event_addAddressButtonActionPerformed

    private void removeAddressButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_removeAddressButtonActionPerformed
    {//GEN-HEADEREND:event_removeAddressButtonActionPerformed
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {
            String address = addressesList.getSelectedValue();
            int ID = (int) dbManager.GetItemValue(editedWatchlist, "ID", "address", Utilities.SingleQuotedString(address), connection);
            dbManager.ExecuteUpdate("delete from " + editedWatchlist +  " where ID = " + ID, connection);
            connection.close();
            addressListModel.removeElement(address);
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_removeAddressButtonActionPerformed

    private void saveWatchlistButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveWatchlistButtonActionPerformed
    {//GEN-HEADEREND:event_saveWatchlistButtonActionPerformed
        lastSelectedAddress = addressesList.getSelectedValue();
        SaveAddressChanges();
        
        //if creating new watchlist rename it, otherwise the changes were already saved
        if(editedWatchlist.equals(TEMPWATCHLIST) && !addressListModel.isEmpty())
        {
            String name = JOptionPane.showInputDialog(this, "Please enter a name for your watchlist", "Name input", JOptionPane.QUESTION_MESSAGE);
            if(name == null || name.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "You need to enter a name");
                return;
            }
            name = "WL_" + name;

            try (Connection connection = ConnectionDB.getConnection( "properties"))
            {
                if(dbManager.TableExists(name,connection))
                {
                    JOptionPane.showMessageDialog(this, "Watchlist with name '" + name + "' already exists");
                    return;
                }
                name = name.replace(" ", "_");
                dbManager.ExecuteUpdate("alter table " + TEMPWATCHLIST + " rename to " + name,connection);
                editedWatchlist = name;

                connection.close();
            }
            catch (NullPointerException | HeadlessException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "watchlistsManager");
        PopulateWatchlistTable();//call before populating wl list (otherwise editedWatchlist will be null)
        PopulateWatchlistsList();
        PopulateDatabasesTree();
    }//GEN-LAST:event_saveWatchlistButtonActionPerformed

    private void balanceBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_balanceBoxActionPerformed
    {//GEN-HEADEREND:event_balanceBoxActionPerformed
        balanceSpinner.setEnabled(balanceBox.isSelected());
    }//GEN-LAST:event_balanceBoxActionPerformed

    private void watchlistEditorBackButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_watchlistEditorBackButtonActionPerformed
    {//GEN-HEADEREND:event_watchlistEditorBackButtonActionPerformed
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "watchlistsManager");
        PopulateWatchlistTable();
        //if only one item present, jlist will not respond to selection of that item
        watchlistsList.clearSelection();
    }//GEN-LAST:event_watchlistEditorBackButtonActionPerformed

    private void newWatchlistButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_newWatchlistButtonActionPerformed
    {//GEN-HEADEREND:event_newWatchlistButtonActionPerformed
        editedWatchlist = TEMPWATCHLIST;

        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "editWatchlistPanel");
        saveWatchlistButton.setEnabled(false);
        RefreshAddressComponents(false);
        AddressComponentsToDefault();
        addressListModel.clear(); //easier than checking if contains for every address

        //if user exited watchlists editor without saving, it's possible that the wl_temp table already exists
        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            //if user exited watchlists editor without saving, it's possible that the wl_temp table already exists
            if (dbManager.TableExists(TEMPWATCHLIST, connection))
                dbManager.ExecuteUpdate("drop table " + TEMPWATCHLIST, connection);            

            //create a temporary wl table where we'll insert the addresses, we'll rename or delete this table depending on user action
            dbManager.CreateTable(new String[]
            {
                TEMPWATCHLIST, "id", "int", "address", "varchar(50)", "name", "varchar(256)", "blocksminted", "boolean",
                "level", "boolean", "balance", "boolean", "balancetreshold", "double"
            }, connection);
            connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_newWatchlistButtonActionPerformed

    private void editWatchlistButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_editWatchlistButtonActionPerformed
    {//GEN-HEADEREND:event_editWatchlistButtonActionPerformed
        //SaveAddressChanges gets triggered when user selects an address from existing list, saving the address
        //with the default settings that are set in this method. Thus we need to set it to null, so SAChanges() will skip saving
        lastSelectedAddress = null;
        editedWatchlist = "WL_" + watchlistsList.getSelectedValue();
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "editWatchlistPanel");
        saveWatchlistButton.setEnabled(true);
        RefreshAddressComponents(false);
        AddressComponentsToDefault();
        addressListModel.clear(); //easier than checking if contains for every address

        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            dbManager.GetColumn(editedWatchlist, "address", "ID", "asc", connection).forEach(address ->
            {
                addressListModel.addElement(address.toString());
            });
            connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_editWatchlistButtonActionPerformed

    private void watchlistsManagerBackButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_watchlistsManagerBackButtonActionPerformed
    {//GEN-HEADEREND:event_watchlistsManagerBackButtonActionPerformed
        CardLayout card = (CardLayout) mainOptionsPanel.getLayout();
        card.show(mainOptionsPanel, "dbOptionsPanel");
        //ATTENTION: IF NODEPREFS REFRESH NOT WORKING, ADD METHOD TO WLMBACKBUTTON
    }//GEN-LAST:event_watchlistsManagerBackButtonActionPerformed

    private void deleteWatchlistButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteWatchlistButtonActionPerformed
    {//GEN-HEADEREND:event_deleteWatchlistButtonActionPerformed
        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            String watchlist = watchlistsList.getSelectedValue();
            if (watchlist != null)
            {
                int choice = JOptionPane.showConfirmDialog(this, "Delete watchlist '" + watchlist + "?", "Delete watchlist", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION)
                {
                    dbManager.ExecuteUpdate("drop table WL_" + watchlistsList.getSelectedValue(), connection);
                    watchlistsListModel.removeElement(watchlist);
                    PopulateDatabasesTree();
                }
            }

            connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_deleteWatchlistButtonActionPerformed

    private void applyWatchlistButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyWatchlistButtonActionPerformed
    {//GEN-HEADEREND:event_applyWatchlistButtonActionPerformed
         if(selectedNode.getUserObject().toString().equals(reqordingNode))
        {
            JOptionPane.showMessageDialog(this, 
                    Utilities.AllignCenterHTML(String.format("Database '%s' is currently ReQording"
                            + "<br/>Stop the ReQording session to apply a new watchlist", selectedDatabase))
                    , "ReQording in session", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        try (Connection connection = ConnectionDB.getConnection( selectedDatabase))
        {      
            if(dbManager.TableExists("my_watchlist", connection))
            {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        Utilities.AllignCenterHTML(String.format("Database '%s' already has a watchlist<br/>"
                                + "If you choose 'OK', all the stored data in this database related to that watchlist will be deleted<br/>"
                                + "Choose 'Cancel' and create a new database if you wish to keep the stored data", selectedDatabase)),
                        "Please confirm",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (choice == JOptionPane.CANCEL_OPTION)
                    return;
                else
                {
                    dbManager.ExecuteUpdate("delete from my_watchlist", connection);
                    dbManager.GetTables(connection).stream().filter(table -> (table.startsWith("WL_"))).forEachOrdered(table ->
                    {
                        dbManager.ExecuteUpdate("drop table " + table, connection);
                    });
                }
            }
            else
                dbManager.CreateTable(new String[]{"my_watchlist","id","int",
                    "name","varchar(255)","address","varchar(255)","balancetreshold","double","alert","boolean","alertvalue","double"},connection);   
                    

            //finds which tables to create from applied watchlist table in properties, creates them and applies addresses to my_watchlist
            //once the watchlist is applied, it is independent from the watchlist saved in properties, this to avoid errors when user changes
            //the watchlist. We don't want to remove all the address tables in that case, and we don't want my_watchlist to be dependent on
            //the properties watchlist for ID and address,so the database watchlist and properties watchlist need to  be decoupled.
            dbManager.CreateAddressTables(selectedDatabase, "WL_" + watchlistsList.getSelectedValue());  
            
            JOptionPane.showMessageDialog(applyWatchlistButton, "Watchlist applied to '" + selectedDatabase + "'");
            
            connection.close();
        }
        catch (NullPointerException | HeadlessException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        
        PopulateDatabasesTree();   
    }//GEN-LAST:event_applyWatchlistButtonActionPerformed

    private void createPropertiesBtnActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_createPropertiesBtnActionPerformed
    {//GEN-HEADEREND:event_createPropertiesBtnActionPerformed
        CardLayout card = (CardLayout) reqorderPanel.getLayout();
        card.show(reqorderPanel, "DB_chooserPanel");

        //make sure the created db will be encrypted and added to databasesTree
        //with regular db's we run the FindDbFiles and InsertDbFiles, here we need to
        //manually insert it, cause we connect with the db immediately after creation
        DatabaseManager.encryptedFiles.add("properties");
        DatabaseManager.dbFiles.add("properties");

        ConnectionDB.CreateDatabase("properties",DatabaseManager.dbPassword,true);
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {
            dbManager.InsertDbFiles();
            card = (CardLayout) mainOptionsPanel.getLayout();
            card.show(mainOptionsPanel, "dbCreatePanel");
            NodeInfo ni = (NodeInfo) databasesNode.getUserObject();
            ni.nodeName = "Databases (local mode)";
            var dmt = (DefaultTreeModel) databasesTree.getModel();
            dmt.reload(databasesNode);

            connection.close();
            gui.LoginComplete();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_createPropertiesBtnActionPerformed

    private void importPropertiesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_importPropertiesButtonActionPerformed
    {//GEN-HEADEREND:event_importPropertiesButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/databases");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("database files (*.mv.db)", "db");
        //        jfc.setSelectedFile(new File("properties.mv.db")); //show preferred filename in filechooser
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);//only allow *.db files
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = jfc.getSelectedFile();

            if (selectedFile.getName().equals("properties.mv.db"))
            {
                int choice = JOptionPane.showConfirmDialog(this, "Do you want to delete the source file '" + selectedFile.getAbsolutePath() + "'",
                        "Copy or move?", JOptionPane.YES_NO_CANCEL_OPTION);

                if (choice == JOptionPane.CANCEL_OPTION)
                    return;

                File newFile = new File(System.getProperty("user.dir") + "/databases/properties.mv.db");

                if (choice == JOptionPane.NO_OPTION)
                {
                    Utilities.CopyFile(selectedFile, newFile);
                }
                else
                {
                    if (choice == JOptionPane.YES_OPTION && selectedFile.renameTo(newFile))
                    {
                        selectedFile.delete();
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(null, "Failed to import file '" + selectedFile.getName() + "'");
                        return;
                    }
                }
                
                //must be added before CanConnect check
                DatabaseManager.encryptedFiles.add("properties");
                DatabaseManager.dbFiles.add("properties");
                
                //We have to copy the db to db folder first before checking its validity, due to CanConnect() creating a new
                //properties database if there is none present
                if (!ConnectionDB.CanConnect("properties", DatabaseManager.dbPassword))
                {
                    JOptionPane.showMessageDialog(this,
                            Utilities.AllignCenterHTML(
                                    "Could not access properties database<br/>"
                                    + "The file either belongs to a different<br/>"
                                    + "account, or it is corrupted"), "Cannot access database", JOptionPane.WARNING_MESSAGE);
                    //don't delete the new file if user opted to delete the old file but new file is invalid
                    if(choice != JOptionPane.YES_OPTION)
                        newFile.delete();
                    DatabaseManager.encryptedFiles.remove("properties");
                    DatabaseManager.dbFiles.remove("properties");
                    gui.ShowCreatePropertiesPanel();
                    return;
                }

                CardLayout card = (CardLayout) reqorderPanel.getLayout();
                card.show(reqorderPanel, "DB_chooserPanel");
                gui.LoginComplete();
            }
            else
            {
                JOptionPane.showMessageDialog(null, "Invalid file: file must be named 'properties.mv.db'");
            }
        }
        else
            BackgroundService.AppendLog("Could not open file chooser @ importButtonActionPerformed");
    }//GEN-LAST:event_importPropertiesButtonActionPerformed

    private void importAccountButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_importAccountButtonActionPerformed
    {//GEN-HEADEREND:event_importAccountButtonActionPerformed
        if(gui.dbManager.RestoreAccount())
        {
            gui.dbManager.FindDbFiles();
            gui.Login();            
        }
    }//GEN-LAST:event_importAccountButtonActionPerformed

    private void saveSocketButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveSocketButtonActionPerformed
    {//GEN-HEADEREND:event_saveSocketButtonActionPerformed
        if(api_IP_inputField.getText().isBlank() || apiPortInputField.getText().isBlank())
        {
            JOptionPane.showMessageDialog(this, "Please enter an IP address and port");
            return;
        }
        
        if(JOptionPane.showConfirmDialog(this,
                "This is an advanced feature, are you sure you want to continue?","Warning",
                JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
            return;
        
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {
            if(!dbManager.TableExists("socket", connection))
                dbManager.CreateTable(new String[]{"socket","ip","varchar(50)","port","varchar(10)"}, connection);
            
            dbManager.ExecuteUpdate("delete from socket", connection);
            
            dbManager.InsertIntoDB(new String[]{"socket",
                "ip", Utilities.SingleQuotedString(api_IP_inputField.getText()),
                "port", Utilities.SingleQuotedString(apiPortInputField.getText())}, connection);
            
            dbManager.customIP = api_IP_inputField.getText();
            dbManager.customPort = apiPortInputField.getText();
            dbManager.socket = dbManager.customIP + ":" + dbManager.customPort;
            
            JOptionPane.showMessageDialog(this, "Settings saved");
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_saveSocketButtonActionPerformed

    private void resetDefaultButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_resetDefaultButtonActionPerformed
    {//GEN-HEADEREND:event_resetDefaultButtonActionPerformed
        try (Connection connection = ConnectionDB.getConnection( "properties"))
        {            
            if(!dbManager.TableExists("socket", connection))
                dbManager.CreateTable(new String[]{"socket","ip","varchar(50)","port","varchar(10)"}, connection);
            
            dbManager.ExecuteUpdate("delete from socket", connection);
            
            api_IP_inputField.setText("localhost");
            apiPortInputField.setText("12391");
            
            dbManager.InsertIntoDB(new String[]{"socket",
                "ip", Utilities.SingleQuotedString(api_IP_inputField.getText()),
                "port", Utilities.SingleQuotedString(apiPortInputField.getText())}, connection);            
            
            dbManager.customIP = api_IP_inputField.getText();
            dbManager.customPort = apiPortInputField.getText();
            dbManager.socket = dbManager.customIP + ":" + dbManager.customPort;
            
            JOptionPane.showMessageDialog(this, "IP and port settings reset to default");
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_resetDefaultButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel DB_chooserPanel;
    private javax.swing.JButton addAddressButton;
    private javax.swing.JList<String> addressesList;
    private javax.swing.JScrollPane addressesListPane;
    private javax.swing.JCheckBox allKnownPeersBox;
    private javax.swing.JCheckBox allOnlineMintersBox;
    private javax.swing.JTextField apiPortInputField;
    private javax.swing.JTextField api_IP_inputField;
    private javax.swing.JButton applyWatchlistButton;
    private javax.swing.JCheckBox backupAccountCheckbox;
    private javax.swing.JCheckBox balanceBox;
    private javax.swing.JSpinner balanceSpinner;
    private javax.swing.JLabel balanceTresholdLabel;
    private javax.swing.JCheckBox blockHeightBox;
    private javax.swing.JCheckBox blockchainSizeBox;
    private javax.swing.JCheckBox blocksMintedBox;
    private javax.swing.JLabel buildVersionLabel;
    private javax.swing.JButton changePasswordButton;
    private javax.swing.JCheckBox cpu_tempBox;
    private javax.swing.JButton createDbButton;
    private javax.swing.JButton createPropertiesBtn;
    private javax.swing.JPanel createPropertiesPanel;
    private javax.swing.JCheckBox dataUsageBox;
    private javax.swing.JTree databasesTree;
    private javax.swing.JPanel dbCreatePanel;
    private javax.swing.JPanel dbModePanel;
    private javax.swing.JPanel dbOptionsPanel;
    private javax.swing.JScrollPane dbOptionsScrollPane;
    private javax.swing.JButton deleteDbButton;
    private javax.swing.JButton deleteTableButton;
    private javax.swing.JButton deleteWatchlistButton;
    private javax.swing.JCheckBox dogePriceBox;
    private javax.swing.JButton editWatchlistButton;
    private javax.swing.JButton encryptDbButton;
    private javax.swing.JEditorPane guideEditorPane;
    private javax.swing.JPanel guidePanel;
    private javax.swing.JScrollPane guideScrollPane;
    private javax.swing.JLabel hourIntervalLabel;
    private javax.swing.JSpinner hourSpinner;
    private javax.swing.JButton importAccountButton;
    private javax.swing.JButton importDbButton;
    private javax.swing.JButton importPropertiesButton;
    private javax.swing.JPanel itemsOptionsPanel;
    private javax.swing.JTable itemsTable;
    private javax.swing.JScrollPane itemsTableScrollPane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JCheckBox levelBox;
    private javax.swing.JButton localButton;
    private javax.swing.JLabel localLabel;
    private javax.swing.JLabel localLabel1;
    private javax.swing.JCheckBox ltcPriceBox;
    private javax.swing.JPanel mainOptionsPanel;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JLabel minuteIntervalLabel;
    private javax.swing.JSpinner minuteSpinner;
    private javax.swing.JCheckBox myBlockHeightBox;
    private javax.swing.JButton newWatchlistButton;
    private javax.swing.JCheckBox numberOfConnectionsBox;
    private javax.swing.JSplitPane optionsSplitPane;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JTextField portTextfield;
    private javax.swing.JScrollPane propOptionsScrollpane;
    private javax.swing.JLabel propertiesLabel;
    private javax.swing.JPanel propertiesOptionsPanel;
    private javax.swing.JCheckBox receivedMailCheckbox;
    private javax.swing.JTextField recipientTextbox;
    private javax.swing.JButton remoteButton;
    private javax.swing.JButton removeAddressButton;
    private javax.swing.JButton reqordButton;
    private javax.swing.JPanel reqorderPanel;
    private javax.swing.JButton resetDefaultButton;
    private javax.swing.JButton retrievePasswordButton;
    private javax.swing.JButton saveDbPrefsButton;
    private javax.swing.JButton saveMailServerButton;
    private javax.swing.JButton saveSocketButton;
    private javax.swing.JButton saveWatchlistButton;
    private javax.swing.JLabel sessionTimeLabel;
    private javax.swing.JButton setBlockchainFolderButton;
    private javax.swing.JPanel showPropsTablePanel;
    private javax.swing.JTextField smtpServerTextfield;
    private javax.swing.JButton switchModeButton;
    private javax.swing.JPanel tableOptionsPanel;
    private javax.swing.JButton testMailServerButton;
    private javax.swing.JLabel timeIntervalLabel1;
    private javax.swing.JScrollPane treeScrollPane;
    private javax.swing.JCheckBox uptimeBox;
    private javax.swing.JTextField usernameTextfield;
    private javax.swing.JPanel watchlistEditor;
    private javax.swing.JButton watchlistEditorBackButton;
    private javax.swing.JLabel watchlistLabel;
    private javax.swing.JList<String> watchlistsList;
    private javax.swing.JPanel watchlistsManager;
    private javax.swing.JButton watchlistsManagerBackButton;
    private javax.swing.JButton watchlistsManagerButton;
    // End of variables declaration//GEN-END:variables
}
