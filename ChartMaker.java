package reqorder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.time.Hour;
import org.jfree.data.time.MovingAverage;
import org.jfree.data.time.Second;
import org.jfree.data.time.Year;

public class ChartMaker extends ApplicationFrame implements ChartMouseListener
{
    private Crosshair xCrosshair;
    protected ChartPanel chartPanel;
    final Color[] colors;
    public ArrayList<TimeSeriesCollection> datasets;
    public final javax.swing.JLabel chartDialogLabel;
    protected final javax.swing.JDialog chartDialog;
    private final Point dialogSize = new Point(220,20);  
    public boolean showCrosshairs = true;
    public boolean showDialog = true;
    public boolean interpolateEnabled = true;
    public boolean movingAverageEnabled;
    public String chartTitle;
    private XYLineAndShapeRenderer[] linerenders;
    private XYStepRenderer[] stepRenderers;
    private TimeSeriesCollection maCollection;
    protected Crosshair maCrosshair;
    
    public ChartMaker(String title,GUI gui)
    {
        super(title);
        colors = new Color[]{Color.BLACK,Color.RED,Color.CYAN, Color.GREEN,Color.MAGENTA,Color.ORANGE,
            Color.PINK,Color.GRAY,Color.YELLOW,Color.BLUE,Color.WHITE,Color.CYAN,Color.ORANGE,Color.YELLOW};
        
        chartDialog = new javax.swing.JDialog(gui);
        chartDialog.setUndecorated(true);         
        
        chartDialogLabel = new javax.swing.JLabel();
        chartDialogLabel.setFont(new java.awt.Font("Dialog", 0, 11)); // NOI18N
        chartDialogLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        chartDialogLabel.setForeground(Color.LIGHT_GRAY);
        
        //only enable chartDialog transparancy for non linux systems
        //Linux rendering on transparent background is blurry to the point of unreadable
        if(gui.dbManager.myOS.contains("nix") || gui.dbManager.myOS.contains("nux"))
        {
            chartDialog.getRootPane ().setOpaque (true);
            chartDialog.getContentPane ().setBackground (new Color (30, 30, 30));
        }
        else
        {
            chartDialog.getRootPane ().setOpaque (false);
            chartDialog.getContentPane ().setBackground (new Color (0, 0, 0, 0));
            chartDialog.setBackground (new Color (30, 30, 30, 180));   
        }       
        
        javax.swing.GroupLayout chartDialogLayout = new javax.swing.GroupLayout(chartDialog.getContentPane());
        chartDialog.getContentPane().setLayout(chartDialogLayout);
        chartDialogLayout.setHorizontalGroup(
                chartDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(chartDialogLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, dialogSize.x, Short.MAX_VALUE)
        );
        chartDialogLayout.setVerticalGroup(
                chartDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(chartDialogLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, dialogSize.y, Short.MAX_VALUE)
        );
        chartDialog.pack();
    }    
    
    @Override
    public void chartMouseClicked(ChartMouseEvent event){}  
    
    @Override
    public void chartMouseMoved(ChartMouseEvent event)
    {
        //We always show the vertical crosshair
        JFreeChart chart = event.getChart();
        XYPlot plot = (XYPlot) chart.getPlot();
        ValueAxis xAxis = plot.getDomainAxis();
        double x = xAxis.java2DToValue(event.getTrigger().getX(), chartPanel.getScreenDataArea(),
                org.jfree.chart.ui.RectangleEdge.BOTTOM);
        this.xCrosshair.setValue(x);         
        //yCrosshairs values will be set in CrosshairOverlay.paintOverlay
        
        if(showDialog)
        {            
            SetDialogPosition(event.getTrigger().getLocationOnScreen(), event.getTrigger().getPoint(), 
                    event.getTrigger().getX(), event.getTrigger().getY());
        }        
    }    
    
