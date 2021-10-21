package reqorder;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class ChartsPanel extends javax.swing.JPanel
{
    private GUI gui;
    private JPanel chartPanel;
    protected ChartMaker chartMaker;
    private final DefaultTreeModel chartsTreeModel;

    public ChartsPanel()
    {
        initComponents();      
        chartsTreeModel = (DefaultTreeModel) chartsTree.getModel();
        MouseListener ml = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                int selRow = chartsTree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = chartsTree.getPathForLocation(e.getX(), e.getY());
                if (selRow != -1)
                {
                    if (e.getClickCount() == 1)
                    {
                        ChartsTreeSelector(selPath, false);
                    }
                    else if (e.getClickCount() == 2)
                    {
                        ChartsTreeSelector(selPath, true);
                    }
                }
            }
        };
        chartsTree.addMouseListener(ml);
    }
    
    protected void Intialise(GUI gui)
    {
        this.gui = gui;
        chartMaker = new ChartMaker("",gui);      
    }
    
    private void ChartsTreeSelector(TreePath treePath, boolean doubleClicked)
    {
        String selection = treePath.getLastPathComponent().toString();
        
        if(selection.equals("MY_WATCHLIST"))
        {            
            ResetChartPanelBoxes();
            createChartButton.setEnabled(false);
            return;
        }

        if (treePath.getPath().length > 2 && !selection.equals("MY_WATCHLIST"))
            createChartButton.setEnabled(true);
        else
            createChartButton.setEnabled(false);

        switch (treePath.getPath().length)
        {
            //all data except for address data (B,B & L)
            case 3:
                //Enable/select checkboxes according to data in selected database
                try (Connection connection = ConnectionDB.getConnection( treePath.getPath()[1].toString()))
                {
                    switch (selection)
                    {
                        case "NODE_DATA":                        
                            Component[] components = ResetChartPanelBoxes();
                            JCheckBox checkBox;
                            for (String column : gui.dbManager.GetColumnHeaders("node_data", connection))
                            {
                                if(column.endsWith("SENT") || column.endsWith("RECEIVED"))
                                    continue;
                                
                                for (Component c : components)
                                {
                                    if (c instanceof JCheckBox)
                                    {
                                        checkBox = (JCheckBox) c;
                                        if (checkBox.getActionCommand().toUpperCase().equals(column))
                                        {
                                            checkBox.setEnabled(true);
                                            checkBox.setVisible(true);
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        case "BANDWIDTH USAGE":
                            SetCheckboxes(new String[]{"BYTES_SENT","BYTES_RECEIVED","BYTES_SENT_AVG_MIN",
                                "BYTES_REC_AVG_MIN","BYTES_SENT_AVG_HOUR","BYTES_REC_AVG_HOUR",
                                "BYTES_SENT_AVG_DAY","BYTES_REC_AVG_DAY"});
                                break;
                        case "BUILDVERSION":
                            SetCheckboxes(new String[]{"BUILDVERSION"});
                            break;
                        case "PRICES":
                            String[] tableNames = new String[4];
                            if(gui.dbManager.TableExists("LTCPRICE", connection))
                            {
                                tableNames[0] = "LTCPRICE";
                                tableNames[1] = "LTCPRICE";
                            }
                            if(gui.dbManager.TableExists("DOGEPRICE", connection))
                            {
                                tableNames[2] = "DOGEPRICE";
                                tableNames[3] = "DOGEPRICE";
                            }
                            
                            SetCheckboxes(tableNames);
                            break;
                    }
                    connection.close();

                    break;
                }
                catch (Exception e)
                {
                    BackgroundService.AppendLog(e);
                }
            //all address data (B,B & L)   
            case 4:
                //get the address ID from properties and then the corresponding tables for that address from the database
                 try (Connection connection = ConnectionDB.getConnection( treePath.getPath()[1].toString()))
                {
                    int ID = gui.dbManager.GetAddressID(connection,chartsTree.getSelectionPath().getLastPathComponent().toString());
                    Component[] components = ResetChartPanelBoxes();
                    JCheckBox checkBox;
                    for (Component c : components)
                    {
                        if (c instanceof JCheckBox)
                        {
                            checkBox = (JCheckBox) c;
                            if (checkBox.getActionCommand().equals("balance"))
                            {
                                if (gui.dbManager.TableExists("WL_ADDRESS_" + ID + "_BALANCE", connection))
                                {
                                    checkBox.setEnabled(true);
                                    checkBox.setVisible(true);
                                    balanceDeltaBox.setEnabled(true);
                                    balanceDeltaBox.setVisible(true);
                                }
                            }
                            if (checkBox.getActionCommand().equals("blocks"))
                            {
                                if (gui.dbManager.TableExists("WL_ADDRESS_" + ID + "_BLOCKS", connection))
                                {
                                    checkBox.setEnabled(true);
                                    checkBox.setVisible(true);
                                    mintingRateBox.setEnabled(true);
                                    mintingRateBox.setVisible(true);
                                    efficiencyBox.setEnabled(true);
                                    efficiencyBox.setVisible(true);
                                    levelingSeperator.setVisible(true);
                                    levelProjectionButton.setVisible(true);
                                }
                            }
                            if (checkBox.getActionCommand().equals("level"))
                            {
                                if (gui.dbManager.TableExists("WL_ADDRESS_" + ID + "_LEVEL", connection))
                                {
                                    checkBox.setEnabled(true);
                                    checkBox.setVisible(true);
                                }
                            }
                        }
                    }
                    connection.close();
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
            break;
        }        
        chartsOptionsPanel.updateUI();
    } 
    
    private void SetCheckboxes(String[] tableNames)
    { 
        Component[] components = ResetChartPanelBoxes();
        
        for(String tableName : tableNames)
        {
            JCheckBox checkBox;
            for (Component c : components)
            {
                if (c instanceof JCheckBox)
                {
                    checkBox = (JCheckBox) c;
                    //since we use the action command of price boxes to determine which price type to use (qort 2 ltc or vice versa),
                    //we cannot compare it to the table name of the price, if it starts with ltc or doge, we can check for those tables
                    String boxName = checkBox.getActionCommand();
                    boxName = boxName.equals("ltc_to_qort_price") || boxName.equals("qort_to_ltc_price") ? "ltcprice" : boxName;
                    boxName = boxName.equals("doge_to_qort_price") || boxName.equals("qort_to_doge_price") ? "dogeprice" : boxName;
                    //don't break if box was already enabled (for prices implementation)
                    if (boxName.toUpperCase().equals(tableName) && !checkBox.isEnabled())
                    {
                        checkBox.setEnabled(true);
                        checkBox.setVisible(true);
                        break;
                    }
                }
            }        
        }            
    }  
    
    protected Component[] ResetChartPanelBoxes()
    {
            Component[] components = chartsOptionsPanel.getComponents();
            JCheckBox checkBox;

            for (Component c : components)
            {
                if (c instanceof JCheckBox)
                {
                    checkBox = (JCheckBox) c;
                    if(checkBox.getActionCommand().equals("skip"))//non dataItem cb's are always enabled
                        continue;
                    checkBox.setEnabled(false);
                    checkBox.setVisible(false);
                }
            }  
            levelingSeperator.setVisible(false);
            levelProjectionButton.setVisible(false);
            
            return components;
    }
    
    protected void PopulateChartsTree()
    {
        File folder = new File(DatabaseManager.dbFolderOS);
        ArrayList<File> dbFiles = new ArrayList<>();
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles)
        {
            if (file.isFile())
            {
                if (file.toString().toLowerCase().endsWith(".mv.db"))
                {
                    dbFiles.add(file);
                }
            }
        }

        DefaultMutableTreeNode databasesNode = new DefaultMutableTreeNode(new NodeInfo("Databases", "server.png"));
        chartsTreeModel.setRoot(databasesNode);

        DefaultMutableTreeNode[] databaseNodes = new DefaultMutableTreeNode[dbFiles.size()];

        for (int i = 0; i < dbFiles.size(); i++)
        {
            String dbName = dbFiles.get(i).getName().split("\\.", 2)[0];

            if (dbName.equals("properties"))
            {
                continue;
            }
            else
            {
                databaseNodes[i] = new DefaultMutableTreeNode(new NodeInfo(dbName, "database.png"));
                databasesNode.add(databaseNodes[i]);
            }

            var addressNodes = new ArrayList<DefaultMutableTreeNode>();
            DefaultMutableTreeNode watchlistNode = null;
            boolean pricesExists = false;

            try (Connection connection = ConnectionDB.getConnection(dbName))
            {
                for (String table : gui.dbManager.GetTables(connection))
                {
                    if (table.equals("NODE_PREFS"))
                        continue;                    

                    DefaultMutableTreeNode tableNode;
                    if (table.equals("MY_WATCHLIST"))
                    {
                        tableNode = new DefaultMutableTreeNode(new NodeInfo(table, "watchlist.png"));
                        watchlistNode = tableNode;
                        databaseNodes[i].add(tableNode);
                    }
                    else
                    {
                        tableNode = new DefaultMutableTreeNode(new NodeInfo(table, "charts_tree.png"));
                    }

                    if (table.startsWith("WL_"))
                    {
                        String ID = table.split("_")[2];
                        String name = (String) gui.dbManager.GetItemValue("my_watchlist", "name", "id", ID, connection);
                        String address = (String) gui.dbManager.GetItemValue("my_watchlist", "address", "id", ID, connection);
                        //filter out duplicates (multiple wl tables possible)
                        boolean exists = false;
                        for (var node : addressNodes)
                        {
                            if (node.getUserObject().toString().equals(name) || node.getUserObject().toString().equals(address))
                            {
                                exists = true;
                                break;
                            }
                        }
                        if (exists)
                            continue;

                        if (name.isEmpty())
                        {
                            tableNode = new DefaultMutableTreeNode(new NodeInfo(address, "charts_tree.png"));
                        }
                        else
                        {
                            tableNode = new DefaultMutableTreeNode(new NodeInfo(name, "charts_tree.png"));
                        }

                        addressNodes.add(tableNode);
                    }
                    if (table.endsWith("PRICE"))
                    {
                        //only need one prices node if any price table exists
                        if (!pricesExists)
                        {
                            pricesExists = true;
                            tableNode = new DefaultMutableTreeNode(new NodeInfo("PRICES", "charts_tree.png"));
                            databaseNodes[i].add(tableNode);
                        }
                    }
                    else
                    {
                        databaseNodes[i].add(tableNode);
                    }
                    
                    if(table.equals("NODE_DATA") && gui.dbManager.GetColumnHeaders("NODE_DATA", connection).contains("BYTES_SENT"))
                    {
                        tableNode = new DefaultMutableTreeNode(new NodeInfo("BANDWIDTH USAGE", "charts_tree.png"));
                        databaseNodes[i].add(tableNode);                        
                    }
                }

                connection.close();
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }

            if (watchlistNode != null)
            {
                for (var node : addressNodes)
                {
                    watchlistNode.add(node);
                }
            }
        }
        chartsTreeModel.reload(databasesNode);

        gui.ExpandTree(chartsTree, 1);
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

        chartsSplitPane = new javax.swing.JSplitPane();
        ChartsTreePane = new javax.swing.JScrollPane();
        chartsTree = new javax.swing.JTree();
        chartsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        chartsUISplitpane = new javax.swing.JSplitPane();
        chartsUIPanel = new javax.swing.JPanel();
        ChartOptionsMain = new javax.swing.JPanel();
        ChartOptionsScrollpane = new javax.swing.JScrollPane();
        ChartOptionsScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        chartsOptionsPanel = new javax.swing.JPanel();
        balanceCheckbox = new javax.swing.JCheckBox();
        levelCheckbox = new javax.swing.JCheckBox();
        numberOfConnectionsBox1 = new javax.swing.JCheckBox();
        allKnownPeersBox1 = new javax.swing.JCheckBox();
        uptimeBox1 = new javax.swing.JCheckBox();
        ltc2Qortbox = new javax.swing.JCheckBox();
        jSeparator8 = new javax.swing.JSeparator();
        cpu_tempBox1 = new javax.swing.JCheckBox();
        blockchainSizeBox1 = new javax.swing.JCheckBox();
        allOnlineMintersBox1 = new javax.swing.JCheckBox();
        blocksCheckbox = new javax.swing.JCheckBox();
        createChartButton = new javax.swing.JButton();
        jSeparator9 = new javax.swing.JSeparator();
        ramUsageBox1 = new javax.swing.JCheckBox();
        selectButton = new javax.swing.JButton();
        deselectButton = new javax.swing.JButton();
        crosshairsCheckbox = new javax.swing.JCheckBox();
        showDialogCheckbox = new javax.swing.JCheckBox();
        jSeparator10 = new javax.swing.JSeparator();
        buildversionCheckbox = new javax.swing.JCheckBox();
        doge2QortBox = new javax.swing.JCheckBox();
        myBlockHeightBox2 = new javax.swing.JCheckBox();
        blockHeightBox2 = new javax.swing.JCheckBox();
        levelProjectionButton = new javax.swing.JButton();
        levelingSeperator = new javax.swing.JSeparator();
        mintingRateBox = new javax.swing.JCheckBox();
        balanceDeltaBox = new javax.swing.JCheckBox();
        interpolateCheckbox = new javax.swing.JCheckBox();
        efficiencyBox = new javax.swing.JCheckBox();
        movingAverageBox = new javax.swing.JCheckBox();
        qort2LtcBox = new javax.swing.JCheckBox();
        qort2DogeBox = new javax.swing.JCheckBox();
        recPerHourBox = new javax.swing.JCheckBox();
        sentPerHourBox = new javax.swing.JCheckBox();
        bytesRecBox = new javax.swing.JCheckBox();
        bytesSentBox = new javax.swing.JCheckBox();
        sentPerDayBox = new javax.swing.JCheckBox();
        recPerDayBox = new javax.swing.JCheckBox();
        sentPerMinBox = new javax.swing.JCheckBox();
        recPerMinBox = new javax.swing.JCheckBox();

        chartsSplitPane.setDividerLocation(200);

        chartsTree.setFont(new java.awt.Font("Serif", 0, 12)); // NOI18N
        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        chartsTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        chartsTree.setCellRenderer(new NodeTreeCellRenderer());
        chartsTree.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                chartsTreeKeyReleased(evt);
            }
        });
        ChartsTreePane.setViewportView(chartsTree);

        chartsSplitPane.setLeftComponent(ChartsTreePane);

        chartsUISplitpane.setDividerLocation(200);
        chartsUISplitpane.setMinimumSize(new java.awt.Dimension(0, 200));

        javax.swing.GroupLayout chartsUIPanelLayout = new javax.swing.GroupLayout(chartsUIPanel);
        chartsUIPanel.setLayout(chartsUIPanelLayout);
        chartsUIPanelLayout.setHorizontalGroup(
            chartsUIPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 77, Short.MAX_VALUE)
        );
        chartsUIPanelLayout.setVerticalGroup(
            chartsUIPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 487, Short.MAX_VALUE)
        );

        chartsUISplitpane.setRightComponent(chartsUIPanel);

        ChartOptionsMain.setMinimumSize(new java.awt.Dimension(200, 0));

        chartsOptionsPanel.setMinimumSize(new java.awt.Dimension(200, 200));
        chartsOptionsPanel.setPreferredSize(new java.awt.Dimension(172, 600));
        chartsOptionsPanel.setLayout(new java.awt.GridBagLayout());

        balanceCheckbox.setSelected(true);
        balanceCheckbox.setText("Balance");
        balanceCheckbox.setActionCommand("balance");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(balanceCheckbox, gridBagConstraints);

        levelCheckbox.setSelected(true);
        levelCheckbox.setText("Level");
        levelCheckbox.setActionCommand("level");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(levelCheckbox, gridBagConstraints);

        numberOfConnectionsBox1.setSelected(true);
        numberOfConnectionsBox1.setText("Connected peers");
        numberOfConnectionsBox1.setActionCommand("numberOfConnections");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(numberOfConnectionsBox1, gridBagConstraints);

        allKnownPeersBox1.setSelected(true);
        allKnownPeersBox1.setText("All known peers");
        allKnownPeersBox1.setActionCommand("allKnownPeers");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(allKnownPeersBox1, gridBagConstraints);

        uptimeBox1.setSelected(true);
        uptimeBox1.setText("Uptime");
        uptimeBox1.setActionCommand("uptime");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(uptimeBox1, gridBagConstraints);

        ltc2Qortbox.setSelected(true);
        ltc2Qortbox.setText("Litecoin to Qort price");
        ltc2Qortbox.setActionCommand("ltc_to_qort_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(ltc2Qortbox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(jSeparator8, gridBagConstraints);

        cpu_tempBox1.setSelected(true);
        cpu_tempBox1.setText("CPU temperature");
        cpu_tempBox1.setToolTipText("On Windows systems, Open Hardware Monitor needs to be installed and running on your system in order to fetch CPU temperature data");
        cpu_tempBox1.setActionCommand("cpu_temp");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 35;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(cpu_tempBox1, gridBagConstraints);

        blockchainSizeBox1.setSelected(true);
        blockchainSizeBox1.setText("Blockchain size");
        blockchainSizeBox1.setActionCommand("blockchainsize");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 36;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blockchainSizeBox1, gridBagConstraints);

        allOnlineMintersBox1.setSelected(true);
        allOnlineMintersBox1.setText("All online minters");
        allOnlineMintersBox1.setActionCommand("allOnlineMinters");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(allOnlineMintersBox1, gridBagConstraints);

        blocksCheckbox.setSelected(true);
        blocksCheckbox.setText("Blocks minted");
        blocksCheckbox.setActionCommand("blocks");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blocksCheckbox, gridBagConstraints);

        createChartButton.setText("Create chart");
        createChartButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                createChartButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(createChartButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(jSeparator9, gridBagConstraints);

        ramUsageBox1.setSelected(true);
        ramUsageBox1.setText("ReQorder RAM usage");
        ramUsageBox1.setActionCommand("ram_usage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 37;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 10, 10);
        chartsOptionsPanel.add(ramUsageBox1, gridBagConstraints);

        selectButton.setText("Select all");
        selectButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 38;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
        chartsOptionsPanel.add(selectButton, gridBagConstraints);

        deselectButton.setText("Deselect all");
        deselectButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deselectButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 39;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 12, 0);
        chartsOptionsPanel.add(deselectButton, gridBagConstraints);

        crosshairsCheckbox.setSelected(true);
        crosshairsCheckbox.setText("Show crosshairs");
        crosshairsCheckbox.setActionCommand("skip");
        crosshairsCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                crosshairsCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(crosshairsCheckbox, gridBagConstraints);

        showDialogCheckbox.setSelected(true);
        showDialogCheckbox.setText("Show dialog");
        showDialogCheckbox.setActionCommand("skip");
        showDialogCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showDialogCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(showDialogCheckbox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(jSeparator10, gridBagConstraints);

        buildversionCheckbox.setSelected(true);
        buildversionCheckbox.setText("Buildversion");
        buildversionCheckbox.setActionCommand("buildversion");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(buildversionCheckbox, gridBagConstraints);

        doge2QortBox.setSelected(true);
        doge2QortBox.setText("Dogecoin to Qort price");
        doge2QortBox.setActionCommand("doge_to_qort_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(doge2QortBox, gridBagConstraints);

        myBlockHeightBox2.setSelected(true);
        myBlockHeightBox2.setText("Blockheight (node)");
        myBlockHeightBox2.setActionCommand("myblockheight");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(myBlockHeightBox2, gridBagConstraints);

        blockHeightBox2.setSelected(true);
        blockHeightBox2.setText("Blockheight (chain)");
        blockHeightBox2.setActionCommand("blockheight");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blockHeightBox2, gridBagConstraints);

        levelProjectionButton.setText("Levelling projection");
        levelProjectionButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                levelProjectionButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 5, 0);
        chartsOptionsPanel.add(levelProjectionButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(levelingSeperator, gridBagConstraints);

        mintingRateBox.setSelected(true);
        mintingRateBox.setText("Minting rate");
        mintingRateBox.setActionCommand("mintingrate");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(mintingRateBox, gridBagConstraints);

        balanceDeltaBox.setSelected(true);
        balanceDeltaBox.setText("Balance delta");
        balanceDeltaBox.setActionCommand("balancedelta");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(balanceDeltaBox, gridBagConstraints);

        interpolateCheckbox.setSelected(true);
        interpolateCheckbox.setText("Interpolate snapshots");
        interpolateCheckbox.setActionCommand("skip");
        interpolateCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                interpolateCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(interpolateCheckbox, gridBagConstraints);

        efficiencyBox.setSelected(true);
        efficiencyBox.setText("Minting efficiency");
        efficiencyBox.setActionCommand("efficiency");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(efficiencyBox, gridBagConstraints);

        movingAverageBox.setText("Moving average");
        movingAverageBox.setActionCommand("skip");
        movingAverageBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                movingAverageBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(movingAverageBox, gridBagConstraints);

        qort2LtcBox.setSelected(true);
        qort2LtcBox.setText("Qort to Litecoin price");
        qort2LtcBox.setActionCommand("qort_to_ltc_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(qort2LtcBox, gridBagConstraints);

        qort2DogeBox.setSelected(true);
        qort2DogeBox.setText("Qort to Dogecoin price");
        qort2DogeBox.setActionCommand("qort_to_doge_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(qort2DogeBox, gridBagConstraints);

        recPerHourBox.setSelected(true);
        recPerHourBox.setText("Avg received per hour");
        recPerHourBox.setActionCommand("bytes_rec_avg_hour");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerHourBox, gridBagConstraints);

        sentPerHourBox.setSelected(true);
        sentPerHourBox.setText("Avg sent per hour");
        sentPerHourBox.setActionCommand("bytes_sent_avg_hour");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerHourBox, gridBagConstraints);

        bytesRecBox.setSelected(true);
        bytesRecBox.setText("Bytes received");
        bytesRecBox.setActionCommand("bytes_received");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(bytesRecBox, gridBagConstraints);

        bytesSentBox.setSelected(true);
        bytesSentBox.setText("Bytes sent");
        bytesSentBox.setActionCommand("bytes_sent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(bytesSentBox, gridBagConstraints);

        sentPerDayBox.setSelected(true);
        sentPerDayBox.setText("Avg sent per day");
        sentPerDayBox.setActionCommand("bytes_sent_avg_day");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerDayBox, gridBagConstraints);

        recPerDayBox.setSelected(true);
        recPerDayBox.setText("Avg received per day");
        recPerDayBox.setActionCommand("bytes_rec_avg_day");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 34;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerDayBox, gridBagConstraints);

        sentPerMinBox.setSelected(true);
        sentPerMinBox.setText("Avg sent per minute");
        sentPerMinBox.setActionCommand("bytes_sent_avg_min");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerMinBox, gridBagConstraints);

        recPerMinBox.setSelected(true);
        recPerMinBox.setText("Avg received per minute");
        recPerMinBox.setActionCommand("bytes_rec_avg_min");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerMinBox, gridBagConstraints);

        ChartOptionsScrollpane.setViewportView(chartsOptionsPanel);

        javax.swing.GroupLayout ChartOptionsMainLayout = new javax.swing.GroupLayout(ChartOptionsMain);
        ChartOptionsMain.setLayout(ChartOptionsMainLayout);
        ChartOptionsMainLayout.setHorizontalGroup(
            ChartOptionsMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
            .addGroup(ChartOptionsMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(ChartOptionsScrollpane, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE))
        );
        ChartOptionsMainLayout.setVerticalGroup(
            ChartOptionsMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 487, Short.MAX_VALUE)
            .addGroup(ChartOptionsMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(ChartOptionsScrollpane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 487, Short.MAX_VALUE))
        );

        chartsUISplitpane.setLeftComponent(ChartOptionsMain);

        chartsSplitPane.setRightComponent(chartsUISplitpane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chartsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 487, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chartsSplitPane)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void chartsTreeKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_chartsTreeKeyReleased
    {//GEN-HEADEREND:event_chartsTreeKeyReleased
        ChartsTreeSelector(chartsTree.getSelectionPath(), false);
    }//GEN-LAST:event_chartsTreeKeyReleased

    private void createChartButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_createChartButtonActionPerformed
    {//GEN-HEADEREND:event_createChartButtonActionPerformed
        if (chartsTree.getSelectionPath() == null)//failsafe, should never be the case (button should be disabled)
        {
            JOptionPane.showMessageDialog(this, "Please select a dataset from the list", "Select a dataset", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String selectedTable = chartsTree.getSelectionPath().getLastPathComponent().toString();
        String database = chartsTree.getSelectionPath().getPath()[1].toString();

        boolean uptimeSelected = false;

        ArrayList<String> selectedItems = new ArrayList<>();
        Component[] components = chartsOptionsPanel.getComponents();
        JCheckBox checkBox;

        //collect all selected axes
        for (Component c : components)
        {
            if (c instanceof JCheckBox)
            {
                checkBox = (JCheckBox) c;
                if (checkBox.isEnabled() && checkBox.isSelected())
                {
                    if (checkBox.getActionCommand().equals("skip"))//for non dataItem cb's
                        continue;
                    
                    //We want uptime to be the last added item for the chartDialog formatting
                    if (checkBox.getActionCommand().equals("uptime"))
                    {
                        uptimeSelected = true;
                        continue;
                    }
                    selectedItems.add(checkBox.getActionCommand());
                }
            }
        }

        if (uptimeSelected)
            selectedItems.add("uptime");
        
        //we want  level to be the first chart, so they'll always use a steprenderer
        if (selectedItems.contains("level"))
        {
            selectedItems.remove("level");
            selectedItems.add(0, "level");
        }

        ArrayList<ResultSet> resultSets = new ArrayList<>();
        ArrayList<Statement> statements = new ArrayList<>();

        try (Connection connection = ConnectionDB.getConnection(database))
        {
            switch (chartsTree.getSelectionPath().getPath().length)
            {
                //all tables except address tables
                case 3:
                    switch (selectedTable)
                    {
                        case "NODE_DATA":
                        case "BUILDVERSION":
                            String query = "select timestamp,";

                            for (String axis : selectedItems)
                                query += axis + ",";
                            
                            query = query.substring(0, query.length() - 1);//remove last comma
                            query += " from " + selectedTable;

                            //need to iterate resultset multiple times to create seperate datasets, hence the createStatement parameters
                            statements.add(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
                            resultSets.add(statements.get(0).executeQuery(query));
                            break;
                        case "PRICES":
                            for (int i = 0; i < selectedItems.size(); i++)
                            {
                                query = "";
                                String axis = selectedItems.get(i);
                                if (axis.equals("qort_to_ltc_price") || axis.equals("ltc_to_qort_price"))
                                    query = "select timestamp, ltcprice from ltcprice";
                                
                                if (axis.equals("qort_to_doge_price") || axis.equals("doge_to_qort_price"))
                                    query = "select timestamp, dogeprice from dogeprice";                                

                                //need to iterate resultset multiple times to create seperate datasets, hence the createStatement parameters
                                statements.add(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
                                resultSets.add(statements.get(i).executeQuery(query));
                            }
                            break;
                        case "BANDWIDTH USAGE":
                            for (int i = 0; i < selectedItems.size(); i++)
                            {
                                query = "";
                                String axis = selectedItems.get(i);
                                switch(axis)
                                {
                                    case "bytes_sent":                                        
                                        query = "select timestamp, bytes_sent from node_data";
                                        break;
                                    case "bytes_received":                                        
                                        query = "select timestamp, bytes_received from node_data";
                                        break;
                                    case "bytes_sent_avg_min": ;
                                    case "bytes_sent_avg_hour":  ;
                                    case "bytes_sent_avg_day":                                         
                                        query = "select timestamp, avg_bytes_sent from node_data";
                                        break;
                                    case "bytes_rec_avg_min":
                                    case "bytes_rec_avg_hour":
                                    case "bytes_rec_avg_day":                                      
                                        query = "select timestamp, avg_bytes_received from node_data";
                                        break;                                        
                                } 
                                //need to iterate resultset multiple times to create seperate datasets, hence the createStatement parameters
                                statements.add(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
                                resultSets.add(statements.get(i).executeQuery(query));
                            }                            
                            break;
                    }
                    break;
                //all address tables
                case 4:
                    int ID = gui.dbManager.GetAddressID(connection, chartsTree.getSelectionPath().getLastPathComponent().toString());
                    for (int i = 0; i < selectedItems.size(); i++)
                    {
                        String axis = selectedItems.get(i);
                        String query = null;
                        switch (axis)
                        {
                            case "blocks":
                            case "mintingrate":
                            case "efficiency":
                                query = String.format("select timestamp, blocksminted from %s",
                                        "WL_ADDRESS_" + ID + "_BLOCKS");
                                break;
                            case "balance":
                            case "balancedelta":
                                query = String.format("select timestamp, balance from %s",
                                        "WL_ADDRESS_" + ID + "_BALANCE");
                                break;
                            case "level":
                                query = String.format("select timestamp, level from %s",
                                        "WL_ADDRESS_" + ID + "_LEVEL");
                                break;
                        }

                        //need to iterate resultset multiple times to create seperate datasets, hence the createStatement parameters
                        statements.add(connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
                        resultSets.add(statements.get(i).executeQuery(query));
                    }
                    break;
            }

            chartPanel = chartMaker.createChartPanel(
                    Utilities.ToH2Char(selectedTable) + " from " + Utilities.ToH2Char(database), resultSets, selectedItems);
            chartsUISplitpane.setRightComponent(chartPanel);
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_createChartButtonActionPerformed

    private void selectButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectButtonActionPerformed
    {//GEN-HEADEREND:event_selectButtonActionPerformed
        Component[] components = chartsOptionsPanel.getComponents();
        JCheckBox checkBox;

        for (Component c : components)
        {
            if (c instanceof JCheckBox)
            {
                checkBox = (JCheckBox) c;

                if (checkBox.getActionCommand().equals("skip"))//non dataItem cb's
                    continue;         

                if (checkBox.isEnabled())
                    checkBox.setSelected(true);
            }
        }
    }//GEN-LAST:event_selectButtonActionPerformed

    private void deselectButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deselectButtonActionPerformed
    {//GEN-HEADEREND:event_deselectButtonActionPerformed
        Component[] components = chartsOptionsPanel.getComponents();
        JCheckBox checkBox;

        for (Component c : components)
        {
            if (c instanceof JCheckBox)
            {
                checkBox = (JCheckBox) c;

                if (checkBox.getActionCommand().equals("skip"))//non dataItem cb'
                    continue;                

                if (checkBox.isEnabled())
                    checkBox.setSelected(false);                
            }
        }
    }//GEN-LAST:event_deselectButtonActionPerformed

    private void crosshairsCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_crosshairsCheckboxActionPerformed
    {//GEN-HEADEREND:event_crosshairsCheckboxActionPerformed
        chartMaker.showCrosshairs = crosshairsCheckbox.isSelected();
    }//GEN-LAST:event_crosshairsCheckboxActionPerformed

    private void showDialogCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showDialogCheckboxActionPerformed
    {//GEN-HEADEREND:event_showDialogCheckboxActionPerformed
        chartMaker.showDialog = showDialogCheckbox.isSelected();
    }//GEN-LAST:event_showDialogCheckboxActionPerformed

    private void levelProjectionButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_levelProjectionButtonActionPerformed
    {//GEN-HEADEREND:event_levelProjectionButtonActionPerformed
        String selectedTable = chartsTree.getSelectionPath().getLastPathComponent().toString();
        String database = chartsTree.getSelectionPath().getPath()[1].toString();

        try (Connection connection = ConnectionDB.getConnection( database))
        {
            ArrayList<ResultSet> resultSets = new ArrayList<>();
            ArrayList<String> selectedItems = new ArrayList<>();
            selectedItems.add("levelling");
            int ID = gui.dbManager.GetAddressID(connection,chartsTree.getSelectionPath().getLastPathComponent().toString());
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            resultSets.add(statement.executeQuery("select timestamp,blocksminted from WL_ADDRESS_" + ID + "_BLOCKS"));

            chartPanel = chartMaker.createChartPanel(
                Utilities.ToH2Char(selectedTable) + " from " + Utilities.ToH2Char(database),resultSets, selectedItems);
            chartsUISplitpane.setRightComponent(chartPanel);
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_levelProjectionButtonActionPerformed

    private void interpolateCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_interpolateCheckboxActionPerformed
    {//GEN-HEADEREND:event_interpolateCheckboxActionPerformed
        chartMaker.interpolateEnabled = interpolateCheckbox.isSelected();

        if (chartMaker.chartPanel == null)
            return;
        
        String nodeTitle = chartsTree.getSelectionPath().getLastPathComponent().toString();
        String chartTitle = chartMaker.chartTitle.replace("'", "*").split("\\*")[1];

        if (chartTitle.equals(nodeTitle))
            chartMaker.SetInterpolation(interpolateCheckbox.isSelected());
    }//GEN-LAST:event_interpolateCheckboxActionPerformed

    private void movingAverageBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_movingAverageBoxActionPerformed
    {//GEN-HEADEREND:event_movingAverageBoxActionPerformed
        chartMaker.movingAverageEnabled = movingAverageBox.isSelected();

        if (chartMaker.chartPanel== null)
            return;        

        String nodeTitle = chartsTree.getSelectionPath().getLastPathComponent().toString();
        //replace single quotes by asterisks to split the title and get the axis name
        String chartTitle = chartMaker.chartTitle.replace("'", "*").split("\\*")[1];

        if (chartTitle.equals(nodeTitle))
            chartMaker.SetMovingAverage(movingAverageBox.isSelected());
    }//GEN-LAST:event_movingAverageBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ChartOptionsMain;
    private javax.swing.JScrollPane ChartOptionsScrollpane;
    private javax.swing.JScrollPane ChartsTreePane;
    private javax.swing.JCheckBox allKnownPeersBox1;
    private javax.swing.JCheckBox allOnlineMintersBox1;
    private javax.swing.JCheckBox balanceCheckbox;
    private javax.swing.JCheckBox balanceDeltaBox;
    private javax.swing.JCheckBox blockHeightBox2;
    private javax.swing.JCheckBox blockchainSizeBox1;
    private javax.swing.JCheckBox blocksCheckbox;
    private javax.swing.JCheckBox buildversionCheckbox;
    private javax.swing.JCheckBox bytesRecBox;
    private javax.swing.JCheckBox bytesSentBox;
    private javax.swing.JPanel chartsOptionsPanel;
    private javax.swing.JSplitPane chartsSplitPane;
    private javax.swing.JTree chartsTree;
    private javax.swing.JPanel chartsUIPanel;
    private javax.swing.JSplitPane chartsUISplitpane;
    private javax.swing.JCheckBox cpu_tempBox1;
    protected javax.swing.JButton createChartButton;
    private javax.swing.JCheckBox crosshairsCheckbox;
    private javax.swing.JButton deselectButton;
    private javax.swing.JCheckBox doge2QortBox;
    private javax.swing.JCheckBox efficiencyBox;
    private javax.swing.JCheckBox interpolateCheckbox;
    private javax.swing.JSeparator jSeparator10;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JSeparator jSeparator9;
    private javax.swing.JCheckBox levelCheckbox;
    private javax.swing.JButton levelProjectionButton;
    private javax.swing.JSeparator levelingSeperator;
    private javax.swing.JCheckBox ltc2Qortbox;
    private javax.swing.JCheckBox mintingRateBox;
    private javax.swing.JCheckBox movingAverageBox;
    private javax.swing.JCheckBox myBlockHeightBox2;
    private javax.swing.JCheckBox numberOfConnectionsBox1;
    private javax.swing.JCheckBox qort2DogeBox;
    private javax.swing.JCheckBox qort2LtcBox;
    private javax.swing.JCheckBox ramUsageBox1;
    private javax.swing.JCheckBox recPerDayBox;
    private javax.swing.JCheckBox recPerHourBox;
    private javax.swing.JCheckBox recPerMinBox;
    private javax.swing.JButton selectButton;
    private javax.swing.JCheckBox sentPerDayBox;
    private javax.swing.JCheckBox sentPerHourBox;
    private javax.swing.JCheckBox sentPerMinBox;
    private javax.swing.JCheckBox showDialogCheckbox;
    private javax.swing.JCheckBox uptimeBox1;
    // End of variables declaration//GEN-END:variables
}