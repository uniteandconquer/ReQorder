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

        DefaultMutableTreeNode databasesNode = 
                new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("databases"), "server.png"));
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
        numberOfConnectionsBox = new javax.swing.JCheckBox();
        allKnownPeersBox = new javax.swing.JCheckBox();
        uptimeBox = new javax.swing.JCheckBox();
        ltc2Qortbox = new javax.swing.JCheckBox();
        jSeparator8 = new javax.swing.JSeparator();
        cpu_tempBox = new javax.swing.JCheckBox();
        blockchainSizeBox = new javax.swing.JCheckBox();
        allOnlineMintersBox = new javax.swing.JCheckBox();
        blocksMintedBox = new javax.swing.JCheckBox();
        createChartButton = new javax.swing.JButton();
        jSeparator9 = new javax.swing.JSeparator();
        ramUsageBox = new javax.swing.JCheckBox();
        selectAllButton = new javax.swing.JButton();
        deselectAllButton = new javax.swing.JButton();
        crosshairsCheckbox = new javax.swing.JCheckBox();
        showDialogCheckbox = new javax.swing.JCheckBox();
        jSeparator10 = new javax.swing.JSeparator();
        buildversionCheckbox = new javax.swing.JCheckBox();
        doge2QortBox = new javax.swing.JCheckBox();
        myBlockHeightBox = new javax.swing.JCheckBox();
        blockHeightBox = new javax.swing.JCheckBox();
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
        showSnapshotsBox = new javax.swing.JCheckBox();
        jSeparator11 = new javax.swing.JSeparator();
        averageLabel = new javax.swing.JLabel();
        averagingSlider = new javax.swing.JSlider();
        showDataBox = new javax.swing.JCheckBox();
        averageAllBox = new javax.swing.JCheckBox();
        qortalRamBox = new javax.swing.JCheckBox();
        cpu_usageBox = new javax.swing.JCheckBox();

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
            .addGap(0, 166, Short.MAX_VALUE)
        );
        chartsUIPanelLayout.setVerticalGroup(
            chartsUIPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1375, Short.MAX_VALUE)
        );

        chartsUISplitpane.setRightComponent(chartsUIPanel);

        ChartOptionsMain.setMinimumSize(new java.awt.Dimension(200, 0));

        chartsOptionsPanel.setMinimumSize(new java.awt.Dimension(200, 200));
        chartsOptionsPanel.setPreferredSize(new java.awt.Dimension(172, 600));
        chartsOptionsPanel.setLayout(new java.awt.GridBagLayout());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        balanceCheckbox.setText(bundle.getString("balanceCheckBox")); // NOI18N
        balanceCheckbox.setActionCommand("balance");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(balanceCheckbox, gridBagConstraints);

        levelCheckbox.setText(bundle.getString("levelCheckbox")); // NOI18N
        levelCheckbox.setActionCommand("level");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(levelCheckbox, gridBagConstraints);

        numberOfConnectionsBox.setText(bundle.getString("numberOfConnectionsBox")); // NOI18N
        numberOfConnectionsBox.setActionCommand("numberOfConnections");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(numberOfConnectionsBox, gridBagConstraints);

        allKnownPeersBox.setText(bundle.getString("allKnownPeersBox")); // NOI18N
        allKnownPeersBox.setActionCommand("allKnownPeers");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(allKnownPeersBox, gridBagConstraints);

        uptimeBox.setText(bundle.getString("uptimeBox")); // NOI18N
        uptimeBox.setActionCommand("uptime");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(uptimeBox, gridBagConstraints);

        ltc2Qortbox.setText(bundle.getString("ltc2QortBox")); // NOI18N
        ltc2Qortbox.setActionCommand("ltc_to_qort_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(ltc2Qortbox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(jSeparator8, gridBagConstraints);

        cpu_tempBox.setText(bundle.getString("cpuTempBox")); // NOI18N
        cpu_tempBox.setToolTipText(bundle.getString("cpuTempBoxTooltip")); // NOI18N
        cpu_tempBox.setActionCommand("cpu_temp");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 42;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(cpu_tempBox, gridBagConstraints);

        blockchainSizeBox.setText(bundle.getString("blockChainSizeBox")); // NOI18N
        blockchainSizeBox.setActionCommand("blockchainsize");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 45;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blockchainSizeBox, gridBagConstraints);

        allOnlineMintersBox.setText(bundle.getString("allOnlineMintersBox")); // NOI18N
        allOnlineMintersBox.setActionCommand("allOnlineMinters");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(allOnlineMintersBox, gridBagConstraints);

        blocksMintedBox.setText(bundle.getString("blocksMintedBox")); // NOI18N
        blocksMintedBox.setActionCommand("blocks");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blocksMintedBox, gridBagConstraints);

        createChartButton.setText(bundle.getString("createChartButton")); // NOI18N
        createChartButton.setActionCommand(bundle.getString("createChartButton")); // NOI18N
        createChartButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                createChartButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(createChartButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(jSeparator9, gridBagConstraints);

        ramUsageBox.setText(bundle.getString("ramUsageBox")); // NOI18N
        ramUsageBox.setActionCommand("ram_usage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 46;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 10, 10);
        chartsOptionsPanel.add(ramUsageBox, gridBagConstraints);

        selectAllButton.setText(bundle.getString("selectAllButton")); // NOI18N
        selectAllButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectAllButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 47;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
        chartsOptionsPanel.add(selectAllButton, gridBagConstraints);

        deselectAllButton.setText(bundle.getString("deselectAllButton")); // NOI18N
        deselectAllButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deselectAllButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 48;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 12, 0);
        chartsOptionsPanel.add(deselectAllButton, gridBagConstraints);

        crosshairsCheckbox.setSelected(true);
        crosshairsCheckbox.setText(bundle.getString("crosshairsCheckbox")); // NOI18N
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
        showDialogCheckbox.setText(bundle.getString("showDialogCheckbox")); // NOI18N
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

        buildversionCheckbox.setText(bundle.getString("buildversionCheckBox")); // NOI18N
        buildversionCheckbox.setActionCommand("buildversion");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(buildversionCheckbox, gridBagConstraints);

        doge2QortBox.setText(bundle.getString("doge2QortBox")); // NOI18N
        doge2QortBox.setActionCommand("doge_to_qort_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(doge2QortBox, gridBagConstraints);

        myBlockHeightBox.setText(bundle.getString("myBlockHeightBox")); // NOI18N
        myBlockHeightBox.setActionCommand("myblockheight");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(myBlockHeightBox, gridBagConstraints);

        blockHeightBox.setText(bundle.getString("blockHeightBox")); // NOI18N
        blockHeightBox.setActionCommand("blockheight");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(blockHeightBox, gridBagConstraints);

        levelProjectionButton.setText(bundle.getString("levellingProjectionButton")); // NOI18N
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
        gridBagConstraints.gridy = 15;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(levelingSeperator, gridBagConstraints);

        mintingRateBox.setText(bundle.getString("mintingRateBox")); // NOI18N
        mintingRateBox.setActionCommand("mintingrate");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(mintingRateBox, gridBagConstraints);

        balanceDeltaBox.setText(bundle.getString("balanceDeltaBox")); // NOI18N
        balanceDeltaBox.setActionCommand("balancedelta");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(balanceDeltaBox, gridBagConstraints);

        interpolateCheckbox.setSelected(true);
        interpolateCheckbox.setText(bundle.getString("interpolateCheckbox")); // NOI18N
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

        efficiencyBox.setText(bundle.getString("efficiencyBox")); // NOI18N
        efficiencyBox.setActionCommand("efficiency");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(efficiencyBox, gridBagConstraints);

        movingAverageBox.setText(bundle.getString("showMovingAverageBox")); // NOI18N
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
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(movingAverageBox, gridBagConstraints);

        qort2LtcBox.setText(bundle.getString("qort2LtcBox")); // NOI18N
        qort2LtcBox.setActionCommand("qort_to_ltc_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(qort2LtcBox, gridBagConstraints);

        qort2DogeBox.setText(bundle.getString("qort2DogeBox")); // NOI18N
        qort2DogeBox.setActionCommand("qort_to_doge_price");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(qort2DogeBox, gridBagConstraints);

        recPerHourBox.setText(bundle.getString("recPerHourBox")); // NOI18N
        recPerHourBox.setActionCommand("bytes_rec_avg_hour");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerHourBox, gridBagConstraints);

        sentPerHourBox.setText(bundle.getString("sentPerHourBox")); // NOI18N
        sentPerHourBox.setActionCommand("bytes_sent_avg_hour");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 37;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerHourBox, gridBagConstraints);

        bytesRecBox.setText(bundle.getString("bytesReceivedBox")); // NOI18N
        bytesRecBox.setActionCommand("bytes_received");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 35;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(bytesRecBox, gridBagConstraints);

        bytesSentBox.setText(bundle.getString("bytesSentbox")); // NOI18N
        bytesSentBox.setActionCommand("bytes_sent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 34;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(bytesSentBox, gridBagConstraints);

        sentPerDayBox.setText(bundle.getString("sentPerDaybox")); // NOI18N
        sentPerDayBox.setActionCommand("bytes_sent_avg_day");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 38;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerDayBox, gridBagConstraints);

        recPerDayBox.setText(bundle.getString("recPerDayBox")); // NOI18N
        recPerDayBox.setActionCommand("bytes_rec_avg_day");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 41;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerDayBox, gridBagConstraints);

        sentPerMinBox.setText(bundle.getString("sentPerMinBox")); // NOI18N
        sentPerMinBox.setActionCommand("bytes_sent_avg_min");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 36;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(sentPerMinBox, gridBagConstraints);

        recPerMinBox.setText(bundle.getString("recPerinBox")); // NOI18N
        recPerMinBox.setActionCommand("bytes_rec_avg_min");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 39;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(recPerMinBox, gridBagConstraints);

        showSnapshotsBox.setSelected(true);
        showSnapshotsBox.setText(bundle.getString("showSnapshotsBox")); // NOI18N
        showSnapshotsBox.setActionCommand("skip");
        showSnapshotsBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showSnapshotsBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(showSnapshotsBox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsOptionsPanel.add(jSeparator11, gridBagConstraints);

        averageLabel.setText(bundle.getString("averageLabelDefault")); // NOI18N
        averageLabel.setToolTipText(bundle.getString("averageLabelTooltip")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        chartsOptionsPanel.add(averageLabel, gridBagConstraints);

        averagingSlider.setMaximum(200);
        averagingSlider.setMinimum(5);
        averagingSlider.setValue(5);
        averagingSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                averagingSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        chartsOptionsPanel.add(averagingSlider, gridBagConstraints);

        showDataBox.setSelected(true);
        showDataBox.setText(bundle.getString("showRawDataBox")); // NOI18N
        showDataBox.setActionCommand("skip");
        showDataBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showDataBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(showDataBox, gridBagConstraints);

        averageAllBox.setText(bundle.getString("averageAllBox")); // NOI18N
        averageAllBox.setActionCommand("skip");
        averageAllBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                averageAllBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        chartsOptionsPanel.add(averageAllBox, gridBagConstraints);

        qortalRamBox.setText(bundle.getString("qortalRamBox")); // NOI18N
        qortalRamBox.setActionCommand("qortal_ram");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 44;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(qortalRamBox, gridBagConstraints);

        cpu_usageBox.setText(bundle.getString("cpu_usageBox")); // NOI18N
        cpu_usageBox.setToolTipText("");
        cpu_usageBox.setActionCommand("cpu_usage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 43;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 3, 10);
        chartsOptionsPanel.add(cpu_usageBox, gridBagConstraints);

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
            .addGap(0, 1375, Short.MAX_VALUE)
            .addGroup(ChartOptionsMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(ChartOptionsScrollpane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1375, Short.MAX_VALUE))
        );

        chartsUISplitpane.setLeftComponent(ChartOptionsMain);

        chartsSplitPane.setRightComponent(chartsUISplitpane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chartsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 574, Short.MAX_VALUE)
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
            JOptionPane.showMessageDialog(this, Main.BUNDLE.getString("selectDataset"),
                    Main.BUNDLE.getString("selectDatasetTitle"), JOptionPane.INFORMATION_MESSAGE);
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
        
        if(selectedItems.isEmpty())
        {
            JOptionPane.showMessageDialog(BackgroundService.GUI, Main.BUNDLE.getString("noDatasetSelected"));
            return;
        }
        movingAverageBox.setEnabled(selectedItems.size() == 1);
        showDataBox.setEnabled(selectedItems.size() == 1);

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
                    Utilities.SingleQuotedString(selectedTable) + " from " + Utilities.SingleQuotedString(database), resultSets, selectedItems);
            chartsUISplitpane.setRightComponent(chartPanel);
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_createChartButtonActionPerformed

    private void selectAllButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectAllButtonActionPerformed
    {//GEN-HEADEREND:event_selectAllButtonActionPerformed
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
    }//GEN-LAST:event_selectAllButtonActionPerformed

    private void deselectAllButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deselectAllButtonActionPerformed
    {//GEN-HEADEREND:event_deselectAllButtonActionPerformed
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
    }//GEN-LAST:event_deselectAllButtonActionPerformed

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
                Utilities.SingleQuotedString(selectedTable) + " from " + Utilities.SingleQuotedString(database),resultSets, selectedItems);
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

    private void showSnapshotsBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showSnapshotsBoxActionPerformed
    {//GEN-HEADEREND:event_showSnapshotsBoxActionPerformed
        chartMaker.snapshotsEnabled = showSnapshotsBox.isSelected();
        chartMaker.ToggleSnapshots();
    }//GEN-LAST:event_showSnapshotsBoxActionPerformed

    private void averagingSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_averagingSliderStateChanged
    {//GEN-HEADEREND:event_averagingSliderStateChanged
        String[] split = Main.BUNDLE.getString("averageLabel").split("%%");
        averageLabel.setText(split[0] + averagingSlider.getValue() + split[1]);
        chartMaker.averagingPeriod = averagingSlider.getValue();
    }//GEN-LAST:event_averagingSliderStateChanged

    private void showDataBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showDataBoxActionPerformed
    {//GEN-HEADEREND:event_showDataBoxActionPerformed
        chartMaker.showRawData = showDataBox.isSelected();
        
        if (chartMaker.chartPanel== null)
            return;        

        String nodeTitle = chartsTree.getSelectionPath().getLastPathComponent().toString();
        //replace single quotes by asterisks to split the title and get the axis name
        String chartTitle = chartMaker.chartTitle.replace("'", "*").split("\\*")[1];

        if (chartTitle.equals(nodeTitle))
            chartMaker.ShowRawData(showDataBox.isSelected());
    }//GEN-LAST:event_showDataBoxActionPerformed

    private void averageAllBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_averageAllBoxActionPerformed
    {//GEN-HEADEREND:event_averageAllBoxActionPerformed
        chartMaker.averageAll = averageAllBox.isSelected();
    }//GEN-LAST:event_averageAllBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ChartOptionsMain;
    private javax.swing.JScrollPane ChartOptionsScrollpane;
    private javax.swing.JScrollPane ChartsTreePane;
    private javax.swing.JCheckBox allKnownPeersBox;
    private javax.swing.JCheckBox allOnlineMintersBox;
    protected javax.swing.JCheckBox averageAllBox;
    private javax.swing.JLabel averageLabel;
    private javax.swing.JSlider averagingSlider;
    private javax.swing.JCheckBox balanceCheckbox;
    private javax.swing.JCheckBox balanceDeltaBox;
    private javax.swing.JCheckBox blockHeightBox;
    private javax.swing.JCheckBox blockchainSizeBox;
    private javax.swing.JCheckBox blocksMintedBox;
    private javax.swing.JCheckBox buildversionCheckbox;
    private javax.swing.JCheckBox bytesRecBox;
    private javax.swing.JCheckBox bytesSentBox;
    private javax.swing.JPanel chartsOptionsPanel;
    private javax.swing.JSplitPane chartsSplitPane;
    private javax.swing.JTree chartsTree;
    private javax.swing.JPanel chartsUIPanel;
    private javax.swing.JSplitPane chartsUISplitpane;
    private javax.swing.JCheckBox cpu_tempBox;
    private javax.swing.JCheckBox cpu_usageBox;
    protected javax.swing.JButton createChartButton;
    private javax.swing.JCheckBox crosshairsCheckbox;
    private javax.swing.JButton deselectAllButton;
    private javax.swing.JCheckBox doge2QortBox;
    private javax.swing.JCheckBox efficiencyBox;
    private javax.swing.JCheckBox interpolateCheckbox;
    private javax.swing.JSeparator jSeparator10;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JSeparator jSeparator9;
    private javax.swing.JCheckBox levelCheckbox;
    private javax.swing.JButton levelProjectionButton;
    private javax.swing.JSeparator levelingSeperator;
    private javax.swing.JCheckBox ltc2Qortbox;
    private javax.swing.JCheckBox mintingRateBox;
    protected javax.swing.JCheckBox movingAverageBox;
    private javax.swing.JCheckBox myBlockHeightBox;
    private javax.swing.JCheckBox numberOfConnectionsBox;
    private javax.swing.JCheckBox qort2DogeBox;
    private javax.swing.JCheckBox qort2LtcBox;
    private javax.swing.JCheckBox qortalRamBox;
    private javax.swing.JCheckBox ramUsageBox;
    private javax.swing.JCheckBox recPerDayBox;
    private javax.swing.JCheckBox recPerHourBox;
    private javax.swing.JCheckBox recPerMinBox;
    private javax.swing.JButton selectAllButton;
    private javax.swing.JCheckBox sentPerDayBox;
    private javax.swing.JCheckBox sentPerHourBox;
    private javax.swing.JCheckBox sentPerMinBox;
    protected javax.swing.JCheckBox showDataBox;
    private javax.swing.JCheckBox showDialogCheckbox;
    private javax.swing.JCheckBox showSnapshotsBox;
    private javax.swing.JCheckBox uptimeBox;
    // End of variables declaration//GEN-END:variables
}