    //Cant use mouseEvent or chartMouseEvent, we need this function to handle both types 
    //(reacting to move = chartMouseEvt, reacting to click = mouseEvt)
    private void SetDialogPosition(Point mousePos, Point pointOnChart, double x, double y)
    {            
           if (chartPanel.getScreenDataArea().contains(pointOnChart))
        {
            int dialogX;
            int dialogY;

            if (x - chartDialog.getWidth() - 20 < chartPanel.getScreenDataArea().getMinX())
            {
                dialogX = mousePos.x + 15;//dialog to the right of mousepointer
            }
            else            
            {
                dialogX = mousePos.x - dialogSize.x - 15;//dialog to the left of mousepointer
            }

            if (y - chartDialog.getHeight() - 20 < chartPanel.getScreenDataArea().getMinY())
            {
                dialogY = mousePos.y + 15; //dialog below mousepointer
            }
            else
            {
                dialogY = mousePos.y - chartDialog.getHeight() - 15;//dialog above mousepointer
            }
            chartDialog.setLocation(dialogX, dialogY);

            chartDialog.setVisible(true);
        }
        else
            chartDialog.setVisible(false);
    }
    
    public void SetInterpolation(boolean interpolate)
    {        
        if(chartPanel.getChart().getXYPlot().getRangeAxis(1).getLabel().equals("moving average"))
        {
            XYPlot plot = chartPanel.getChart().getXYPlot();       
            if(interpolate)
                plot.setRenderer(0, linerenders[0]);
            else
                plot.setRenderer(0,stepRenderers[0]);
            
            return;            
        }
        
        XYPlot plot = chartPanel.getChart().getXYPlot();
        for(int i = 0; i < linerenders.length;i++)
        {            
            if(interpolate)
                plot.setRenderer(i, linerenders[i]);
            else
                plot.setRenderer(i,stepRenderers[i]);
        }
    }
    
    public void SetMovingAverage(boolean isEnabled)
    {
        if(!chartPanel.getChart().getXYPlot().getRangeAxis(1).getLabel().equals("moving average"))
            return;
        
        if(isEnabled)
        {
            chartPanel.getChart().getXYPlot().setDataset(1,maCollection);
            chartPanel.getChart().getXYPlot().getRangeAxis(1).setVisible(true);
            maCrosshair.setVisible(true);
        }
        else
        {
            chartPanel.getChart().getXYPlot().setDataset(1,null);
            chartPanel.getChart().getXYPlot().getRangeAxis(1).setVisible(false);
            maCrosshair.setVisible(false);            
        }
    }
    
    private JFreeChart createChart(String title,ArrayList<ResultSet> resultSets,ArrayList<String> axes)
    {
        datasets = new ArrayList<>();
        
        //First range axis always uses first resultset
        XYDataset dataset1 = createDataset(axes.get(0), resultSets.get(0));
        
        title = dataset1 == null ? "NOT ENOUGH DATA TO PLOT CHART" : title;
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title,"Time of Day",axes.get(0),dataset1,true,true,false);
        
