package reqorder;

import java.sql.Connection;
import java.util.ArrayList;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;

public class BalanceAlertsPanel extends javax.swing.JPanel
{
    GUI gui;
    DatabaseManager dbManager;
    DefaultListModel dbListModel;
    DefaultListModel addressesListModel;
    String selectedDb;

    public BalanceAlertsPanel()
    {
        initComponents();
    }
    
    public void Intitialise(GUI gui, DatabaseManager dbManager)
    {
        this.gui = gui;
        this.dbManager = dbManager;
        dbListModel = (DefaultListModel) databasesList.getModel();
        addressesListModel = (DefaultListModel) addressesList.getModel();

        dbManager.GetDbFiles().stream().map(file -> file).filter(dbName -> 
                !(dbName.equals("properties"))).forEachOrdered(dbName ->
        {
            dbListModel.addElement(dbName);
        });    

        databasesList.addListSelectionListener((javax.swing.event.ListSelectionEvent event) ->
        {
            //Don't trigger event for the item that was deselected
            if (event.getValueIsAdjusting())
            {
                return;
            }
            DbSelected();
        });   
        
         addressesList.addListSelectionListener((javax.swing.event.ListSelectionEvent event) ->
        {
            //Don't trigger event for the item that was deselected
            if (event.getValueIsAdjusting())
            {
                return;
            }
            AddressSelected();
        });            
    }
    
    protected void RepopulateDbList()
    {
        dbListModel.clear();
        
        dbManager.GetDbFiles().stream().map(file -> file).filter(dbName -> 
                !(dbName.equals("properties"))).forEachOrdered(dbName ->
        {
            dbListModel.addElement(dbName);
        });    
    }
    
