package reqorder;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.OverlayChangeEvent;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.chart.text.TextUtils;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.ObjectUtils;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

public class CrosshairOverlay extends AbstractOverlay implements Overlay,
        PropertyChangeListener, PublicCloneable, Cloneable, Serializable
{
    private List xCrosshairs;
    private List yCrosshairs;
    private final ChartMaker chartMaker;
    private String dialogString; 
    private final int[] levels = { 0, 7200, 72000 , 201600 , 374400 ,618400 , 964000 , 1482400 , 2173600 , 3037600 , 4074400 };
    
    public CrosshairOverlay(ChartMaker chartMaker)
    {
        super();
        this.xCrosshairs = new java.util.ArrayList();
        this.yCrosshairs = new java.util.ArrayList();
        this.chartMaker = chartMaker;
    }

    /**
     * Adds a crosshair against the domain axis and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     * @param crosshair
     */
    public void addDomainCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        this.xCrosshairs.add(crosshair);
        crosshair.addPropertyChangeListener(this);
        fireOverlayChanged();
    }

    /**
     * Removes a domain axis crosshair and sends an {@link OverlayChangeEvent}
     * to all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     *
     * @see #addDomainCrosshair(org.jfree.chart.plot.Crosshair)
     */
    public void removeDomainCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        if (this.xCrosshairs.remove(crosshair))
        {
            crosshair.removePropertyChangeListener(this);
            fireOverlayChanged();
        }
    }

    /**
     * Clears all the domain crosshairs from the overlay and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     */
    public void clearDomainCrosshairs()
    {
        if (this.xCrosshairs.isEmpty())
        {
            return;  // nothing to do
        }
        List crosshairs = getDomainCrosshairs();
        for (int i = 0; i < crosshairs.size(); i++)
        {
            Crosshair c = (Crosshair) crosshairs.get(i);
            this.xCrosshairs.remove(c);
            c.removePropertyChangeListener(this);
        }
        fireOverlayChanged();
    }

    /**
     * Returns a new list containing the domain crosshairs for this overlay.
     *
     * @return A list of crosshairs.
     */
    public List getDomainCrosshairs()
    {
        return new ArrayList(this.xCrosshairs);
    }

    /**
     * Adds a crosshair against the range axis and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     */
    public void addRangeCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        this.yCrosshairs.add(crosshair);
        crosshair.addPropertyChangeListener(this);
        fireOverlayChanged();
    }

    /**
     * Removes a range axis crosshair and sends an {@link OverlayChangeEvent} to
     * all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     *
     * @see #addRangeCrosshair(org.jfree.chart.plot.Crosshair)
     */
    public void removeRangeCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        if (this.yCrosshairs.remove(crosshair))
        {
            crosshair.removePropertyChangeListener(this);
            fireOverlayChanged();
        }
    }

    /**
     * Clears all the range crosshairs from the overlay and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     */
    public void clearRangeCrosshairs()
    {
        if (this.yCrosshairs.isEmpty())
        {
            return;  // nothing to do
        }
        List crosshairs = getRangeCrosshairs();
        for (int i = 0; i < crosshairs.size(); i++)
        {
            Crosshair c = (Crosshair) crosshairs.get(i);
            this.yCrosshairs.remove(c);
            c.removePropertyChangeListener(this);
        }
        fireOverlayChanged();
    }

    /**
     * Returns a new list containing the range crosshairs for this overlay.
     *
     * @return A list of crosshairs.
     */
    public List getRangeCrosshairs()
    {
        return new ArrayList(this.yCrosshairs);
    }

    /**
     * Receives a property change event (typically a change in one of the
     * crosshairs).
     *
     * @param e the event.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e)
    {
        fireOverlayChanged();
    }

    /**
     * Paints the crosshairs in the layer.
     *
     * @param g2 the graphics target.
     * @param chartPanel the chart panel.
     */
    @Override
    public void paintOverlay(Graphics2D g2, ChartPanel chartPanel)
    {  
        //Draw vertical crosshair
        Shape savedClip = g2.getClip();
        Rectangle2D dataArea = chartPanel.getScreenDataArea();
        g2.clip(dataArea);
        JFreeChart chart = chartPanel.getChart();
        XYPlot plot = (XYPlot) chart.getPlot();
        
        Crosshair crosshairX = (Crosshair) xCrosshairs.get(0);
        ValueAxis xAxis = plot.getDomainAxis();            
        RectangleEdge xAxisEdge = plot.getDomainAxisEdge();
        double x = crosshairX.getValue();
        double xx = xAxis.valueToJava2D(x, dataArea, xAxisEdge);
        if (plot.getOrientation() == PlotOrientation.VERTICAL)
        {
            drawVerticalCrosshair(g2, dataArea, xx, crosshairX);
        }
        else
        {
            drawHorizontalCrosshair(g2, dataArea, xx, crosshairX);
        }   

        dialogString = Utilities.DateFormatShort(x) + "<br/>";
        
        String lastLabel = "";
        long millis = (long) x;
        for (int i = 0; i < chartMaker.datasets.size(); i++)
        {               
            ValueAxis yAxis = plot.getRangeAxis(i);
            
            if(yAxis.getLabel().equals("moving average") && !chartMaker.maCrosshair.isVisible())
                continue;
            
            boolean stepRenderer = chartPanel.getChart().getXYPlot().getRenderer(i) instanceof XYStepRenderer;   
            dataArea = chartPanel.getScreenDataArea();
            g2.clip(dataArea);
            TimeSeriesCollection dataset = chartMaker.datasets.get(i);
            
            double y = Double.NaN;  
            if(stepRenderer)
            {
                int[] bounds = dataset.getSurroundingItems(0, millis);
                if (bounds[0] >= 0)
                {                    
                    TimeSeriesDataItem prevItem = dataset.getSeries(0).getDataItem(bounds[0]);
    //                prevX = prevItem.getPeriod().getMiddleMillisecond();
                    Number yValue = prevItem.getValue();
                    if (yValue != null)
                    {
                        y = yValue.doubleValue();
                    }
                }
            }
            else
                y =  DatasetUtils.findYValue(dataset, 0, x);            

            if(chartMaker.showCrosshairs)
            {
                Crosshair ch = (Crosshair) yCrosshairs.get(i);
                if(ch.isVisible())
                {
                    RectangleEdge yAxisEdge = plot.getRangeAxisEdge(i);
                    ch.setValue(y);

                    double yy = yAxis.valueToJava2D(y, dataArea, yAxisEdge);
                    if (plot.getOrientation() == PlotOrientation.VERTICAL)
                    {
                        drawHorizontalCrosshair(g2, dataArea, yy, ch);
                    }
                    else
                    {
                        drawVerticalCrosshair(g2, dataArea, yy, ch);
                    }
                }
            }
            
            if(chartMaker.showDialog)
            {
                //for moving average dialog label use the format of the axis it's coupled to
                String label = yAxis.getLabel().equals("moving average") ? lastLabel : yAxis.getLabel();
                
                switch(label)
                {
                    case "blockheight":
                    case "myblockheight":
                    case "numberOfConnections":
                    case "allKnownPeers":
                    case "allOnlineMinters":
                    case "blocks":
                    case "level":
                        dialogString += String.format("%s : %s<br/>",yAxis.getLabel(),NumberFormat.getIntegerInstance().format((int) y));
                        break;
                    case "levelling":
                        dialogString += String.format("%s : %d<br/>%s blocks","level",
                                (int) y, NumberFormat.getIntegerInstance().format(levels[(int)y]));
                        break;
                    case "bytes_sent":
                    case "bytes_received":
                    case "bytes_sent_avg_min":
                    case "bytes_sent_avg_hour":
                    case "bytes_sent_avg_day":
                    case "bytes_rec_avg_min":
                    case "bytes_rec_avg_hour":
                    case "bytes_rec_avg_day":
                    case "ram_usage":
                    case "blockchainsize":
                        dialogString += String.format("%s : %.2fMb<br/>",yAxis.getLabel(), y );
                        break;  
                    case "ltc_to_qort_price":
                    case "doge_to_qort_price":
                    case "balance":
                        dialogString += String.format("%s : %.5f QORT<br/>",yAxis.getLabel(), (double) y);
                        break;   
                    case "qort_to_ltc_price":    
                        dialogString += String.format("%s : %.5f LTC<br/>",yAxis.getLabel(), (double) y);
                        break;     
                    case "qort_to_doge_price": 
                        dialogString += String.format("%s : %.5f Doge<br/>",yAxis.getLabel(), (double) y);
                        break;           
                    case "uptime":
                        dialogString += String.format("%s:<br/>%s<br/>",yAxis.getLabel(), Utilities.MillisToDayHrMinShortFormat((long)y));
                        break;
                    case "buildversion":
                        dialogString += String.format("%s:<br/>%.6f<br/>",yAxis.getLabel(), (double) y);
                        break;
                    case "mintingrate":
                        dialogString += String.format("%s : %d B/Hr<br/>",yAxis.getLabel(), (int) y );
                        break;
                    case "balancedelta":
                        dialogString += String.format("%s : %.3f Q/Hr<br/>",yAxis.getLabel(), (double) y);
                        break; 
                    case "efficiency":
                        dialogString += String.format("%s : %.2f%%<br/>",yAxis.getLabel(), (double) y);
                        break; 
                    case "cpu_temp":
                        dialogString += String.format("%s : %.1f °C<br/>",yAxis.getLabel(), (double) y);
                        break; 
                }
            }   
            lastLabel = yAxis.getLabel();
        }
        
        chartMaker.chartDialogLabel.setText(Utilities.AllignCenterHTML(dialogString));
        g2.setClip(savedClip);       
    }

    /**
     * Draws a crosshair horizontally across the plot.
     *
     * @param g2 the graphics target.
     * @param dataArea the data area.
     * @param y the y-value in Java2D space.
     * @param crosshair the crosshair.
     */
    protected void drawHorizontalCrosshair(Graphics2D g2, Rectangle2D dataArea,
            double y, Crosshair crosshair)
    {

        if (y >= dataArea.getMinY() && y <= dataArea.getMaxY())
        {
            Line2D line = new Line2D.Double(dataArea.getMinX(), y,
                    dataArea.getMaxX(), y);
            Paint savedPaint = g2.getPaint();
            Stroke savedStroke = g2.getStroke();
            g2.setPaint(crosshair.getPaint());
            g2.setStroke(crosshair.getStroke());
            g2.draw(line);
            if (crosshair.isLabelVisible())
            {
                String label = crosshair.getLabelGenerator().generateLabel(
                        crosshair);
                RectangleAnchor anchor = crosshair.getLabelAnchor();
                Point2D pt = calculateLabelPoint(line, anchor, 5, 5);
                float xx = (float) pt.getX();
                float yy = (float) pt.getY();
                TextAnchor alignPt = textAlignPtForLabelAnchorH(anchor);
                Shape hotspot = TextUtils.calculateRotatedStringBounds(
                        label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);   
                if(hotspot != null)
                {                    
                    if (!dataArea.contains(hotspot.getBounds2D()))
                    {
                        anchor = flipAnchorV(anchor);
                        pt = calculateLabelPoint(line, anchor, 5, 5);
                        xx = (float) pt.getX();
                        yy = (float) pt.getY();
                        alignPt = textAlignPtForLabelAnchorH(anchor);
                        hotspot = TextUtils.calculateRotatedStringBounds(
                                label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                    }

                    g2.setPaint(crosshair.getLabelBackgroundPaint());
                    g2.fill(hotspot);
                    g2.setPaint(crosshair.getLabelOutlinePaint());
                    g2.setStroke(crosshair.getLabelOutlineStroke());
                    g2.draw(hotspot);
                    TextUtils.drawAlignedString(label, g2, xx, yy, alignPt);
                }
            }
            g2.setPaint(savedPaint);
            g2.setStroke(savedStroke);
        }
    }

    /**
     * Draws a crosshair vertically on the plot.
     *
     * @param g2 the graphics target.
     * @param dataArea the data area.
     * @param x the x-value in Java2D space.
     * @param crosshair the crosshair.
     */
    protected void drawVerticalCrosshair(Graphics2D g2, Rectangle2D dataArea,
            double x, Crosshair crosshair)
    {

        if (x >= dataArea.getMinX() && x <= dataArea.getMaxX())
        {
            Line2D line = new Line2D.Double(x, dataArea.getMinY(), x,
                    dataArea.getMaxY());
            Paint savedPaint = g2.getPaint();
            Stroke savedStroke = g2.getStroke();
            g2.setPaint(crosshair.getPaint());
            g2.setStroke(crosshair.getStroke());
            g2.draw(line);
            if (crosshair.isLabelVisible())
            {
                String label = crosshair.getLabelGenerator().generateLabel(
                        crosshair);
                RectangleAnchor anchor = crosshair.getLabelAnchor();
                Point2D pt = calculateLabelPoint(line, anchor, 5, 5);
                float xx = (float) pt.getX();
                float yy = (float) pt.getY();
                TextAnchor alignPt = textAlignPtForLabelAnchorV(anchor);
                Shape hotspot = TextUtils.calculateRotatedStringBounds(
                        label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                if (!dataArea.contains(hotspot.getBounds2D()))
                {
                    anchor = flipAnchorH(anchor);
                    pt = calculateLabelPoint(line, anchor, 5, 5);
                    xx = (float) pt.getX();
                    yy = (float) pt.getY();
                    alignPt = textAlignPtForLabelAnchorV(anchor);
                    hotspot = TextUtils.calculateRotatedStringBounds(
                            label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                }
                g2.setPaint(crosshair.getLabelBackgroundPaint());
                g2.fill(hotspot);
                g2.setPaint(crosshair.getLabelOutlinePaint());
                g2.setStroke(crosshair.getLabelOutlineStroke());
                g2.draw(hotspot);
                TextUtils.drawAlignedString(label, g2, xx, yy, alignPt);
            }
            g2.setPaint(savedPaint);
            g2.setStroke(savedStroke);
        }
    }

    /**
     * Calculates the anchor point for a label.
     *
     * @param line the line for the crosshair.
     * @param anchor the anchor point.
     * @param deltaX the x-offset.
     * @param deltaY the y-offset.
     *
     * @return The anchor point.
     */
    private Point2D calculateLabelPoint(Line2D line, RectangleAnchor anchor,
            double deltaX, double deltaY)
    {
        double x, y;
        boolean left = (anchor == RectangleAnchor.BOTTOM_LEFT
                || anchor == RectangleAnchor.LEFT
                || anchor == RectangleAnchor.TOP_LEFT);
        boolean right = (anchor == RectangleAnchor.BOTTOM_RIGHT
                || anchor == RectangleAnchor.RIGHT
                || anchor == RectangleAnchor.TOP_RIGHT);
        boolean top = (anchor == RectangleAnchor.TOP_LEFT
                || anchor == RectangleAnchor.TOP
                || anchor == RectangleAnchor.TOP_RIGHT);
        boolean bottom = (anchor == RectangleAnchor.BOTTOM_LEFT
                || anchor == RectangleAnchor.BOTTOM
                || anchor == RectangleAnchor.BOTTOM_RIGHT);
        Rectangle rect = line.getBounds();

        // we expect the line to be vertical or horizontal
        if (line.getX1() == line.getX2())
        {  // vertical
            x = line.getX1();
            y = (line.getY1() + line.getY2()) / 2.0;
            if (left)
            {
                x = x - deltaX;
            }
            if (right)
            {
                x = x + deltaX;
            }
            if (top)
            {
                y = Math.min(line.getY1(), line.getY2()) + deltaY;
            }
            if (bottom)
            {
                y = Math.max(line.getY1(), line.getY2()) - deltaY;
            }
        }
        else
        {  // horizontal
            x = (line.getX1() + line.getX2()) / 2.0;
            y = line.getY1();
            if (left)
            {
                x = Math.min(line.getX1(), line.getX2()) + deltaX;
            }
            if (right)
            {
                x = Math.max(line.getX1(), line.getX2()) - deltaX;
            }
            if (top)
            {
                y = y - deltaY;
            }
            if (bottom)
            {
                y = y + deltaY;
            }
        }
        return new Point2D.Double(x, y);
    }

    /**
     * Returns the text anchor that is used to align a label to its anchor
     * point.
     *
     * @param anchor the anchor.
     *
     * @return The text alignment point.
     */
    private TextAnchor textAlignPtForLabelAnchorV(RectangleAnchor anchor)
    {
        TextAnchor result = TextAnchor.CENTER;
        switch (anchor)
        {
            case TOP_LEFT:
                result = TextAnchor.TOP_RIGHT;
                break;
            case TOP:
                result = TextAnchor.TOP_CENTER;
                break;
            case TOP_RIGHT:
                result = TextAnchor.TOP_LEFT;
                break;
            case LEFT:
                result = TextAnchor.HALF_ASCENT_RIGHT;
                break;
            case RIGHT:
                result = TextAnchor.HALF_ASCENT_LEFT;
                break;
            case BOTTOM_LEFT:
                result = TextAnchor.BOTTOM_RIGHT;
                break;
            case BOTTOM:
                result = TextAnchor.BOTTOM_CENTER;
                break;
            case BOTTOM_RIGHT:
                result = TextAnchor.BOTTOM_LEFT;
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Returns the text anchor that is used to align a label to its anchor
     * point.
     *
     * @param anchor the anchor.
     *
     * @return The text alignment point.
     */
    private TextAnchor textAlignPtForLabelAnchorH(RectangleAnchor anchor)
    {
        TextAnchor result = TextAnchor.CENTER;
        switch (anchor)
        {
            case TOP_LEFT:
                result = TextAnchor.BOTTOM_LEFT;
                break;
            case TOP:
                result = TextAnchor.BOTTOM_CENTER;
                break;
            case TOP_RIGHT:
                result = TextAnchor.BOTTOM_RIGHT;
                break;
            case LEFT:
                result = TextAnchor.HALF_ASCENT_LEFT;
                break;
            case RIGHT:
                result = TextAnchor.HALF_ASCENT_RIGHT;
                break;
            case BOTTOM_LEFT:
                result = TextAnchor.TOP_LEFT;
                break;
            case BOTTOM:
                result = TextAnchor.TOP_CENTER;
                break;
            case BOTTOM_RIGHT:
                result = TextAnchor.TOP_RIGHT;
                break;
            default:
                break;
        }
        return result;
    }

    private RectangleAnchor flipAnchorH(RectangleAnchor anchor)
    {
        RectangleAnchor result = anchor;
        switch (anchor)
        {
            case TOP_LEFT:
                result = RectangleAnchor.TOP_RIGHT;
                break;
            case TOP_RIGHT:
                result = RectangleAnchor.TOP_LEFT;
                break;
            case LEFT:
                result = RectangleAnchor.RIGHT;
                break;
            case RIGHT:
                result = RectangleAnchor.LEFT;
                break;
            case BOTTOM_LEFT:
                result = RectangleAnchor.BOTTOM_RIGHT;
                break;
            case BOTTOM_RIGHT:
                result = RectangleAnchor.BOTTOM_LEFT;
                break;
            default:
                break;
        }
        return result;
    }

    private RectangleAnchor flipAnchorV(RectangleAnchor anchor)
    {
        RectangleAnchor result = anchor;
        switch (anchor)
        {
            case TOP_LEFT:
                result = RectangleAnchor.BOTTOM_LEFT;
                break;
            case TOP_RIGHT:
                result = RectangleAnchor.BOTTOM_RIGHT;
                break;
            case TOP:
                result = RectangleAnchor.BOTTOM;
                break;
            case BOTTOM:
                result = RectangleAnchor.TOP;
                break;
            case BOTTOM_LEFT:
                result = RectangleAnchor.TOP_LEFT;
                break;
            case BOTTOM_RIGHT:
                result = RectangleAnchor.TOP_RIGHT;
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Tests this overlay for equality with an arbitrary object.
     *
     * @param obj the object ({@code null} permitted).
     *
     * @return A boolean.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        if (!(obj instanceof CrosshairOverlay))
        {
            return false;
        }
        CrosshairOverlay that = (CrosshairOverlay) obj;
        if (!this.xCrosshairs.equals(that.xCrosshairs))
        {
            return false;
        }
        return this.yCrosshairs.equals(that.yCrosshairs);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.xCrosshairs);
        hash = 41 * hash + Objects.hashCode(this.yCrosshairs);
        return hash;
    }

    /**
     * Returns a clone of this instance.
     *
     * @return A clone of this instance.
     *
     * @throws java.lang.CloneNotSupportedException if there is some problem
     * with the cloning.
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        CrosshairOverlay clone = (CrosshairOverlay) super.clone();
        clone.xCrosshairs = (List) ObjectUtils.deepClone(this.xCrosshairs);
        clone.yCrosshairs = (List) ObjectUtils.deepClone(this.yCrosshairs);
        return clone;
    }

}
