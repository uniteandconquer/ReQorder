package reqorder;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.text.NumberFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

public class MonitorPanel extends javax.swing.JPanel
{
    private GUI gui;
    private DefaultTreeModel monitorTreeModel;
    private JSONObject jSONObject;
    private JSONArray jSONArray;
    protected Timer timer;
    private final int tick = 1000;
    private int nodeInfoUpdateDelta = 60;
    private int currentTick;
    private long lastOnlineTime;
    private long lastPingTime;
    private final SystemInfo systemInfo;
    private final List<NetworkIF> interfaces; 
    private long totalBytesSent = 0;
    private long totalBytesReceived = 0;   
    private long lastBytesSent;
    private long lastBytesReceived;
    private long lastUpdateTime;
    protected long startTime;
    private String nodeStatusString;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private int myBlockHeight;
    private long syncTime;
    protected boolean isSynced;
    private long syncStartTime;
    private int syncStartBlock;
    private boolean coreOnline;
    private boolean priceButtonReady = true;
    protected String blockChainFolder;

    public MonitorPanel()
    {
        initComponents();
        
        systemInfo = new SystemInfo();
        interfaces = systemInfo.getHardware().getNetworkIFs();

        for (NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            lastBytesSent += nif.getBytesSent();
            lastBytesReceived += nif.getBytesRecv();
        }     
    }
    
    protected void Initialise(GUI gui)
    { 
        this.gui = gui;
    }
    
