package reqorder;

import java.awt.Component;
import java.awt.Font;
import java.awt.HeadlessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class AlertsPanel extends javax.swing.JPanel
{
    private GUI gui;
    private final DefaultListModel alertsListModel;

    public AlertsPanel()
    {
        initComponents();
        alertsListModel = (DefaultListModel) alertsList.getModel();
    }
    
    protected void Initialise(GUI gui)
    {
        this.gui = gui;
    }
    
     private void CheckBlockchainCheckbox(JCheckBox checkBox)
    {
        try(Connection connection = ConnectionDB.getConnection( "properties"))
        {
            if(!(boolean)gui.dbManager.TableExists("blockchain_folder", connection))
            {
                if(JOptionPane.showConfirmDialog(checkBox, 
                        "The blockchain folder location is not set, do you want to set it up now?", 
                        "Blockchainfolder not set", JOptionPane.YES_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION)
                {
                    gui.dbManager.SetBlockchainFolder();
                    //in case folder was not set
                    checkBox.setSelected((boolean)gui.dbManager.TableExists("blockchain_folder", connection));
                }
                else
                    checkBox.setSelected(false);
            } 
            else//when called through spinner statechange
                checkBox.setSelected(true);
                
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
     
     public void PopuateAlertsList()
    {
        //make sure refresh happens on EDT
        SwingUtilities.invokeLater(() ->
        {
            alertsListModel.removeAllElements();
            
            try (Connection connection = ConnectionDB.getConnection( "properties"))
            {
                //alerts table is created in dbmanager.PrepareEmail
                if(gui.dbManager.TableExists("alerts", connection))
                {
                    //fill alerts list with alerts stored in properties database
                    gui.dbManager.GetColumn("alerts", "timestamp", "timestamp","desc", connection).forEach(timestamp ->
                    {
                        //no recipient for GUI alerts
                        String itemString = "";
                        String messageString;
                        ArrayList<Object>  itemList = gui.dbManager.GetRow("alerts", "timestamp", String.valueOf(timestamp), connection);
                        itemString += itemList.get(2).toString() + "  |  at: ";//subject
                        itemString += Utilities.DateFormat((long)itemList.get(0));//time sent
                        messageString = itemList.get(3).toString();
                        alertsListModel.addElement(new AlertItem(itemString, messageString,(long)itemList.get(0),(boolean)itemList.get(4)));
                    });                    
                }
            }
            catch(Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        });                
    }
     
     protected void LoadPanel()
     {
          PopuateAlertsList();

         try (Connection connection = ConnectionDB.getConnection("properties"))
         {
             if (gui.dbManager.TableExists("alerts_settings", connection))
             {
                 //set components value according to values stored in properties database
                 long longValue = ((long) gui.dbManager.GetFirstItem("alerts_settings", "blockchainvalue", connection)) / 1000000000;
                 chainSizeAlertSpinner.setValue(longValue);
                 longValue = ((long) gui.dbManager.GetFirstItem("alerts_settings", "spaceleftvalue", connection)) / 1000000000;
                 spaceLeftSpinner.setValue(longValue);
                 long longPrice = Math.abs((long) gui.dbManager.GetFirstItem("alerts_settings", "ltcvalue", connection));
                 ltcAlertSpinner.setValue(((double) longPrice / 100000000));
                 longPrice = Math.abs((long) gui.dbManager.GetFirstItem("alerts_settings", "dogevalue", connection));
                 dogeAlertSpinner.setValue(((double) longPrice / 100000000));

                 JCheckBox checkBox;
                 for (Component c : alertsOptionsPanel.getComponents())
                 {
                     if (c instanceof JCheckBox)
                     {
                         checkBox = (JCheckBox) c;
                         checkBox.setSelected((boolean) gui.dbManager.GetFirstItem("alerts_settings", checkBox.getActionCommand(), connection));
                     }
                 }
                 statusAlertsBox.setEnabled(emailAlertsCheckbox.isSelected());
                 nodeInfoBox.setEnabled(statusAlertsBox.isSelected());
             }
             if((boolean)gui.dbManager.TableExists("blockchain_folder", connection))
             {
                 chainSizeAlertBox.setSelected((boolean)gui.dbManager.GetFirstItem("alerts_settings", "blockchainsize", connection));
                 chainSizeAlertSpinner.setValue((long)gui.dbManager.GetFirstItem("alerts_settings", "blockchainvalue", connection) / 1000000000);
                 spaceLeftAlertBox.setSelected((boolean)gui.dbManager.GetFirstItem("alerts_settings", "spaceleft", connection));
                 spaceLeftSpinner.setValue((long)gui.dbManager.GetFirstItem("alerts_settings", "spaceleftvalue", connection) / 1000000000);
             }
             connection.close();
         }
         catch (NullPointerException | SQLException e)
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

        balanceAlertsDialog = new javax.swing.JDialog();
        balanceAlertsDialog.setModal(true);
        balanceAlertsPanel = new reqorder.BalanceAlertsPanel();
        alertsMainSplitPane = new javax.swing.JSplitPane();
        alertsOptionsScrollpane = new javax.swing.JScrollPane();
        alertsOptionsScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        alertsOptionsPanel = new javax.swing.JPanel();
        goToSettingsBtn = new javax.swing.JButton();
        jSeparator12 = new javax.swing.JSeparator();
        deleteSelectedAlertBtn = new javax.swing.JButton();
        deleteAllAlertsBtn = new javax.swing.JButton();
        jSeparator13 = new javax.swing.JSeparator();
        reqordingHaltedBox = new javax.swing.JCheckBox();
        dogeAlertBox = new javax.swing.JCheckBox();
        levelUpdatesBox = new javax.swing.JCheckBox();
        coreUpdatesBox = new javax.swing.JCheckBox();
        mintingHaltedBox = new javax.swing.JCheckBox();
        chainSizeAlertBox = new javax.swing.JCheckBox();
        nameRegBox = new javax.swing.JCheckBox();
        ltcAlertBox = new javax.swing.JCheckBox();
        chainSizeAlertSpinner = new javax.swing.JSpinner();
        dogeAlertSpinner = new javax.swing.JSpinner();
        ltcAlertSpinner = new javax.swing.JSpinner();
        jLabel9 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        saveAlertsButton = new javax.swing.JButton();
        emailAlertsCheckbox = new javax.swing.JCheckBox();
        deleteReadButton = new javax.swing.JButton();
        balanceAlertsButton = new javax.swing.JButton();
        spaceLeftAlertBox = new javax.swing.JCheckBox();
        spaceLeftSpinner = new javax.swing.JSpinner();
        jLabel14 = new javax.swing.JLabel();
        nodeInfoBox = new javax.swing.JCheckBox();
        statusAlertsSpinner = new javax.swing.JSpinner();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        statusAlertsBox = new javax.swing.JCheckBox();
        outOfSyncBox = new javax.swing.JCheckBox();
        jSeparator14 = new javax.swing.JSeparator();
        dogeBelowRadio = new javax.swing.JRadioButton();
        dogeAboveRadio = new javax.swing.JRadioButton();
        ltcAboveRadio = new javax.swing.JRadioButton();
        ltcBelowRadio = new javax.swing.JRadioButton();
        jSeparator15 = new javax.swing.JSeparator();
        jSeparator16 = new javax.swing.JSeparator();
        jSeparator17 = new javax.swing.JSeparator();
        alertsSplitpane2 = new javax.swing.JSplitPane();
        alertsListPane = new javax.swing.JScrollPane();
        alertsListPane.getVerticalScrollBar().setUnitIncrement(10);
        alertsList = new javax.swing.JList(new DefaultListModel());
        alertsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() 
            {
                @Override
                public void valueChanged(javax.swing.event.ListSelectionEvent event)
                {
                    //Don't trigger event for the item that was deselected
                    if(event.getValueIsAdjusting())
                    return;
                    if(alertsList.getSelectedIndex() < 0)
                    return;

                    var ae = (reqorder.AlertItem) alertsListModel.get(alertsList.getSelectedIndex());
                    ae.read = true;                
                    alertsTextPane.setText(ae.message);
                    try(Connection connection = ConnectionDB.getConnection("properties"))
                    {
                        //opted not to check if is read, more code/sql queries without much gain
                        gui.dbManager.ChangeValue("alerts", "read", "true", "timestamp", String.valueOf(ae.timestamp), connection);
                        connection.close();
                    }
                    catch(Exception e)
                    {
                        BackgroundService.AppendLog(e);
                    }
                }
            });
            alertsTextScrollPane = new javax.swing.JScrollPane();
            alertsTextScrollPane.getVerticalScrollBar().setUnitIncrement(10);
            alertsTextPane = new javax.swing.JTextArea();

            balanceAlertsDialog.setTitle("Set balance alerts for active watchlists");

            javax.swing.GroupLayout balanceAlertsDialogLayout = new javax.swing.GroupLayout(balanceAlertsDialog.getContentPane());
            balanceAlertsDialog.getContentPane().setLayout(balanceAlertsDialogLayout);
            balanceAlertsDialogLayout.setHorizontalGroup(
                balanceAlertsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(balanceAlertsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            );
            balanceAlertsDialogLayout.setVerticalGroup(
                balanceAlertsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(balanceAlertsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            );

            alertsMainSplitPane.setDividerLocation(250);

            alertsOptionsScrollpane.setMinimumSize(new java.awt.Dimension(222, 16));

            alertsOptionsPanel.setMinimumSize(new java.awt.Dimension(222, 561));
            alertsOptionsPanel.setLayout(new java.awt.GridBagLayout());

            goToSettingsBtn.setText("Go to mail settings");
            goToSettingsBtn.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    goToSettingsBtnActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 4;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 2, 0);
            alertsOptionsPanel.add(goToSettingsBtn, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 6;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
            alertsOptionsPanel.add(jSeparator12, gridBagConstraints);

            deleteSelectedAlertBtn.setText("Delete selected");
            deleteSelectedAlertBtn.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    deleteSelectedAlertBtnActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridy = 1;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
            alertsOptionsPanel.add(deleteSelectedAlertBtn, gridBagConstraints);

            deleteAllAlertsBtn.setText("Delete all");
            deleteAllAlertsBtn.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    deleteAllAlertsBtnActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 2;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.ipadx = 30;
            gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
            alertsOptionsPanel.add(deleteAllAlertsBtn, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 19;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
            alertsOptionsPanel.add(jSeparator13, gridBagConstraints);

            reqordingHaltedBox.setText("ReQording halted");
            reqordingHaltedBox.setActionCommand("reqording");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 13;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(0, 30, 1, 0);
            alertsOptionsPanel.add(reqordingHaltedBox, gridBagConstraints);

            dogeAlertBox.setText("Doge price");
            dogeAlertBox.setActionCommand("dogeprice");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 24;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.gridheight = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(3, 30, 3, 0);
            alertsOptionsPanel.add(dogeAlertBox, gridBagConstraints);

            levelUpdatesBox.setText("Levelling updates");
            levelUpdatesBox.setActionCommand("levelling");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 17;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(1, 30, 1, 0);
            alertsOptionsPanel.add(levelUpdatesBox, gridBagConstraints);

            coreUpdatesBox.setText("Qortal core updates");
            coreUpdatesBox.setActionCommand("core_update");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 16;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(1, 30, 1, 0);
            alertsOptionsPanel.add(coreUpdatesBox, gridBagConstraints);

            mintingHaltedBox.setText("Minting halted");
            mintingHaltedBox.setActionCommand("minting");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 15;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(1, 30, 1, 0);
            alertsOptionsPanel.add(mintingHaltedBox, gridBagConstraints);

            chainSizeAlertBox.setText("Blockchain size");
            chainSizeAlertBox.setActionCommand("blockchainsize");
            chainSizeAlertBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    chainSizeAlertBoxActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 28;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(5, 30, 5, 0);
            alertsOptionsPanel.add(chainSizeAlertBox, gridBagConstraints);

            nameRegBox.setText("Name registration");
            nameRegBox.setActionCommand("name_reg");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 18;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(1, 30, 1, 0);
            alertsOptionsPanel.add(nameRegBox, gridBagConstraints);

            ltcAlertBox.setText("LTC price");
            ltcAlertBox.setActionCommand("ltcprice");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 20;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.gridheight = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(6, 30, 6, 0);
            alertsOptionsPanel.add(ltcAlertBox, gridBagConstraints);

            chainSizeAlertSpinner.setModel(new javax.swing.SpinnerNumberModel(Long.valueOf(30L), Long.valueOf(0L), Long.valueOf(1000L), Long.valueOf(1L)));
            chainSizeAlertSpinner.addChangeListener(new javax.swing.event.ChangeListener()
            {
                public void stateChanged(javax.swing.event.ChangeEvent evt)
                {
                    chainSizeAlertSpinnerStateChanged(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 29;
            gridBagConstraints.ipadx = 22;
            gridBagConstraints.insets = new java.awt.Insets(1, 32, 1, 0);
            alertsOptionsPanel.add(chainSizeAlertSpinner, gridBagConstraints);

            dogeAlertSpinner.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 0.1d));
            dogeAlertSpinner.addChangeListener(new javax.swing.event.ChangeListener()
            {
                public void stateChanged(javax.swing.event.ChangeEvent evt)
                {
                    dogeAlertSpinnerStateChanged(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 26;
            gridBagConstraints.ipadx = 40;
            gridBagConstraints.insets = new java.awt.Insets(1, 32, 5, 0);
            alertsOptionsPanel.add(dogeAlertSpinner, gridBagConstraints);

            ltcAlertSpinner.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 0.1d));
            ltcAlertSpinner.addChangeListener(new javax.swing.event.ChangeListener()
            {
                public void stateChanged(javax.swing.event.ChangeEvent evt)
                {
                    ltcAlertSpinnerStateChanged(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 22;
            gridBagConstraints.ipadx = 40;
            gridBagConstraints.insets = new java.awt.Insets(1, 32, 1, 0);
            alertsOptionsPanel.add(ltcAlertSpinner, gridBagConstraints);

            jLabel9.setText("GB");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 31;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
            alertsOptionsPanel.add(jLabel9, gridBagConstraints);

            jLabel12.setText("QORT");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 22;
            gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
            alertsOptionsPanel.add(jLabel12, gridBagConstraints);

            jLabel13.setText("QORT");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 26;
            gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
            alertsOptionsPanel.add(jLabel13, gridBagConstraints);

            saveAlertsButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
            saveAlertsButton.setText("Save alerts settings");
            saveAlertsButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    saveAlertsButtonActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 11;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 10, 0);
            alertsOptionsPanel.add(saveAlertsButton, gridBagConstraints);

            emailAlertsCheckbox.setText("Enable e-mail alerts");
            emailAlertsCheckbox.setActionCommand("emailalerts");
            emailAlertsCheckbox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    emailAlertsCheckboxActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 5;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(5, 30, 2, 0);
            alertsOptionsPanel.add(emailAlertsCheckbox, gridBagConstraints);

            deleteReadButton.setText("Delete read");
            deleteReadButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    deleteReadButtonActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.ipadx = 19;
            gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
            alertsOptionsPanel.add(deleteReadButton, gridBagConstraints);

            balanceAlertsButton.setText("Balance alerts");
            balanceAlertsButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    balanceAlertsButtonActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 12;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
            alertsOptionsPanel.add(balanceAlertsButton, gridBagConstraints);

            spaceLeftAlertBox.setText("Disk space left");
            spaceLeftAlertBox.setActionCommand("spaceleft");
            spaceLeftAlertBox.setAutoscrolls(true);
            spaceLeftAlertBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    spaceLeftAlertBoxActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 30;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(5, 30, 5, 0);
            alertsOptionsPanel.add(spaceLeftAlertBox, gridBagConstraints);

            spaceLeftSpinner.setModel(new javax.swing.SpinnerNumberModel(Long.valueOf(2L), Long.valueOf(0L), Long.valueOf(1000L), Long.valueOf(1L)));
            spaceLeftSpinner.addChangeListener(new javax.swing.event.ChangeListener()
            {
                public void stateChanged(javax.swing.event.ChangeEvent evt)
                {
                    spaceLeftSpinnerStateChanged(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 31;
            gridBagConstraints.ipadx = 22;
            gridBagConstraints.insets = new java.awt.Insets(0, 32, 10, 0);
            alertsOptionsPanel.add(spaceLeftSpinner, gridBagConstraints);

            jLabel14.setText("GB");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 29;
            alertsOptionsPanel.add(jLabel14, gridBagConstraints);

            nodeInfoBox.setText("Show node info");
            nodeInfoBox.setToolTipText("Enabling node info will add the current status of your Qortal node and active minting account to the status alert e-mails");
            nodeInfoBox.setActionCommand("shownodeinfo");
            nodeInfoBox.setEnabled(false);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 8;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(5, 30, 2, 0);
            alertsOptionsPanel.add(nodeInfoBox, gridBagConstraints);

            statusAlertsSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, 24, 1));
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 9;
            gridBagConstraints.ipadx = 22;
            gridBagConstraints.insets = new java.awt.Insets(5, 60, 5, 0);
            alertsOptionsPanel.add(statusAlertsSpinner, gridBagConstraints);

            jLabel15.setText("Every");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 9;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 60);
            alertsOptionsPanel.add(jLabel15, gridBagConstraints);

            jLabel16.setText("hours");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 2;
            gridBagConstraints.gridy = 9;
            alertsOptionsPanel.add(jLabel16, gridBagConstraints);

            statusAlertsBox.setText("Enable status alerts");
            statusAlertsBox.setToolTipText("Enabling status alerts will send you an e-mail with the current reqording status at the time iterval specified (between 1 and 24 hours)");
            statusAlertsBox.setActionCommand("statusalerts");
            statusAlertsBox.setEnabled(false);
            statusAlertsBox.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    statusAlertsBoxActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 7;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(5, 30, 2, 0);
            alertsOptionsPanel.add(statusAlertsBox, gridBagConstraints);

            outOfSyncBox.setText("Out of sync");
            outOfSyncBox.setToolTipText("ReQorder will send you an alert if the blockheight of your node is lagging 30 blocks or more behind the blockheight of the chain");
            outOfSyncBox.setActionCommand("out_of_sync");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 14;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
            gridBagConstraints.insets = new java.awt.Insets(1, 30, 1, 0);
            alertsOptionsPanel.add(outOfSyncBox, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 3;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
            alertsOptionsPanel.add(jSeparator14, gridBagConstraints);

            dogeBelowRadio.setText("below");
            dogeBelowRadio.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    dogeBelowRadioActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 25;
            gridBagConstraints.gridwidth = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
            alertsOptionsPanel.add(dogeBelowRadio, gridBagConstraints);

            dogeAboveRadio.setSelected(true);
            dogeAboveRadio.setText("above");
            dogeAboveRadio.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    dogeAboveRadioActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 24;
            gridBagConstraints.gridwidth = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
            alertsOptionsPanel.add(dogeAboveRadio, gridBagConstraints);

            ltcAboveRadio.setSelected(true);
            ltcAboveRadio.setText("above");
            ltcAboveRadio.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    ltcAboveRadioActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 20;
            gridBagConstraints.gridwidth = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
            alertsOptionsPanel.add(ltcAboveRadio, gridBagConstraints);

            ltcBelowRadio.setText("below");
            ltcBelowRadio.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    ltcBelowRadioActionPerformed(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 21;
            gridBagConstraints.gridwidth = 2;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
            alertsOptionsPanel.add(ltcBelowRadio, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 27;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
            alertsOptionsPanel.add(jSeparator15, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 10;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
            alertsOptionsPanel.add(jSeparator16, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 23;
            gridBagConstraints.gridwidth = 4;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
            alertsOptionsPanel.add(jSeparator17, gridBagConstraints);

            alertsOptionsScrollpane.setViewportView(alertsOptionsPanel);

            alertsMainSplitPane.setLeftComponent(alertsOptionsScrollpane);

            alertsSplitpane2.setDividerLocation(275);
            alertsSplitpane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

            alertsList.setCellRenderer(new MyListCellRenderer());
            alertsListPane.setViewportView(alertsList);

            alertsSplitpane2.setTopComponent(alertsListPane);

            alertsTextPane.setEditable(false);
            alertsTextPane.setColumns(20);
            alertsTextPane.setLineWrap(true);
            alertsTextPane.setRows(5);
            alertsTextPane.setWrapStyleWord(true);
            alertsTextPane.setMargin(new java.awt.Insets(20, 20, 20, 20));
            alertsTextScrollPane.setViewportView(alertsTextPane);

            alertsSplitpane2.setRightComponent(alertsTextScrollPane);

            alertsMainSplitPane.setRightComponent(alertsSplitpane2);

            javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
            this.setLayout(layout);
            layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(alertsMainSplitPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 730, Short.MAX_VALUE)
            );
            layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(alertsMainSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 506, Short.MAX_VALUE)
            );
        }// </editor-fold>//GEN-END:initComponents

    private void goToSettingsBtnActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_goToSettingsBtnActionPerformed
    {//GEN-HEADEREND:event_goToSettingsBtnActionPerformed
        gui.GoToSettings();
    }//GEN-LAST:event_goToSettingsBtnActionPerformed

    private void deleteSelectedAlertBtnActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteSelectedAlertBtnActionPerformed
    {//GEN-HEADEREND:event_deleteSelectedAlertBtnActionPerformed
        //can't delete by index, as the list array will be modified and indices will change
        //find the selected objects and delete them using the object reference
        var elements = new ArrayList<Object>();
        for (int index : alertsList.getSelectedIndices())
            elements.add(alertsListModel.get(index));

        if (elements.isEmpty())
            return;

        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            elements.forEach(o ->
            {
                AlertItem ae = (AlertItem) o;
                gui.dbManager.ExecuteUpdate("delete from alerts where timestamp = " + String.valueOf(ae.timestamp), connection);
                alertsListModel.removeElement(o);
            });
            alertsTextPane.setText("");
            if (alertsList.getSelectedIndex() != -1)
            {
                AlertItem ai = (AlertItem) alertsListModel.get(alertsList.getSelectedIndex());
                alertsTextPane.setText(ai.message);
            }
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_deleteSelectedAlertBtnActionPerformed

    private void deleteAllAlertsBtnActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteAllAlertsBtnActionPerformed
    {//GEN-HEADEREND:event_deleteAllAlertsBtnActionPerformed
        if (alertsListModel.toArray().length == 0)
            return;

        if ((JOptionPane.showConfirmDialog(deleteAllAlertsBtn, "Delete all alerts?", "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION))
        {
            var elements = alertsListModel.toArray();

            try (Connection connection = ConnectionDB.getConnection("properties"))
            {
                for (Object o : elements)
                {
                    AlertItem ae = (AlertItem) o;
                    gui.dbManager.ExecuteUpdate("delete from alerts where timestamp = " + String.valueOf(ae.timestamp), connection);
                    alertsListModel.removeElement(o);
                }
                alertsTextPane.setText("");
                if (alertsList.getSelectedIndex() != -1)
                {
                    AlertItem ai = (AlertItem) alertsListModel.get(alertsList.getSelectedIndex());
                    alertsTextPane.setText(ai.message);
                }
                connection.close();
            }
            catch (NullPointerException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }//GEN-LAST:event_deleteAllAlertsBtnActionPerformed

    private void chainSizeAlertBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_chainSizeAlertBoxActionPerformed
    {//GEN-HEADEREND:event_chainSizeAlertBoxActionPerformed
        //only check folder db table if box was just selected
        if (!chainSizeAlertBox.isSelected())
            return;

        CheckBlockchainCheckbox(chainSizeAlertBox);
    }//GEN-LAST:event_chainSizeAlertBoxActionPerformed

    private void chainSizeAlertSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_chainSizeAlertSpinnerStateChanged
    {//GEN-HEADEREND:event_chainSizeAlertSpinnerStateChanged
        if(!chainSizeAlertBox.isSelected())
            CheckBlockchainCheckbox(chainSizeAlertBox);
    }//GEN-LAST:event_chainSizeAlertSpinnerStateChanged

    private void dogeAlertSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_dogeAlertSpinnerStateChanged
    {//GEN-HEADEREND:event_dogeAlertSpinnerStateChanged
        if(!dogeAlertBox.isSelected())
        dogeAlertBox.setSelected(true);
    }//GEN-LAST:event_dogeAlertSpinnerStateChanged

    private void ltcAlertSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_ltcAlertSpinnerStateChanged
    {//GEN-HEADEREND:event_ltcAlertSpinnerStateChanged
        if(!ltcAlertBox.isSelected())
        ltcAlertBox.setSelected(true);
    }//GEN-LAST:event_ltcAlertSpinnerStateChanged

    private void saveAlertsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveAlertsButtonActionPerformed
    {//GEN-HEADEREND:event_saveAlertsButtonActionPerformed
        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            if (!gui.dbManager.TableExists("alerts_settings", connection))
                gui.dbManager.CreateAlertsSettingsTable(connection);

            gui.dbManager.ExecuteUpdate("delete from alerts_settings", connection);

            ArrayList<String> insertString = new ArrayList<>();
            insertString.add("alerts_settings");
            insertString.add("id");
            insertString.add("0");
            insertString.add("blockchainvalue");
            long longSize = (long) (((long) chainSizeAlertSpinner.getValue()) * 1000000000L);
            insertString.add(String.valueOf(longSize));
            insertString.add("spaceleftvalue");
            longSize = (long) (((long) spaceLeftSpinner.getValue()) * 1000000000L);
            insertString.add(String.valueOf(longSize));
            insertString.add("ltcvalue");
            long longPrice = Double.valueOf((double) ltcAlertSpinner.getValue() * 100000000).longValue();
            longPrice = ltcAboveRadio.isSelected() ? longPrice : longPrice * -1;
            insertString.add(String.valueOf(longPrice));
            insertString.add("dogevalue");
            longPrice = Double.valueOf((double) dogeAlertSpinner.getValue() * 100000000).longValue();
            longPrice = dogeAboveRadio.isSelected() ? longPrice : longPrice * -1;
            insertString.add(String.valueOf(longPrice));
            //setting the interval value if this alert is not enabled is not a problem
            insertString.add("statusinterval");
            insertString.add(String.valueOf(statusAlertsSpinner.getValue()));

            Component[] components = alertsOptionsPanel.getComponents();
            JCheckBox checkBox;
            for (Component c : components)
            {
                if (c instanceof JCheckBox)
                {
                    checkBox = (JCheckBox) c;
                    
                    insertString.add(checkBox.getActionCommand());
                    insertString.add(String.valueOf(checkBox.isSelected()));
                }
            }
            gui.dbManager.InsertIntoDB(insertString.toArray(new String[0]), connection);
            gui.dbManager.ResetAlertsSent(); 
            JOptionPane.showMessageDialog(saveAlertsButton, "Alerts settings saved", "Success", JOptionPane.PLAIN_MESSAGE);

            connection.close();

            gui.PopulateDatabasesTree();
        }
        catch (HeadlessException | NullPointerException | SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_saveAlertsButtonActionPerformed

    private void emailAlertsCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_emailAlertsCheckboxActionPerformed
    {//GEN-HEADEREND:event_emailAlertsCheckboxActionPerformed
       if (!emailAlertsCheckbox.isSelected())
        {
            statusAlertsBox.setSelected(false);
            statusAlertsBox.setEnabled(false);
            nodeInfoBox.setSelected(false);
            nodeInfoBox.setEnabled(false);
            return;
        }

        try (Connection connection = ConnectionDB.getConnection("properties"))
        {
            if (!gui.dbManager.TableExists("mail_server", connection))
            {
                JOptionPane.showMessageDialog(emailAlertsCheckbox,
                        "Mailserver settings must be initialised before enabling e-mail alerts", "Mailserver not set up", JOptionPane.PLAIN_MESSAGE);
                emailAlertsCheckbox.setSelected(false);
                statusAlertsBox.setEnabled(false);
                nodeInfoBox.setEnabled(false);
            }
            else
            {
                statusAlertsBox.setEnabled(true);
                
                if(gui.dbManager.TableExists("alerts_settings", connection))
                {
                    statusAlertsBox.setSelected((boolean)gui.dbManager.GetFirstItem("alerts_settings", "statusalerts", connection));
                    nodeInfoBox.setSelected((boolean)gui.dbManager.GetFirstItem("alerts_settings", "shownodeinfo", connection));  
                    nodeInfoBox.setEnabled(statusAlertsBox.isSelected());                       
                }           
            }
            connection.close();
        }
        catch (NullPointerException | SQLException e)
        {
            
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_emailAlertsCheckboxActionPerformed

    private void deleteReadButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteReadButtonActionPerformed
    {//GEN-HEADEREND:event_deleteReadButtonActionPerformed
        if (alertsListModel.toArray().length == 0)
            return;

        if ((JOptionPane.showConfirmDialog(deleteReadButton, "Delete all read alerts?", "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION))
        {
            //can't delete by index, as the list array will be modified and indices will change
            //find the selected objects and delete them using the object reference
            var itemsToDelete = new ArrayList<AlertItem>();
            for (Object object : alertsListModel.toArray())
            {
                AlertItem alertItem = (AlertItem) object;
                if (alertItem.read)
                    itemsToDelete.add(alertItem);
            }

            if (itemsToDelete.isEmpty())
                return;

            try (Connection connection = ConnectionDB.getConnection("properties"))
            {
                itemsToDelete.forEach(o ->
                {
                    AlertItem ai = (AlertItem) o;
                    gui.dbManager.ExecuteUpdate("delete from alerts where timestamp = " + String.valueOf(ai.timestamp), connection);
                    alertsListModel.removeElement(o);
                });
                alertsTextPane.setText("");
                if (alertsList.getSelectedIndex() != -1)
                {
                    AlertItem ai = (AlertItem) alertsListModel.get(alertsList.getSelectedIndex());
                    alertsTextPane.setText(ai.message);
                }

                connection.close();
            }
            catch (NullPointerException | SQLException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }//GEN-LAST:event_deleteReadButtonActionPerformed

    private void balanceAlertsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_balanceAlertsButtonActionPerformed
    {//GEN-HEADEREND:event_balanceAlertsButtonActionPerformed
        balanceAlertsPanel.RepopulateDbList();
        balanceAlertsDialog.pack();
        balanceAlertsDialog.setLocation(this.getX() + 25, this.getY() + 40);
        balanceAlertsDialog.setVisible(true);
    }//GEN-LAST:event_balanceAlertsButtonActionPerformed

    private void spaceLeftAlertBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_spaceLeftAlertBoxActionPerformed
    {//GEN-HEADEREND:event_spaceLeftAlertBoxActionPerformed
        //only check folder db table if box was just selected
        if (!spaceLeftAlertBox.isSelected())
            return;

        CheckBlockchainCheckbox(spaceLeftAlertBox);
    }//GEN-LAST:event_spaceLeftAlertBoxActionPerformed

    private void spaceLeftSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_spaceLeftSpinnerStateChanged
    {//GEN-HEADEREND:event_spaceLeftSpinnerStateChanged
        if(!spaceLeftAlertBox.isSelected())
            CheckBlockchainCheckbox(spaceLeftAlertBox);
    }//GEN-LAST:event_spaceLeftSpinnerStateChanged

    private void statusAlertsBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_statusAlertsBoxActionPerformed
    {//GEN-HEADEREND:event_statusAlertsBoxActionPerformed
        nodeInfoBox.setEnabled(statusAlertsBox.isSelected());
        //if statusalerts are disabled, deselect nodeinfo box
        nodeInfoBox.setSelected(statusAlertsBox.isSelected() ? nodeInfoBox.isSelected() : false); 
    }//GEN-LAST:event_statusAlertsBoxActionPerformed

    private void ltcAboveRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ltcAboveRadioActionPerformed
    {//GEN-HEADEREND:event_ltcAboveRadioActionPerformed
        ltcBelowRadio.setSelected(!ltcAboveRadio.isSelected());
    }//GEN-LAST:event_ltcAboveRadioActionPerformed

    private void ltcBelowRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ltcBelowRadioActionPerformed
    {//GEN-HEADEREND:event_ltcBelowRadioActionPerformed
        ltcAboveRadio.setSelected(!ltcBelowRadio.isSelected());
    }//GEN-LAST:event_ltcBelowRadioActionPerformed

    private void dogeAboveRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dogeAboveRadioActionPerformed
    {//GEN-HEADEREND:event_dogeAboveRadioActionPerformed
        dogeBelowRadio.setSelected(!dogeAboveRadio.isSelected());
    }//GEN-LAST:event_dogeAboveRadioActionPerformed

    private void dogeBelowRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dogeBelowRadioActionPerformed
    {//GEN-HEADEREND:event_dogeBelowRadioActionPerformed
        dogeAboveRadio.setSelected(!dogeBelowRadio.isSelected());
    }//GEN-LAST:event_dogeBelowRadioActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<String> alertsList;
    private javax.swing.JScrollPane alertsListPane;
    private javax.swing.JSplitPane alertsMainSplitPane;
    private javax.swing.JPanel alertsOptionsPanel;
    private javax.swing.JScrollPane alertsOptionsScrollpane;
    private javax.swing.JSplitPane alertsSplitpane2;
    private javax.swing.JTextArea alertsTextPane;
    private javax.swing.JScrollPane alertsTextScrollPane;
    private javax.swing.JButton balanceAlertsButton;
    protected javax.swing.JDialog balanceAlertsDialog;
    protected reqorder.BalanceAlertsPanel balanceAlertsPanel;
    private javax.swing.JCheckBox chainSizeAlertBox;
    private javax.swing.JSpinner chainSizeAlertSpinner;
    private javax.swing.JCheckBox coreUpdatesBox;
    private javax.swing.JButton deleteAllAlertsBtn;
    private javax.swing.JButton deleteReadButton;
    private javax.swing.JButton deleteSelectedAlertBtn;
    private javax.swing.JRadioButton dogeAboveRadio;
    protected javax.swing.JCheckBox dogeAlertBox;
    private javax.swing.JSpinner dogeAlertSpinner;
    private javax.swing.JRadioButton dogeBelowRadio;
    private javax.swing.JCheckBox emailAlertsCheckbox;
    private javax.swing.JButton goToSettingsBtn;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator13;
    private javax.swing.JSeparator jSeparator14;
    private javax.swing.JSeparator jSeparator15;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JSeparator jSeparator17;
    private javax.swing.JCheckBox levelUpdatesBox;
    private javax.swing.JRadioButton ltcAboveRadio;
    protected javax.swing.JCheckBox ltcAlertBox;
    private javax.swing.JSpinner ltcAlertSpinner;
    private javax.swing.JRadioButton ltcBelowRadio;
    private javax.swing.JCheckBox mintingHaltedBox;
    private javax.swing.JCheckBox nameRegBox;
    private javax.swing.JCheckBox nodeInfoBox;
    private javax.swing.JCheckBox outOfSyncBox;
    private javax.swing.JCheckBox reqordingHaltedBox;
    private javax.swing.JButton saveAlertsButton;
    private javax.swing.JCheckBox spaceLeftAlertBox;
    private javax.swing.JSpinner spaceLeftSpinner;
    private javax.swing.JCheckBox statusAlertsBox;
    private javax.swing.JSpinner statusAlertsSpinner;
    // End of variables declaration//GEN-END:variables

    public class MyListCellRenderer extends DefaultListCellRenderer
    {
        private  JLabel label;
        private final Font font = new Font("SansSerif",Font.PLAIN,13);
        private final Font boldFont = new Font("SansSerif",Font.BOLD,13);
        
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            AlertItem ae = (AlertItem) list.getModel().getElementAt(index);
            
            if (c instanceof JLabel)
            {   
                this.label = (JLabel) c;
                label.setText(value.toString());
                
                if(ae.read)
                    label.setFont(font);
                else
                    label.setFont(boldFont);       
            }

            return c;
        }
    }   
    
}