        chart.addSubtitle(new TextTitle("Mousewheel = zoom | Ctrl + mouse drag = pan\nLasso with mouse to select period"));
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);
        plot.setDomainPannable(true);
        plot.setRangePannable(true); 
        
        //needs to be numberaxis for the numberformatter (not valueaxis)
        NumberAxis firstYAxis = (NumberAxis) plot.getRangeAxis();
        if(AxisToInteger(firstYAxis.getLabel()))//check if integer needed from local method
            firstYAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        else
            firstYAxis.setNumberFormatOverride(GetDoubleFormat());        
        firstYAxis.setLabelPaint(colors[0]);
        firstYAxis.setTickLabelPaint(colors[0]);
        plot.setBackgroundPaint(Color.LIGHT_GRAY);
        plot.getDomainAxis().setTickLabelPaint(colors[0]);     
        
        //Create all line and step renderers, can be switched according to user interpolate preferences
        linerenders = new XYLineAndShapeRenderer[axes.size()];
        stepRenderers = new XYStepRenderer[axes.size()];
        
        //only show moving average if there is one rangeaxis
        if(axes.size() == 1 && dataset1 != null)
            CreateMovingAverageAxis(axes.get(0), dataset1, plot);
        
        CreateAxisRenderers(0);

        String firstAxis = axes.get(0);
        //if the first resultset is for level, levelling or buildversion we  enable the steprenderers
        boolean useStepRenderer = firstAxis.equals("level") || firstAxis.equals("buildversion") || firstAxis.equals("levelling");
        if(useStepRenderer || !interpolateEnabled)
        {     
            //steprenderer for all cases
            plot.setRenderer(0,stepRenderers[0]);                 
            //symbolaxis only for buildversion
            if(firstAxis.equals("buildversion"))
                CreateSymbolAxis(dataset1, resultSets, plot);
        }
        //otherwise we enable the linerenderers
        else
            plot.setRenderer(0,linerenders[0]);        

        //if resultsets.size > 1, all resultsets should only have one column
        int currentResultset = resultSets.size() > 1 ? 1 : 0;

        for(int i = 1; i < axes.size(); i++)
        {
            NumberAxis axis = new NumberAxis(axes.get(i));
            axis.setAutoRangeIncludesZero(false);
            if(AxisToInteger(axis.getLabel()))
                axis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            else
                axis.setNumberFormatOverride(GetDoubleFormat());

            plot.setRangeAxis(i, axis);
            plot.setRangeAxisLocation(i, 
                    i%2==0 ? AxisLocation.BOTTOM_OR_LEFT: AxisLocation.BOTTOM_OR_RIGHT);

            XYDataset dataset = createDataset(axes.get(i),resultSets.get(currentResultset));
            plot.setDataset(i, dataset);
            plot.mapDatasetToRangeAxis(i, i);

            CreateAxisRenderers(i);

            if(interpolateEnabled)
            {
                plot.setRenderer(i,linerenders[i]);                
            }
            else
            {
                plot.setRenderer(i,stepRenderers[i]);     
            }
//            plot.getRendererForDataset(plot.getDataset(i)).setSeriesPaint(0,colors[i]);
            axis.setLabelPaint(colors[i]);
            //due to timestamp being a long in milisecs in the dataset. Setting different value
            //in dataset will prevent coherent date formatting in crosshair and dialog labels, so we hide it
            if(axis.getLabel().equals("uptime"))
                axis.setTickLabelFont(new Font("SanSerif", Font.PLAIN, 0));
            axis.setTickLabelPaint(colors[i]);  

            //only move on to next resultset if there is more than one
            currentResultset = resultSets.size() > 1 ? currentResultset + 1 : 0;                    
        } 
        
        return chart;
    }  
    
    private NumberFormat GetDoubleFormat()
    {
        NumberFormat df = NumberFormat.getInstance();
        df.setMaximumFractionDigits(5);
        df.setMinimumFractionDigits(5);
        return  df;
    }
    
    private void CreateAxisRenderers(int rangeIndex)
    {  
        stepRenderers[rangeIndex] = new XYStepRenderer();
        stepRenderers[rangeIndex].setSeriesStroke(0, new BasicStroke(2.0f));
        stepRenderers[rangeIndex].setSeriesPaint(0, colors[rangeIndex]);
        stepRenderers[rangeIndex].setDefaultEntityRadius(6);
        linerenders[rangeIndex] = new XYLineAndShapeRenderer();
        linerenders[rangeIndex].setSeriesStroke(0, new BasicStroke(2f));
        linerenders[rangeIndex].setSeriesPaint(0, colors[rangeIndex]);
    }
    
    private void CreateMovingAverageAxis(String coupledAxis, XYDataset dataset,XYPlot plot)
    {
        NumberAxis axis = new NumberAxis("moving average");
        axis.setAutoRangeIncludesZero(false);
        if(AxisToInteger(coupledAxis))
            axis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        else
            axis.setNumberFormatOverride(GetDoubleFormat());

        plot.setRangeAxis(1, axis);
        plot.setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT);
        TimeSeries ts = CreateMovingAverageTimeSeries("", dataset);
        TimeSeries maDataset = MovingAverage.createMovingAverage(ts, "moving average", 49, 49);
        maCollection = new TimeSeriesCollection();
        maCollection.addSeries(maDataset);
        datasets.add(maCollection);        

        plot.setDataset(1, maCollection);
        plot.mapDatasetToRangeAxis(1, 1);
        axis.setLabelPaint(colors[1]);
        axis.setTickLabelPaint(colors[1]);  

        StandardXYItemRenderer maRenderer = new StandardXYItemRenderer();
        maRenderer.setSeriesStroke(0, new BasicStroke(4));
        maRenderer.setSeriesPaint(0, colors[1]);
        plot.setRenderer(1, maRenderer);
    }
    
    private boolean  AxisToInteger(String axis)
    {
        if(axis.endsWith("price"))
            return  false;
        if(axis.equals("cpu_temp"))
            return false;
        return !axis.equals("balance"); //return true if not balance, cpu_temp or endswith price
    }
    
    private void CreateSymbolAxis(XYDataset dataset,ArrayList<ResultSet> resultSets,XYPlot plot)
    {
        //create custom labels for each item tick on the range axis and set the symbolaxis
        String[] grade = new String[dataset.getItemCount(0) + 1];
        grade[0] = "";
        ResultSet rs = resultSets.get(0);
        int i = 1;
        try
        {
            rs.beforeFirst();
            while(rs.next())
            {
                grade[i] = rs.getString("buildversion");
                i++;
            }
            grade[grade.length - 1] = grade[grade.length - 2];
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        SymbolAxis rangeAxis = new SymbolAxis("", grade);
        rangeAxis.setTickUnit(new NumberTickUnit(1));
        rangeAxis.setRange(0, grade.length);
        plot.setRangeAxis(rangeAxis);
    }
        
    private double StringToDouble(String stringValue)
    {
        //we need to remove all non-numericals to insert into timeseries,
        //even though symbolaxis will not render the double but a custum value, the dataset requires a number
        stringValue = stringValue.replaceAll("[^\\d.]", "");
        stringValue = stringValue.replaceAll("[\\.]", "");
        stringValue = stringValue.substring(0,1) + "." + stringValue.substring(1,stringValue.length());
        return Double.parseDouble(stringValue); 
    }
    
    private XYDataset CreateLevellingDataset(String name, ResultSet resultSet)
    {
         try
        {     
            resultSet.next();
            int lowest = resultSet.getInt("blocksminted");
            long first = resultSet.getLong("timestamp");
            resultSet.last();
            int highest = resultSet.getInt("blocksminted");
            long last= resultSet.getLong("timestamp");
            
            int blocksMinted = highest - lowest;
            long timeTaken = last - first;
            
            if(blocksMinted < 5)
                return  null;
            
            TimeSeries series = new TimeSeries(name);
            long millisecPerBlock = ((long) timeTaken / blocksMinted);
            int[] levels = { 0, 7200, 72000 , 201600 , 374400 ,618400 , 964000 , 1482400 , 2173600 , 3037600 , 4074400 };
            RegularTimePeriod nextLevelTime = null;
            //find and set current level at current time
            int currentLevel = 0;
            for(int i = 0; i < levels.length; i++)
            {
                if(highest >= levels[i + 1])
                    continue;
                
                currentLevel = i;
                series.add(new Second(new Date(System.currentTimeMillis())),currentLevel);
                break;
            }
            //find and set all next levels
            for(int i = currentLevel; i < levels.length - 1; i++)
            {                
                int blocksTillNext = levels[i + 1] - highest;
                long projectedMilisec = last + (blocksTillNext * millisecPerBlock);
                
                nextLevelTime = new Second(new Date(projectedMilisec));
                series.addOrUpdate(nextLevelTime,i + 1);
            }  
            if(nextLevelTime != null)
            {
                //render 2 more years to level 10
                RegularTimePeriod lastItemTime = new Second(new Year(nextLevelTime.getEnd()).next().getEnd());
                series.addOrUpdate(lastItemTime,10);                
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private XYDataset CreateMintingDataset(String name, ResultSet resultSet)
    {
         try
        {     
            int lastBlocksMinted = -1;
            long lastTimestamp = -1;
            int blocksMinted;
            long timestamp;
            int blocksDelta;
            double timeQuotient;
            int mintingRate;
            TimeSeries series = new TimeSeries(name);
            RegularTimePeriod time;            
            
            while(resultSet.next())
            {
                if(lastBlocksMinted < 0)
                {
                    lastBlocksMinted = resultSet.getInt("blocksminted");
                    lastTimestamp = resultSet.getLong("timestamp");
                    continue;
                }
                
                blocksMinted = resultSet.getInt("blocksminted");
                timestamp = resultSet.getLong("timestamp");
                
                //dont't take measurements for intervals smaller than 2 minutes to avoid irregularities
                if(timestamp - lastTimestamp < 120000)
                    continue;
                
                blocksDelta = blocksMinted - lastBlocksMinted;
                timeQuotient = ((double) 3600000 / (timestamp - lastTimestamp));                 
                mintingRate = (int) (blocksDelta * timeQuotient);                
                time = new Second(new Date(lastTimestamp));
                
                series.addOrUpdate(time, mintingRate);
                
                lastBlocksMinted = blocksMinted;
                lastTimestamp = timestamp;                
            }
            
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;             
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private XYDataset CreateEfficiencyDataset(String name, ResultSet resultSet)
    {
         try
        {     
            int lastBlocksMinted = -1;
            long lastTimestamp = -1;
            int blocksMinted;
            long timestamp;
            int blocksDelta;
            double timeQuotient;
            double efficiency;
            TimeSeries series = new TimeSeries(name);
            RegularTimePeriod time;            
            
            while(resultSet.next())
            {
                if(lastBlocksMinted < 0)
                {
                    lastBlocksMinted = resultSet.getInt("blocksminted");
                    lastTimestamp = resultSet.getLong("timestamp");
                    continue;
                }
                
                blocksMinted = resultSet.getInt("blocksminted");
                timestamp = resultSet.getLong("timestamp");
                
                //dont't take measurements for intervals smaller than 15 minutes to 
                //even the chart out, minting data is volatile over short periods of time
                if(timestamp - lastTimestamp < 900000)
                    continue;
                
                blocksDelta = blocksMinted - lastBlocksMinted;
                timeQuotient = ((double) (timestamp - lastTimestamp)) / 60000;                 
                efficiency = (blocksDelta / timeQuotient) * 100;                
                time = new Second(new Date(lastTimestamp)); 
                
                if(Double.isInfinite(efficiency))//in case timeQoutient is zero for some reason
                    continue;
                
                series.addOrUpdate(time,efficiency );
                
                lastBlocksMinted = blocksMinted;
                lastTimestamp = timestamp;                
            }
            
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;             
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private XYDataset CreateBalanceDeltaDataset(String name, ResultSet resultSet)
    {
         try
        {    
            double lastBalance = -1;
            long lastTimestamp = -1;
            double balance;
            long timestamp;
            double balanceDelta;
            double timeQuotient;
            double balanceRate;
            TimeSeries series = new TimeSeries(name);
            RegularTimePeriod time;            
            
            while(resultSet.next())
            {
                if(lastBalance < 0)
                {
                    lastBalance = resultSet.getDouble("balance");
                    lastTimestamp = resultSet.getLong("timestamp");
                    continue;
                }
                
                balance = resultSet.getDouble("balance");
                timestamp = resultSet.getLong("timestamp");
                
                //dont't take measurements for intervals smaller than 1 minutes to avoid irregularities
                if(timestamp - lastTimestamp < 60000)
                    continue;
                
                balanceDelta = balance - lastBalance;
                timeQuotient = ((double) 3600000 / (timestamp - lastTimestamp));                 
                balanceRate = balanceDelta * timeQuotient;        
                time = new Second(new Date(lastTimestamp));
                
                series.addOrUpdate(time, balanceRate);
                
                lastBalance = balance;
                lastTimestamp = timestamp;                
            }
            
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;                   
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private XYDataset CreateBandwidthDataset(String name, ResultSet resultSet)
    {
        try
        {      
            resultSet.beforeFirst();
            TimeSeries series = new TimeSeries(name);
            RegularTimePeriod time;
            long value = 0;  
            while(resultSet.next())
            {   
                //using second to make sure addOrUpdate will insert if time interval is smaller than 1 minute
                time = new Second(new Date(resultSet.getLong("timestamp")));
                
                switch(name)
                {
                    case "bytes_sent":
                        value = (long) resultSet.getObject("bytes_sent");
                        break;
                    case "bytes_received":
                        value = (long) resultSet.getObject("bytes_received");
                        break;
                    case "bytes_sent_avg_min":
                        value = (long) resultSet.getObject("avg_bytes_sent");
                        break;
                    case "bytes_sent_avg_hour":
                        value = (long) resultSet.getObject("avg_bytes_sent") * 60;
                        break;
                    case "bytes_sent_avg_day":
                        value = (long) resultSet.getObject("avg_bytes_sent") * 1440;
                        break;
                    case "bytes_rec_avg_min":
                        value = (long) resultSet.getObject("avg_bytes_received");
                        break;
                    case "bytes_rec_avg_hour":
                        value = (long) resultSet.getObject("avg_bytes_received") * 60;
                        break;
                    case "bytes_rec_avg_day":
                        value = (long) resultSet.getObject("avg_bytes_received") * 1440;
                        break;
                }
                //bytes to Mb
                double mbValue = (double) value;           
                series.addOrUpdate(time, mbValue / 1000000);
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private XYDataset createDataset(String name, ResultSet resultSet)
    {
        if(name.startsWith("bytes"))
            return CreateBandwidthDataset(name, resultSet);
        
        switch(name)
        {
            case "levelling":
                return CreateLevellingDataset(name, resultSet);
            case "mintingrate":
                return CreateMintingDataset(name, resultSet);
            case "balancedelta":
                return CreateBalanceDeltaDataset(name, resultSet);
            case "efficiency":
                return CreateEfficiencyDataset(name, resultSet);
        }
        
        try
        {      
            boolean switchPrice = name.startsWith("qort");
            //due to WL_ naming in database, using blocks instead of blocksminted
            name = name.equals("blocks") ? "blocksminted" : name;
            //replace box action command with db table name
            name = name.equals("ltc_to_qort_price") || name.equals("qort_to_ltc_price") ? "ltcprice" : name;
            name = name.equals("doge_to_qort_price") || name.equals("qort_to_doge_price") ? "dogeprice" : name;
            
            resultSet.beforeFirst();
            TimeSeries series = new TimeSeries(name);
            RegularTimePeriod time = null;
            Number value = null;  
            while(resultSet.next())
            {   
                //using second to make sure addOrUpdate will insert if time interval is smaller than 1 minute
                time = new Second(new Date(resultSet.getLong("timestamp")));
                
                //We need to adjust the value for buildversion as it is a string
                 if(name.equals("buildversion"))
                {
                    String stringValue = resultSet.getString(name);
                    value = StringToDouble(stringValue);//need this value for steprenderer adjust below (null check)
                    series.addOrUpdate(time,value);
                    continue;
                }
                else
                    value = (Number) resultSet.getObject(name);
                
                //We want the long stored in database as a double for price formatting on range axis
                if(name.endsWith("price"))
                {
                    double price = (long) value.doubleValue();
                    price = switchPrice ? 1 / (price / 100000000) : price / 100000000;
                    series.addOrUpdate(time, price);
                    continue;
                }  
                if(name.equals("blockchainsize") || name.equals("ram_usage"))//bytes to Mb
                {
                    value  = (long) value / 1000000;
                    series.addOrUpdate(time,value);
                    continue;
                }
                //for all other data type range axes                
                series.addOrUpdate(time, value);
            }
                
            //render another two hours to the top of the step renderer
            //otherwise crosshair/chart will not clearly show latest value
            if(name.equals("level") || name.equals("buildversion"))
            {
                if(time != null && value != null)
                {
                    time = new Second(new Hour(time.getEnd()).next().next().getEnd());
                    series.add(time, value);                 
                }
            }

            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }    
    
    private TimeSeries CreateMovingAverageTimeSeries(String title, XYDataset sourceDataset)
    {
        TimeSeries ts = new TimeSeries(title + " - Moving average");
        RegularTimePeriod time;
        for(int i = 0; i < sourceDataset.getItemCount(0); i++)
        {
            time = new Second(new Date((long)sourceDataset.getX(0, i)));
            ts.add(time,sourceDataset.getY(0, i));
//            System.out.println("x = " + sourceDataset.getX(0, i) + " , y = " + sourceDataset.getY(0, i));
        }
        
        return  ts;
        
    }

    public JPanel createChartPanel(String title,ArrayList<ResultSet> resultSets, ArrayList<String> axes)
    {     
        chartTitle = title;
        JFreeChart chart = createChart(title,resultSets,axes);
        chartPanel = new ChartPanel(chart);
        chartPanel.addChartMouseListener(this);
        CrosshairOverlay crosshairOverlay = new CrosshairOverlay(this);
        xCrosshair = new Crosshair(Double.NaN, Color.DARK_GRAY, new BasicStroke(.5f));
        xCrosshair.setLabelVisible(true);
        xCrosshair.setLabelGenerator((Crosshair crshr) ->
        {
            return Utilities.DateFormatShort(crshr.getValue());
        });
        crosshairOverlay.addDomainCrosshair(xCrosshair);    

        XYPlot plot = (XYPlot) chart.getPlot();
        int rangeAxisCount = plot.getRangeAxisCount();
        for(int i = 0; i < rangeAxisCount; i++)
        {
            Crosshair crosshair = new Crosshair(Double.NaN, colors[i], new BasicStroke(.5f));
            crosshair.setLabelVisible(true);
            if(i%4 == 1)
                crosshair.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
            if(i%4 == 3)
                crosshair.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            if(i%4 == 2)
                crosshair.setLabelAnchor(RectangleAnchor.TOP_LEFT);
             
            String lbl = plot.getRangeAxis(i).getLabel();
            if(lbl.equals("moving average"))
                maCrosshair = crosshair;
            //for moving average crosshair label use the format of the axis it's coupled to
            final String label = lbl.equals("moving average") ? axes.get(0) : lbl;
            
            crosshair.setLabelGenerator((Crosshair crshr) ->
            {
                return Utilities.GenerateLabelString(label, crshr.getValue());
            });
            crosshairOverlay.addRangeCrosshair(crosshair);
        } 
        chart.setBackgroundPaint(Color.DARK_GRAY);
        chartPanel.addOverlay(crosshairOverlay);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.addMouseListener(new MouseListener()
        {
            @Override
            public void mousePressed(MouseEvent me)
            {
                if(showDialog)
                    chartDialog.setVisible(false);
            }
            @Override
            public void mouseReleased(MouseEvent me)
            {
                if (chartPanel.getScreenDataArea().contains(me.getPoint()))
                {
                    if(showDialog)
                    {
                        SetDialogPosition(me.getLocationOnScreen(), me.getPoint(), me.getX(), me.getY());
                        chartDialog.setVisible(true);
                    }
                }
            }
            @Override public void mouseEntered(MouseEvent me){}
            @Override public void mouseExited(MouseEvent me){}
            @Override public void mouseClicked(MouseEvent me){}
        });
        chartDialog.setSize(dialogSize.x, dialogSize.y * (rangeAxisCount + 1) + 10);
        
        //must be done after chart is initialized, 
        //make sure there is a dataset plotted to MA (in case not enough data for rangeAxis[0]
        if(axes.size() == 1 && plot.getRangeAxis(1) != null)
            SetMovingAverage(movingAverageEnabled);
        
        return chartPanel;
    } 
    
}