    private void DbSelected()
    {
        try (Connection connection = ConnectionDB.getConnection( databasesList.getSelectedValue()))
        {
            addressesListModel.clear();
            
            //reset address components on selection of other db
            if(!databasesList.getSelectedValue().equals(selectedDb))
            {     
                infoLabel.setText(Main.BUNDLE.getString("balanceAlertsInfoLabel"));
                saveAlertButton.setEnabled(false);
                balanceSpinner.setEnabled(false);
                balanceSpinner.setValue(0);
                aboveRadio.setEnabled(false);
                belowRadio.setEnabled(false);
            }
            
            selectedDb = databasesList.getSelectedValue();
            
            if(!dbManager.TableExists("my_watchlist", connection))
                return;            
                        
            ArrayList<Object> addresses = dbManager.GetColumn("my_watchlist", "address", "", "", connection);
            
            addresses.forEach(address ->
            {
                String name = (String)dbManager.GetItemValue("my_watchlist", "name", "address", Utilities.SingleQuotedString(address.toString()), connection);
                if(name.isEmpty())
                    addressesListModel.addElement(address);
                else
                    addressesListModel.addElement(name);
            });
            connection.close();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }    
    
    private void AddressSelected()
    {
        //selecting a database will trigger a valuechanged event on addressesList, which will return null
        if(addressesList.getSelectedValue() == null)
            return;
        
        try (Connection connection = ConnectionDB.getConnection( databasesList.getSelectedValue()))
        {
            int ID = dbManager.GetAddressID(connection, addressesList.getSelectedValue());
            String table = "WL_ADDRESS_" + ID + "_BALANCE";
            if(dbManager.TableExists(table, connection))
            {
                ArrayList<Object> timestamps = dbManager.GetColumn(table, "timestamp", "timestamp", "desc", connection);
                if(timestamps.isEmpty())
                {
                    String[] split = Main.BUNDLE.getString("noReqordedBalance").split("%%");
                    infoLabel.setText(String.format(split[0] + "%s" + split[1] + "%s" + split[2], 
                            addressesList.getSelectedValue(),databasesList.getSelectedValue()));
                    balanceSpinner.setValue(0);                    
                }
                else
                {
                    long timestamp = (long)timestamps.get(0);
                    double balance = (double)dbManager.GetItemValue(table, "balance", "timestamp", String.valueOf(timestamp), connection);
                    String[] split = Main.BUNDLE.getString("reqordedBalance").split("%%");
                    infoLabel.setText(String.format(split[0] + "%s" + split[1] + "%.5f" + split[2], addressesList.getSelectedValue(),balance));
                    balanceSpinner.setValue(balance);
                    
                }
                double alertValue = (double)dbManager.GetItemValue("my_watchlist", "alertvalue", "id", String.valueOf(ID), connection);
                //if no alertvalue, show last known balance as set above
                if(Math.abs(alertValue) > 0)
                {                    
                    balanceSpinner.setValue(Math.abs(alertValue));
                    aboveRadio.setSelected(alertValue >= 0);
                    belowRadio.setSelected(alertValue < 0);
                }
                
                aboveRadio.setEnabled(true);
                belowRadio.setEnabled(true);
                saveAlertButton.setEnabled(true);
                balanceSpinner.setEnabled(true);
            }
            else
            {
                String[] split = Main.BUNDLE.getString("noBalanceData").split("%%");
                infoLabel.setText(String.format(split[0] + "%s" + split[1] + "%s" + split[2], 
                        addressesList.getSelectedValue(),databasesList.getSelectedValue()));
                aboveRadio.setEnabled(false);
                belowRadio.setEnabled(false);
                saveAlertButton.setEnabled(false);
                balanceSpinner.setEnabled(false);
            }
            
            connection.close();
        }
        catch (Exception e)
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

        mainSplitpane = new javax.swing.JSplitPane();
        databasesScrollpane = new javax.swing.JScrollPane();
        databasesList = new javax.swing.JList(new DefaultListModel());
        jSplitPane1 = new javax.swing.JSplitPane();
        optionsPanel = new javax.swing.JPanel();
        balanceSpinner = new javax.swing.JSpinner();
        balanceAlertsLabel = new javax.swing.JLabel();
        saveAlertButton = new javax.swing.JButton();
        aboveRadio = new javax.swing.JRadioButton();
        belowRadio = new javax.swing.JRadioButton();
        jLabel2 = new javax.swing.JLabel();
        infoLabel = new javax.swing.JLabel();
        addressesScrollpane = new javax.swing.JScrollPane();

        addressesScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        addressesList = new javax.swing.JList(new DefaultListModel());

        mainSplitpane.setDividerLocation(150);

        databasesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        databasesScrollpane.setViewportView(databasesList);

        mainSplitpane.setLeftComponent(databasesScrollpane);

        jSplitPane1.setDividerLocation(150);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        optionsPanel.setLayout(new java.awt.GridBagLayout());

        balanceSpinner.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 0.1d));
        balanceSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.ipadx = 50;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        optionsPanel.add(balanceSpinner, gridBagConstraints);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        balanceAlertsLabel.setText(bundle.getString("balanceAlertsLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridheight = 4;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        optionsPanel.add(balanceAlertsLabel, gridBagConstraints);

        saveAlertButton.setText(bundle.getString("saveAlertButton")); // NOI18N
        saveAlertButton.setEnabled(false);
        saveAlertButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveAlertButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 5, 0);
        optionsPanel.add(saveAlertButton, gridBagConstraints);

        aboveRadio.setSelected(true);
        aboveRadio.setText(bundle.getString("aboveRadio")); // NOI18N
        aboveRadio.setEnabled(false);
        aboveRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                aboveRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        optionsPanel.add(aboveRadio, gridBagConstraints);

        belowRadio.setText(bundle.getString("belowRadio")); // NOI18N
        belowRadio.setEnabled(false);
        belowRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                belowRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        optionsPanel.add(belowRadio, gridBagConstraints);

        jLabel2.setText(bundle.getString("qortLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 10);
        optionsPanel.add(jLabel2, gridBagConstraints);

        infoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        infoLabel.setText(bundle.getString("balanceAlertsInfoLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 15, 0);
        optionsPanel.add(infoLabel, gridBagConstraints);

        jSplitPane1.setLeftComponent(optionsPanel);

        addressesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        addressesScrollpane.setViewportView(addressesList);

        jSplitPane1.setRightComponent(addressesScrollpane);

        mainSplitpane.setRightComponent(jSplitPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainSplitpane, javax.swing.GroupLayout.DEFAULT_SIZE, 893, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainSplitpane, javax.swing.GroupLayout.DEFAULT_SIZE, 518, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void aboveRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_aboveRadioActionPerformed
    {//GEN-HEADEREND:event_aboveRadioActionPerformed
        belowRadio.setSelected(false);
    }//GEN-LAST:event_aboveRadioActionPerformed

    private void belowRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_belowRadioActionPerformed
    {//GEN-HEADEREND:event_belowRadioActionPerformed
        aboveRadio.setSelected(false);
    }//GEN-LAST:event_belowRadioActionPerformed

    private void saveAlertButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveAlertButtonActionPerformed
    {//GEN-HEADEREND:event_saveAlertButtonActionPerformed
         try (Connection connection = ConnectionDB.getConnection( databasesList.getSelectedValue()))
        {
            int ID = dbManager.GetAddressID(connection, addressesList.getSelectedValue());
            
            dbManager.ChangeValue("my_watchlist", "alert", "true", "id", String.valueOf(ID),connection);
            double alertValue = (double) balanceSpinner.getValue();
            alertValue = aboveRadio.isSelected() ? alertValue : -alertValue;
            dbManager.ChangeValue("my_watchlist", "alertvalue", String.valueOf(alertValue), "id", String.valueOf(ID),connection);            
            connection.close();
            
            JOptionPane.showMessageDialog(saveAlertButton, 
                    Main.BUNDLE.getString("alertSaved"),
                    Main.BUNDLE.getString("success"), JOptionPane.PLAIN_MESSAGE);
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_saveAlertButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton aboveRadio;
    private javax.swing.JList<String> addressesList;
    private javax.swing.JScrollPane addressesScrollpane;
    private javax.swing.JLabel balanceAlertsLabel;
    private javax.swing.JSpinner balanceSpinner;
    private javax.swing.JRadioButton belowRadio;
    private javax.swing.JList<String> databasesList;
    private javax.swing.JScrollPane databasesScrollpane;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane mainSplitpane;
    private javax.swing.JPanel optionsPanel;
    private javax.swing.JButton saveAlertButton;
    // End of variables declaration//GEN-END:variables
}