    private void SetBlockChainFolder()
    {
        try(Connection c = ConnectionDB.getConnection("properties"))
        {
            if(gui.dbManager.TableExists("blockchain_folder", c))
                blockChainFolder = (String) gui.dbManager.GetFirstItem("blockchain_folder", "blockchain_folder", c);
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    private DefaultMutableTreeNode root,statusRoot, statusNode,nodeNode,peers,
            mintingNode,dataNode,uptimeNode,syncNode,blockheightNode,chainHeighNode,
            buildversionNode,blockchainNode,spaceLeftNode,peersNode,allMintersNode,
            knownPeersNode,mintingAccountNode,blocksMintedNode,balanceNode,levelNode,
            dataUsageNode,averageRateNode,minuteRateNode,hourRateNode,dayRateNode,
            pricesNode,qortToLtcNode,ltcToQortNode,qortToDogeNode,dogeToQortNode;    
    
    protected void CreateMonitorTree()
    {
        SetBlockChainFolder();
        
        monitorTreeModel = (DefaultTreeModel) monitorTree.getModel(); 
        root = (DefaultMutableTreeNode) monitorTreeModel.getRoot();
        
        statusRoot = new DefaultMutableTreeNode(new NodeInfo("Core Status","status.png"));
        statusNode = new DefaultMutableTreeNode("Status");
        statusRoot.add(statusNode);
        root.add(statusRoot);
        
        nodeNode = new DefaultMutableTreeNode(new NodeInfo("Node","node.png"));
        root.add(nodeNode);        
        syncNode = new DefaultMutableTreeNode("Synchronization Status");
        nodeNode.add(syncNode);
        blockheightNode = new DefaultMutableTreeNode("Blockheight (node)");
        nodeNode.add(blockheightNode);
        chainHeighNode = new DefaultMutableTreeNode("Blockheight (chain)");
        nodeNode.add(chainHeighNode);        
        if(blockChainFolder != null)
        {            
            blockchainNode = new DefaultMutableTreeNode("Blockchain size");
            nodeNode.add(blockchainNode);
            spaceLeftNode = new DefaultMutableTreeNode("Disk space left");
            nodeNode.add(spaceLeftNode);
        }
        uptimeNode = new DefaultMutableTreeNode("Uptime");
        nodeNode.add(uptimeNode);
        buildversionNode = new DefaultMutableTreeNode("Build version");
        nodeNode.add(buildversionNode);        
        
        peers = new DefaultMutableTreeNode(new NodeInfo("Peers","peers.png"));
        root.add(peers);
        peersNode = new DefaultMutableTreeNode("Peers");
        peers.add(peersNode);
        allMintersNode = new DefaultMutableTreeNode("All Online Minters");
        peers.add(allMintersNode);        
        knownPeersNode = new DefaultMutableTreeNode("All Known Peers");
        peers.add(knownPeersNode);     
        
        mintingNode = new DefaultMutableTreeNode(new NodeInfo("Minting Account","account.png"));
        root.add(mintingNode);
        mintingAccountNode = new DefaultMutableTreeNode("Minting Account Address");
        mintingNode.add(mintingAccountNode);
        blocksMintedNode = new DefaultMutableTreeNode("Blocks Minted");
        mintingNode.add(blocksMintedNode);
        balanceNode = new DefaultMutableTreeNode("Account Balance");
        mintingNode.add(balanceNode);
        levelNode = new DefaultMutableTreeNode("Account Level");
        mintingNode.add(levelNode);
        
        pricesNode = new DefaultMutableTreeNode(new NodeInfo("Prices (last 10 trades averaged)","prices.png"));  
        root.add(pricesNode);
        qortToLtcNode = new DefaultMutableTreeNode("QORT to Litecoin price");
        ltcToQortNode = new DefaultMutableTreeNode("Litecoin to QORT price");
        qortToDogeNode = new DefaultMutableTreeNode("QORT to Dogecoin price");
        dogeToQortNode = new DefaultMutableTreeNode("Dogecoin to QORT price");
        pricesNode.add(qortToLtcNode);
        pricesNode.add(ltcToQortNode);
        pricesNode.add(qortToDogeNode);
        pricesNode.add(dogeToQortNode);
        
        dataNode = new DefaultMutableTreeNode(new NodeInfo("Data Usage (System wide)","data.png"));
        root.add(dataNode);
        dataUsageNode = new DefaultMutableTreeNode("Total data usage");
        averageRateNode = new DefaultMutableTreeNode("Average data rate per day");
        minuteRateNode = new DefaultMutableTreeNode("Data rate per minute");
        hourRateNode = new DefaultMutableTreeNode("Data rate per hour");
        dayRateNode = new DefaultMutableTreeNode("Data rate per day");
        dataNode.add(dataUsageNode);   
        dataNode.add(averageRateNode);
        dataNode.add(minuteRateNode);
        dataNode.add(hourRateNode);
        dataNode.add(dayRateNode);        
        
        //collapse all but status node on tree creation
        monitorTreeModel.reload();
        gui.ExpandNode(monitorTree, statusRoot, 1);
    }
    
    private void ClearMonitorTree()
    {        
        uptimeNode.setUserObject("Uptime");
        syncNode.setUserObject("Synchronization Status");
        blockheightNode.setUserObject("Blockheight (node)");
        chainHeighNode.setUserObject("Blockheight (chain)");
        buildversionNode.setUserObject("Build version");
        peersNode .setUserObject("Peers");
        allMintersNode.setUserObject("All Online Minters");   
        knownPeersNode.setUserObject("All Known Peers");  
        mintingAccountNode.setUserObject("Minting Account Address");
        blocksMintedNode.setUserObject("Blocks Minted");
        balanceNode.setUserObject("Account Balance");
        levelNode.setUserObject("Account Level");
        dataUsageNode.setUserObject("Data Usage Info");
        dataUsageNode.setUserObject("Total data usage");
        averageRateNode.setUserObject("Average data rate per day");
        minuteRateNode.setUserObject("Data rate per minute");
        hourRateNode.setUserObject("Data rate per hour");
        dayRateNode.setUserObject("Data rate per day");
        monitorTreeModel.reload();
        gui.ExpandNode(monitorTree, statusRoot, 1);
    }
    
     protected void RestartTimer()
    {              
        //set this variable to avoid showing time left estimate too early
        syncStartTime = System.currentTimeMillis();
        currentTick = 0;
        refreshButton.setEnabled(false); //avoid button spamming

        if (timer == null)
        {
            timer = new Timer();
        }
        else
        {
            timer.cancel();
            timer = new Timer();
        }

        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {    
                if (currentTick % nodeInfoUpdateDelta == 0) //refresh every updateDelta
                {   
                    currentTick = 0;
                    refreshButton.setEnabled(false);
                    
                    //This code updates the GUI, but needs to run on a seperate thread due to the delay caused by
                    //ReadStringFromURL, updating swing from a seperate thread causes concurrency issues.
                    //invokeLater() makes sure the changes will occur on the Event Dispatch Tread 
                    SwingUtilities.invokeLater(() ->
                    {
                        RefreshNodeLabels();                     
                    });   
                }
                if (currentTick < 10)
                {
                    refreshButton.setText("Refresh in " + (10 - currentTick));
                }
                if (currentTick == 10) //allow refresh every 10 seconds
                {
                    refreshButton.setText("Refresh Now");
                    refreshButton.setEnabled(true);
//                    if(!isSynced)//update every 9 seconds when time approximation is active
//                        RestartTimer();
                }
                
                //show node update status
                if (coreOnline)
                {                                        
                    pingLabel.setText("Last refresh: " + Utilities.TimeFormat(lastPingTime)
                            + ", next refresh in " + (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta))
                            + " sec");
                }
                else
                {
                    //show last online (if was online)
                    nodeStatusString = lastOnlineTime == 0 ? "Last refresh: " + Utilities.TimeFormat(lastPingTime)
                            : "Last time online: " + Utilities.DateFormat(lastOnlineTime);
                    nodeStatusString += ". Next refresh in " + (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta)) + " sec";                    
                    pingLabel.setText(nodeStatusString); 
                }

                currentTick++;
            }
        }, 0, tick);
    }

    private void RefreshNodeLabels()
    {
//        ReadStringFromURL was causing hiccups in GUI timer, using seperate thread
        executor.execute(() ->
        {             
            try
            {
                //If ReadStringFromURL throws an error, coreOnline will be set to false
                String jsonString = Utilities.ReadStringFromURL("http://localhost:12391/admin/status");
                
                //only expand tree if status switched from offline to online, expand before setting coreOnline to true
                if(!coreOnline)
                    gui.ExpandTree(monitorTree, 1);
                coreOnline = true;          
                if(priceButtonReady)
                    pricesButton.setEnabled(true);
                
                lastOnlineTime = System.currentTimeMillis();
                
                //First we get all the variables form the Qortal API before we change the nodes in the GUI
                //this due to the time delay for API queries, we want to change the nodes only after all variables are fetched
                int numberOfConnections;
                long uptime;
                String buildVersion;
                int allKnownPeers;
                int mintersOnline;
                String myMintingAddress;
                double myBalance;
                
                //derived from admin/status
                jSONObject = new JSONObject(jsonString);
                myBlockHeight = jSONObject.getInt("height");
                numberOfConnections = jSONObject.getInt("numberOfConnections");
                
                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/admin/info");
                jSONObject = new JSONObject(jsonString);
                uptime = jSONObject.getLong("uptime");
                buildVersion = jSONObject.getString("buildVersion");    

                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/peers/known");
                jSONArray = new JSONArray(jsonString);
                allKnownPeers = jSONArray.length();
                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/online");
                if(jsonString == null)
                    mintersOnline = 0;
                else
                {
                    jSONArray = new JSONArray(jsonString);
                    mintersOnline = jSONArray.length();                      
                }              
                
                jsonString = Utilities.ReadStringFromURL("http://localhost:12391/admin/mintingaccounts");
                jSONArray = new JSONArray(jsonString);
                //If there's no minting account set we'll get a nullpointer exception
                if(jSONArray.length() > 0)
                {
                    jSONObject = jSONArray.getJSONObject(0);
                    myMintingAddress = jSONObject.getString("mintingAccount");
                    myBalance =  Double.parseDouble(Utilities.ReadStringFromURL("http://localhost:12391/addresses/balance/" + myMintingAddress));
                    jsonString = Utilities.ReadStringFromURL("http://localhost:12391/addresses/" + myMintingAddress);
                    jSONObject = new JSONObject(jsonString);

                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),
                        "Active minting account: " + myMintingAddress);                    
                    monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),String.format(
                            "Blocks minted: %s",NumberFormat.getIntegerInstance().format(jSONObject.getInt("blocksMinted"))));                    
                    monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),
                            "Balance: " + myBalance + " QORT");                              
                    monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),
                            "Level: " + jSONObject.getInt("level"));             
                }    
                else
                {          
                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),
                            "No Active Minting Account Found");                       
                }                
                
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()), "Qortal Core is Online");
                monitorTreeModel.valueForPathChanged(new TreePath(peersNode.getPath()), 
                        "Connected peers: " + numberOfConnections);                
                monitorTreeModel.valueForPathChanged(new TreePath(uptimeNode.getPath()),
                        "Uptime: " + Utilities.MillisToDayHrMin(uptime));                
                monitorTreeModel.valueForPathChanged(new TreePath(buildversionNode.getPath()), 
                        "Qortal core build version: " + buildVersion);
                
                monitorTreeModel.valueForPathChanged(new TreePath(allMintersNode.getPath()),
                        "Minters online: " + mintersOnline);
                monitorTreeModel.valueForPathChanged(new TreePath(knownPeersNode.getPath()),
                        "All known peers: " + allKnownPeers);

                int chainHeight = Utilities.FindChainHeight();

                if (myBlockHeight < chainHeight)
                {
                    if (isSynced)
                    {
                        syncStartTime = System.currentTimeMillis();
                        syncStartBlock = myBlockHeight;
                        isSynced = false;
                        nodeInfoUpdateDelta = 10;
                    }

                    monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),
                            "Core is Synchronizing");   
                }
                else
                {
                    if (!isSynced)
                    {
                        isSynced = true;
                        nodeInfoUpdateDelta = 60;
                        monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),
                                "Core is Synchronized");   
                    }
                }

                String heightString = chainHeight == 0 ? "N/A" : String.format("%s", NumberFormat.getIntegerInstance().format(chainHeight));// String.valueOf(chainHeight);
                if (myBlockHeight - syncStartBlock > 0)
                {
                    syncTime = ((System.currentTimeMillis() - syncStartTime) / (myBlockHeight - syncStartBlock)) * (chainHeight - myBlockHeight);
                }
                
                //FOR DEBUGGING SYNCTIME
