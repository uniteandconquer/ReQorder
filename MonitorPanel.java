package reqorder;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.swing.JOptionPane;
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
    private boolean mintingChecked = false;
    private int mintingAccount = 0;

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
            pricesNode,qortToUsdNode,usdToQortNode,qortToLtcNode,ltcToQortNode,
            qortToDogeNode,dogeToQortNode;    
    
    protected void CreateMonitorTree()
    {
        SetBlockChainFolder();
        
        monitorTreeModel = (DefaultTreeModel) monitorTree.getModel(); 
        root = (DefaultMutableTreeNode) monitorTreeModel.getRoot();
        
        statusRoot = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("coreStatus"),"status.png"));
        statusNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("statusDefault"));
        statusRoot.add(statusNode);
        root.add(statusRoot);
        
        nodeNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("node"),"node.png"));
        root.add(nodeNode);        
        syncNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("syncStatusDefault"));
        nodeNode.add(syncNode);
        blockheightNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("nodeHeightDefault"));
        nodeNode.add(blockheightNode);
        chainHeighNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("chainHeightDefault"));
        nodeNode.add(chainHeighNode);        
        if(blockChainFolder != null)
        {            
            blockchainNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("chainSizeDefault"));
            nodeNode.add(blockchainNode);
            spaceLeftNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("spaceLeftDefault"));
            nodeNode.add(spaceLeftNode);
        }
        uptimeNode = new DefaultMutableTreeNode("uptimeDefault");
        nodeNode.add(uptimeNode);
        buildversionNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("buildVersionDefault"));
        nodeNode.add(buildversionNode);        
        
        peers = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("peersDefault"),"peers.png"));
        root.add(peers);
        peersNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("peersDefault"));
        peers.add(peersNode);
        allMintersNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("allMintersDefault"));
        peers.add(allMintersNode);        
        knownPeersNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("allPeersDefault"));
        peers.add(knownPeersNode);     
        
        mintingNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("mintingAccNode"),"account.png"));
        root.add(mintingNode);
        mintingAccountNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("mintingAddressDefault"));
        mintingNode.add(mintingAccountNode);
        blocksMintedNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("blocksMintedDefault"));
        mintingNode.add(blocksMintedNode);
        balanceNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("balanceDefault"));
        mintingNode.add(balanceNode);
        levelNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("levelDefault"));
        mintingNode.add(levelNode);
        
        pricesNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("pricesNode"),"prices.png"));  
        root.add(pricesNode);
        qortToUsdNode = new DefaultMutableTreeNode("QORT to USD price");
        usdToQortNode = new DefaultMutableTreeNode("USD to QORT price");
        qortToLtcNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("q2litePriceDefault"));
        ltcToQortNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("lite2qPriceDefault"));
        qortToDogeNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("q2dogePriceDefault"));
        dogeToQortNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("doge2qPriceDefault"));
        pricesNode.add(qortToUsdNode);
        pricesNode.add(usdToQortNode);
        pricesNode.add(qortToLtcNode);
        pricesNode.add(ltcToQortNode);
        pricesNode.add(qortToDogeNode);
        pricesNode.add(dogeToQortNode);
        
        dataNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("dataUsageNode"),"data.png"));
        root.add(dataNode);
        dataUsageNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("totalUsageDefault"));
        averageRateNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("avgPerDayDefault"));
        minuteRateNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("ratePerMinuteDefault"));
        hourRateNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("ratePerHourDefault"));
        dayRateNode = new DefaultMutableTreeNode(Main.BUNDLE.getString("ratePerDayDefault"));
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
        uptimeNode.setUserObject(Main.BUNDLE.getString("uptimeDefault"));
        syncNode.setUserObject(Main.BUNDLE.getString("syncStatusDefault"));
        blockheightNode.setUserObject(Main.BUNDLE.getString("nodeHeightDefault"));
        chainHeighNode.setUserObject(Main.BUNDLE.getString("chainHeightDefault"));
        buildversionNode.setUserObject(Main.BUNDLE.getString("buildVersionDefault"));
        peersNode .setUserObject(Main.BUNDLE.getString("peersDefault"));
        allMintersNode.setUserObject(Main.BUNDLE.getString("allMintersDefault"));   
        knownPeersNode.setUserObject(Main.BUNDLE.getString("allPeersDefault"));  
        mintingAccountNode.setUserObject(Main.BUNDLE.getString("mintingAddressDefault"));
        blocksMintedNode.setUserObject(Main.BUNDLE.getString("blocksMintedDefault"));
        balanceNode.setUserObject(Main.BUNDLE.getString("balanceDefault"));
        levelNode.setUserObject(Main.BUNDLE.getString("levelDefault"));
        dataUsageNode.setUserObject(Main.BUNDLE.getString("totalUsageDefault"));
        averageRateNode.setUserObject(Main.BUNDLE.getString("avgPerDayDefault"));
        minuteRateNode.setUserObject(Main.BUNDLE.getString("ratePerMinuteDefault"));
        hourRateNode.setUserObject(Main.BUNDLE.getString("ratePerHourDefault"));
        dayRateNode.setUserObject(Main.BUNDLE.getString("ratePerDayDefault"));
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
                    refreshButton.setText(Main.BUNDLE.getString("refreshIn") + (10 - currentTick));
                }
                if (currentTick == 10) //allow refresh every 10 seconds
                {
                    refreshButton.setText(Main.BUNDLE.getString("refreshNow"));
                    refreshButton.setEnabled(true);
//                    if(!isSynced)//update every 9 seconds when time approximation is active
//                        RestartTimer();
                }
                
                //show node update status
                if (coreOnline)
                {    
                    String[] split = Main.BUNDLE.getString("pingLabel").split("%%");
                    pingLabel.setText(String.format(split[0] + "%s" + split[1] + "%d" + split[2],  
                                                    Utilities.TimeFormat(lastPingTime),
                                                    (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta))));
                            
                }
                else
                {
                    lastPingTime = lastPingTime == 0 ? System.currentTimeMillis() : lastPingTime;
                    
                    //show last online (if was online)
                    nodeStatusString = lastOnlineTime == 0 ? Main.BUNDLE.getString("lastRefresh") + Utilities.TimeFormat(lastPingTime)
                            : Main.BUNDLE.getString("lastOnline") + Utilities.DateFormat(lastOnlineTime);
                    
                    String[] split = Main.BUNDLE.getString("nextRefresh").split("%%");
                    nodeStatusString += String.format(split[0] + "%d" + split[1], (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta)));                   
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
                String jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/status");
                
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
                
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/info");
                jSONObject = new JSONObject(jsonString);
                uptime = jSONObject.getLong("uptime");
                buildVersion = jSONObject.getString("buildVersion");    

                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/peers/known");
                jSONArray = new JSONArray(jsonString);
                allKnownPeers = jSONArray.length();
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/online");
                if(jsonString == null)
                    mintersOnline = 0;
                else
                {
                    jSONArray = new JSONArray(jsonString);
                    mintersOnline = jSONArray.length();                      
                }              
                
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/mintingaccounts");
                jSONArray = new JSONArray(jsonString);
                //If there's no minting account set we'll get a nullpointer exception
                if(jSONArray.length() > 0)
                {
                    if(jSONArray.length() > 1 && !mintingChecked)
                        SetMintingAccount(jSONArray);
                    
                    jSONObject = jSONArray.getJSONObject(mintingAccount);
                    myMintingAddress = jSONObject.getString("mintingAccount");
                    myBalance =  Double.parseDouble(Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/balance/" + myMintingAddress));
                    jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/" + myMintingAddress);
                    jSONObject = new JSONObject(jsonString);
                    
                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),
                        Main.BUNDLE.getString("activeAccountDBM") + myMintingAddress);                    
                    monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),String.format(
                            Main.BUNDLE.getString("blocksMintedDBM") + "%s",NumberFormat.getIntegerInstance().format(jSONObject.getInt("blocksMinted"))));                    
                    monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),
                            Main.BUNDLE.getString("balanceDBM") + myBalance + " QORT");                              
                    monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),
                            Main.BUNDLE.getString("levelDBM") + jSONObject.getInt("level"));             
                }    
                else
                {          
                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),
                            Main.BUNDLE.getString("noAccountDBM"));  
                    monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),Main.BUNDLE.getString("blocksMintedDefault"));                    
                    monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),Main.BUNDLE.getString("balanceDefault"));                              
                    monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),Main.BUNDLE.getString("levelDefault"));                        
                }                
                
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()), Main.BUNDLE.getString("coreIsOnline"));
                monitorTreeModel.valueForPathChanged(new TreePath(peersNode.getPath()), 
                        Main.BUNDLE.getString("connectedPeers") + numberOfConnections);                
                monitorTreeModel.valueForPathChanged(new TreePath(uptimeNode.getPath()),
                        Main.BUNDLE.getString("uptimeTree") + Utilities.MillisToDayHrMin(uptime));                
                monitorTreeModel.valueForPathChanged(new TreePath(buildversionNode.getPath()), 
                        Main.BUNDLE.getString("buildVersionTree") + buildVersion);
                
                monitorTreeModel.valueForPathChanged(new TreePath(allMintersNode.getPath()),
                        Main.BUNDLE.getString("mintersOnline") + mintersOnline);
                monitorTreeModel.valueForPathChanged(new TreePath(knownPeersNode.getPath()),
                        Main.BUNDLE.getString("allKnownPeers") + allKnownPeers);

                int chainHeight = Utilities.FindChainHeight();

                if (myBlockHeight < chainHeight)
                {
                    if (isSynced)
                    {
                        syncStartTime = System.currentTimeMillis();
                        syncStartBlock = myBlockHeight;
                        isSynced = false;
                        nodeInfoUpdateDelta = 60;
                    }

                    monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),
                            Main.BUNDLE.getString("isSyncing"));   
                }
                else
                {
                    if (!isSynced)
                    {
                        isSynced = true;
                        nodeInfoUpdateDelta = 60;
                        monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),
                                Main.BUNDLE.getString("isSynced"));   
                    }
                }

                String heightString = chainHeight == 0 ? "N/A" : String.format("%s", NumberFormat.getIntegerInstance().format(chainHeight));// String.valueOf(chainHeight);
                if(heightString.equals("N/A"))
                    monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),
                            Main.BUNDLE.getString("noChainHeight"));   
                            
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
                        Main.BUNDLE.getString("estimatedTime") + Utilities.MillisToDayHrMinSec(syncTime) : Main.BUNDLE.getString("estimatingTime");
                //fail safe 
                estimateString = syncTime < 1000 ? Main.BUNDLE.getString("estimatingTime") : estimateString;

                 String blocksString = myBlockHeight < chainHeight
                        ? String.format(Main.BUNDLE.getString("nodeHeight") + "%s", NumberFormat.getIntegerInstance().format(myBlockHeight)) + "  |  "
                        + Main.BUNDLE.getString("blocksLeft") + NumberFormat.getIntegerInstance().format(chainHeight - myBlockHeight)  + " " + estimateString 
                        : String.format(Main.BUNDLE.getString("nodeHeight") + "%s",NumberFormat.getIntegerInstance().format(myBlockHeight));                   

                monitorTreeModel.valueForPathChanged(new TreePath(blockheightNode.getPath()),
                        blocksString);  
                          
                monitorTreeModel.valueForPathChanged(new TreePath(chainHeighNode.getPath()),
                        Main.BUNDLE.getString("chainHeight") + heightString);      
                    
                //must be set after synctime approximation
                lastPingTime = System.currentTimeMillis();                     

                RefreshDataNode();   

                //Using model.nodeChanged was causing arrayIndexOutOfBounds error for jTree, especially on the Pi4 (slower?)
                //Using model.valueForPathChanged seems to have solved this problem
                if(blockChainFolder != null)
                {
                    File folder = new File(blockChainFolder);
                    long size = Utilities.getDirectorySize(folder);
                    monitorTreeModel.valueForPathChanged(new TreePath(blockchainNode.getPath()), 
                            String.format(Main.BUNDLE.getString("blockChainSizeDBM") + "%sMb",
                                NumberFormat.getIntegerInstance().format(size / 1000000)));
                    monitorTreeModel.valueForPathChanged(new TreePath(spaceLeftNode.getPath()), 
                            String.format(Main.BUNDLE.getString("spaceLeftDBM") + "%sMb",
                                NumberFormat.getIntegerInstance().format(folder.getFreeSpace() / 1000000)));              
                }  
            }
            catch(ConnectException e)
            {
                coreOnline = false;
                pricesButton.setEnabled(false);
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()),
                        Utilities.AllignCenterHTML(Main.BUNDLE.getString("cannotConnectMp")));   
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
    
    private void SetMintingAccount(JSONArray jsonArray)
    {
        mintingChecked = true;
        
        String[] accounts = new String[jsonArray.length()];
        for(int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jso = jsonArray.getJSONObject(i);
            accounts[i] = jso.getString("mintingAccount");
        }        
        
        String choice = (String)JOptionPane.showInputDialog(this, 
                Utilities.AllignCenterHTML(Main.BUNDLE.getString("multipleAccounts")),
                Main.BUNDLE.getString("multipleAccountTitle"), JOptionPane.QUESTION_MESSAGE, null, accounts, accounts[0]); 
        
        for(int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jso = jsonArray.getJSONObject(i);
            if(jso.getString("mintingAccount").equals(choice))
            {
                mintingAccount = i;     
                break;
            }
        }        
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

            String[] split = Main.BUNDLE.getString("totalUsage").split("%%");
            monitorTreeModel.valueForPathChanged(new TreePath(dataUsageNode.getPath()),
                    String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) totalBytesReceived / 1000000), ((double) totalBytesSent / 1000000)));
            
            split = Main.BUNDLE.getString("avgPerDay").split("%%");
            monitorTreeModel.valueForPathChanged(new TreePath(averageRateNode.getPath()),
                    String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double)  averageReceived / 1000000), ((double) averageSent / 1000000)));

            split = Main.BUNDLE.getString("ratePerMinute").split("%%");
            monitorTreeModel.valueForPathChanged(new TreePath(minuteRateNode.getPath()),
                    String.format( split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) (receivedPerSec * 60) / 1000000), ((double) (sentPerSec * 60) / 1000000)));

            split = Main.BUNDLE.getString("ratePerHour").split("%%");
            monitorTreeModel.valueForPathChanged(new TreePath(hourRateNode.getPath()),
                    String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) (receivedPerSec * 3600) / 1000000), ((double) (sentPerSec * 3600) / 1000000)));

            split = Main.BUNDLE.getString("ratePerDay").split("%%");
            monitorTreeModel.valueForPathChanged(new TreePath(dayRateNode.getPath()),
                    String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
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

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        refreshButton.setText(bundle.getString("refreshButtonDefault")); // NOI18N
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

        pingLabel.setText(bundle.getString("pingLabelDefault")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        monitorPanel.add(pingLabel, gridBagConstraints);

        pricesButton.setText(bundle.getString("pricesButton")); // NOI18N
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
        monitorTreeModel.valueForPathChanged(new TreePath(qortToUsdNode.getPath()),
                String.format("Fetching QORT to USD price. Please wait..."));
        monitorTreeModel.reload(qortToUsdNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(usdToQortNode.getPath()),
                String.format("Fetching USD to QORT price. Please wait..."));
        monitorTreeModel.reload(usdToQortNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToLtcNode.getPath()),
                String.format(Main.BUNDLE.getString("fetchQ2Lite")));
        monitorTreeModel.reload(qortToLtcNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(ltcToQortNode.getPath()),
                String.format(Main.BUNDLE.getString("fetchLite2Q")));
        monitorTreeModel.reload(ltcToQortNode);
        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToDogeNode.getPath()),
                String.format(Main.BUNDLE.getString("fetchQ2Doge")));
        monitorTreeModel.reload(qortToDogeNode);  
        
        monitorTreeModel.valueForPathChanged(new TreePath(dogeToQortNode.getPath()),
                String.format(Main.BUNDLE.getString("fetchDoge2Q")));
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
                 long now = Instant.now().getEpochSecond();
            
                double LTC_USDprice = Double.NaN;
                String jsonString = Utilities.ReadStringFromURL(
                        "https://poloniex.com/public?command=returnChartData&currencyPair=USDC_LTC&start="
                        + (now - 3000) + "&end=9999999999&resolution=auto");
                
                if(jsonString != null)
                {
                    JSONArray pricesArray = new JSONArray(jsonString);
                   JSONObject lastObject = pricesArray.getJSONObject(pricesArray.length() - 1);
                   //will be 0 if result is invalid
                   if (lastObject.getLong("date") > 0)
                       LTC_USDprice = (double) lastObject.getDouble("weightedAverage");    
                }                           
                
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/LITECOIN?maxtrades=10");
                 double LTCprice = ((double)Long.parseLong(jsonString) / 100000000);
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/DOGECOIN?maxtrades=10");
                 double DogePrice = ((double) Long.parseLong(jsonString) / 100000000);
                
                double qortUsdPrice = Double.isNaN(LTC_USDprice) ? Double.NaN : LTC_USDprice * (1 / LTCprice);
                 
                 //update swing components in EDT
                 SwingUtilities.invokeLater(() ->
                 {
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToUsdNode.getPath()),
                            Double.isNaN(qortUsdPrice) ?  "Could not fetch USD data from poloniex.com" : 
                                    String.format("1 QORT = %.5f USD", ((double)qortUsdPrice)));
                    monitorTreeModel.reload(qortToUsdNode);
                    
                    monitorTreeModel.valueForPathChanged(new TreePath(usdToQortNode.getPath()),
                            Double.isNaN(qortUsdPrice) ?  "Please check your internet connection" : 
                                    String.format("1 USD = %.5f QORT", 1 / qortUsdPrice));
                    monitorTreeModel.reload(usdToQortNode);
                    
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