//                System.out.println("ST = " + syncTime + " SSB = " + syncStartBlock + " , MBH = " + myBlockHeight);                
                
                //we want to start time left estimation only when 30 seconds or more have passed since we went out of sync
                //we need to give the algo some time to get a good estimate of blocks_synced per delta time, otherwise the figure
                //will be irrelevant and confusing to the user
                String estimateString = System.currentTimeMillis() - syncStartTime > 30000 ?
                        " | Estimated time left: " + Utilities.MillisToDayHrMinSec(syncTime) : " | Estimating time left";
                //fail safe 
                estimateString = syncTime < 1000 ? " | Estimating time left" : estimateString;

                 String blocksString = myBlockHeight < chainHeight
                        ? String.format("Blockheight node:  %s", NumberFormat.getIntegerInstance().format(myBlockHeight)) + "  |  "
                        + "Blocks left: " + (chainHeight - myBlockHeight) + estimateString
                        : String.format("Blockheight node:  %s",NumberFormat.getIntegerInstance().format(myBlockHeight));                   

                monitorTreeModel.valueForPathChanged(new TreePath(blockheightNode.getPath()),
                        blocksString);  
                          
                monitorTreeModel.valueForPathChanged(new TreePath(chainHeighNode.getPath()),
                        "Blockheight chain: " + heightString);      
                    
                //must be set after synctime approximation
                lastPingTime = System.currentTimeMillis();                     

                RefreshDataNode();   

                //Using model.nodeChanged was causing arrayIndexOutOfBounds error for jTree, especially on the Pi4 (slower?)
                //Using model.valueForPathChanged seems to have solved this problem
                if(blockChainFolder != null)
                {
                    File folder = new File(blockChainFolder);
                    long size = Utilities.getDirectorySize(folder);
                    monitorTreeModel.valueForPathChanged(new TreePath(blockchainNode.getPath()), String.format("Blockchain size: %sMb",
                            NumberFormat.getIntegerInstance().format(size / 1000000)));
                    monitorTreeModel.valueForPathChanged(new TreePath(spaceLeftNode.getPath()), String.format("Space left on disk: %sMb",
                            NumberFormat.getIntegerInstance().format(folder.getFreeSpace() / 1000000)));              
                }  
            }
            catch(ConnectException e)
            {
                coreOnline = false;
                pricesButton.setEnabled(false);
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()),
                        Utilities.AllignCenterHTML("<br/>Cannot connect to Qortal core<br/>"
                   + "Check if your core and/or SSH tunnel are running<br/><br/>"));   
                ClearMonitorTree();
            }
            catch (IOException | NumberFormatException | TimeoutException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }  
            
            //model.reload(node) was causing array index out of bounds error, select de-select will also reload the node
            TreePath selected = monitorTree.getSelectionPath();
            monitorTree.setSelectionInterval(0, monitorTree.getRowCount() - 1);
            monitorTree.setSelectionPath(selected);
            
        });   //end executor    
    }
    
    private void RefreshDataNode()
    {       
        long currentBytesSent = 0;
        long currentBytesReceived = 0;

        for (NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            currentBytesSent += nif.getBytesSent();
            currentBytesReceived += nif.getBytesRecv();
        }

        //FOR DEBUGGING
//        if(currentBytesReceived < lastBytesReceived || currentBytesSent < lastBytesSent)
//            System.out.println(String.format("INCONGRUENT DATA: cbs = %.2f , lbs = %.2f , cbr = %.2f , lbr = %.2f",
//                    (double)currentBytesSent/1000000,(double)lastBytesSent/1000000, (double)currentBytesReceived/1000000,(double)lastBytesReceived/1000000));
        
        
        //Current bytes sent should always be bigger than lastbytes sent
        //If, for some reason Oshi returns a faulty value for getBytes this would result in a negative value for 
        //bytesSent/Rec, which would result in a negative value for totalBytesSent/Rec as well as averageBytesSent/Rec
        long bytesSent = currentBytesSent > lastBytesSent ? currentBytesSent - lastBytesSent : lastBytesSent;
        long bytesReceived = currentBytesReceived > lastBytesReceived ? currentBytesReceived - lastBytesReceived : lastBytesReceived;
        totalBytesSent += bytesSent;
        totalBytesReceived += bytesReceived;
        
        //FOR DEBUGGING
//        System.out.println(String.format("bs = %.2f , br = %.2f , tbs = %.2f , tbr = %.2f , cbs = %.2f , cbr = %.2f , lbs = %.2f , lbr = %.2f", 
//                (double)bytesSent/1000000, (double)bytesReceived/1000000, (double)totalBytesSent/1000000, (double)totalBytesReceived/1000000,
//                 (double)currentBytesSent/1000000, (double)currentBytesReceived/1000000,(double)lastBytesSent/1000000,(double)lastBytesReceived/1000000));

        lastBytesSent = currentBytesSent;
        lastBytesReceived = currentBytesReceived;

        if (lastUpdateTime > 0)//cannot calculate rate on first update (will cause / by 0)
        {
            long timePassed = System.currentTimeMillis() - lastUpdateTime;
            timePassed = timePassed < 1000 ? 1000 : timePassed;//failsafe for / by 0
            long receivedPerSec = bytesReceived / (timePassed / 1000);
            long sentPerSec = bytesSent / (timePassed / 1000);
        
            long averageReceived = (totalBytesReceived / (System.currentTimeMillis() - startTime)) * 86400000;
            long averageSent = (totalBytesSent / (System.currentTimeMillis() - startTime)) * 86400000;

            monitorTreeModel.valueForPathChanged(new TreePath(dataUsageNode.getPath()),
                    String.format("Total Received: %.2fMb  |  Total Sent: %.2fMb",
                    ((double) totalBytesReceived / 1000000), ((double) totalBytesSent / 1000000)));
            
            monitorTreeModel.valueForPathChanged(new TreePath(averageRateNode.getPath()),
                    String.format("Average rate per day: Down %.2fMb  |  Up: %.2fMb",
                    ((double)  averageReceived / 1000000), ((double) averageSent / 1000000)));

            monitorTreeModel.valueForPathChanged(new TreePath(minuteRateNode.getPath()),
                    String.format( "Current rate per minute: Down: %.2fMb  |  Up: %.2fMb",
                    ((double) (receivedPerSec * 60) / 1000000), ((double) (sentPerSec * 60) / 1000000)));

            monitorTreeModel.valueForPathChanged(new TreePath(hourRateNode.getPath()),
                    String.format("Current rate per hour: Down: %.2fMb  |  Up: %.2fMb",
                    ((double) (receivedPerSec * 3600) / 1000000), ((double) (sentPerSec * 3600) / 1000000)));

            monitorTreeModel.valueForPathChanged(new TreePath(dayRateNode.getPath()),
                    String.format("Current rate per day: Down: %.2fMb  |  Up: %.2fMb",
                    ((double) (receivedPerSec * 86400) / 1000000), ((double) (sentPerSec * 86400) / 1000000)));
        }
        
        lastUpdateTime = System.currentTimeMillis();           
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

        monitorPanel = new javax.swing.JPanel();
        monitorTreeScrollPane = new javax.swing.JScrollPane();
        monitorTree = new javax.swing.JTree();
        refreshButton = new javax.swing.JButton();
        pingLabel = new javax.swing.JLabel();
        pricesButton = new javax.swing.JButton();

        monitorPanel.setLayout(new java.awt.GridBagLayout());

        monitorTree.setFont(new java.awt.Font("Serif", 0, 13)); // NOI18N
        monitorTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(new NodeInfo("Node Monitor","qortal.png"))));
        monitorTree.setCellRenderer(new NodeTreeCellRenderer());
        monitorTreeScrollPane.setViewportView(monitorTree);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        monitorPanel.add(monitorTreeScrollPane, gridBagConstraints);

        refreshButton.setText("Refresh Now");
        refreshButton.setMinimumSize(new java.awt.Dimension(125, 30));
        refreshButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 5, 8, 5);
        monitorPanel.add(refreshButton, gridBagConstraints);

        pingLabel.setText("Ping status");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        monitorPanel.add(pingLabel, gridBagConstraints);

        pricesButton.setText("Fetch prices");
        pricesButton.setEnabled(false);
        pricesButton.setMinimumSize(new java.awt.Dimension(125, 30));
        pricesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                pricesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 10, 8, 5);
        monitorPanel.add(pricesButton, gridBagConstraints);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 719, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(monitorPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 719, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 648, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(monitorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 648, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_refreshButtonActionPerformed
    {//GEN-HEADEREND:event_refreshButtonActionPerformed
        RestartTimer();
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void pricesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_pricesButtonActionPerformed
    {//GEN-HEADEREND:event_pricesButtonActionPerformed
        monitorTreeModel.valueForPathChanged(new TreePath(qortToLtcNode.getPath()),
                String.format("Fetching QORT to Litecoin price. Please wait..."));
        monitorTreeModel.reload(qortToLtcNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(ltcToQortNode.getPath()),
                String.format("Fetching Litecoin to QORT price. Please wait..."));
        monitorTreeModel.reload(ltcToQortNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToDogeNode.getPath()),
                String.format("Fetching QORT to Dogecoin price. Please wait..."));
        monitorTreeModel.reload(qortToDogeNode);  
        
        monitorTreeModel.valueForPathChanged(new TreePath(dogeToQortNode.getPath()),
                String.format("Fetching Dogecoin to QORT price. Please wait..."));
        monitorTreeModel.reload(dogeToQortNode);      
        
        pricesButton.setEnabled(false);
        priceButtonReady = false;
        Timer buttonTimer = new Timer();
        TimerTask buttonTask = new TimerTask()
        {
            @Override
            public void run()
            {
                priceButtonReady = true;
                pricesButton.setEnabled(true);
            }
        };
        buttonTimer.schedule(buttonTask, 120000);
        
        //Pinging for prices causes a delay, jamming the GUI, using a sepertate thread
        Thread thread = new Thread(() ->
        {
            try
            {
                 String jsonString = Utilities.ReadStringFromURL("http://localhost:12391/crosschain/price/LITECOIN?maxtrades=10");
                 double LTCprice = ((double)Long.parseLong(jsonString) / 100000000);
                 jsonString = Utilities.ReadStringFromURL("http://localhost:12391/crosschain/price/DOGECOIN?maxtrades=10");
                 double DogePrice = ((double) Long.parseLong(jsonString) / 100000000);
                 
                 //update swing components in EDT
                 SwingUtilities.invokeLater(() ->
                 {
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToLtcNode.getPath()),
                            String.format("1 QORT = %.5f Litecoin", ((double)1/LTCprice)));
                    monitorTreeModel.reload(qortToLtcNode);
                    
                    monitorTreeModel.valueForPathChanged(new TreePath(ltcToQortNode.getPath()),
                            String.format("1 Litecoin = %.5f QORT", LTCprice));
                    monitorTreeModel.reload(ltcToQortNode);
                    
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToDogeNode.getPath()),
                            String.format("1 QORT = %.5f Dogecoin", ((double) 1/DogePrice)));
                    monitorTreeModel.reload(qortToDogeNode);      
                    
                    monitorTreeModel.valueForPathChanged(new TreePath(dogeToQortNode.getPath()),
                            String.format("1 Dogecoin = %.5f QORT", DogePrice));
                    monitorTreeModel.reload(dogeToQortNode);                 
                 });
            }
            catch (IOException | NumberFormatException | TimeoutException e)
            {
                BackgroundService.AppendLog(e);
            }
        });
        thread.start();
    }//GEN-LAST:event_pricesButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel monitorPanel;
    private javax.swing.JTree monitorTree;
    private javax.swing.JScrollPane monitorTreeScrollPane;
    private javax.swing.JLabel pingLabel;
    private javax.swing.JButton pricesButton;
    private javax.swing.JButton refreshButton;
    // End of variables declaration//GEN-END:variables
}
