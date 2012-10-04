/*
 * The MIT License
 *
 * Copyright 2012 Praqma.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.praqma.jenkins.memorymap;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import java.awt.BasicStroke;
import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.praqma.jenkins.memorymap.result.MemoryMapParsingResult;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Praqma
 */
public class MemoryMapBuildAction implements Action {

    private List<MemoryMapParsingResult> results;
    private AbstractBuild<?,?> build;

    private static final HashMap<MemoryMapProjectAction.GraphCategories,List<String>> categoryMap = new HashMap<MemoryMapProjectAction.GraphCategories, List<String>>();
    
    static {
        categoryMap.put(MemoryMapProjectAction.GraphCategories.Flash, Arrays.asList(".econst",".text"));
        categoryMap.put(MemoryMapProjectAction.GraphCategories.Ram, Arrays.asList(".stack",".cinit",".ebss"));
    }
    
    public MemoryMapBuildAction(AbstractBuild<?,?> build, List<MemoryMapParsingResult> results) {
        this.results = results;
        this.build = build;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Memory map";
    }

    @Override
    public String getUrlName() {
        return null;
    }
    
    /**
     * Returns an indication wheather as to the requirements are met. You do one check per set of values you wish to compare. 
     * 
     * @param threshold
     * @param valuenames
     * @return 
     */
    public boolean validateThreshold(int threshold, String... valuenames) {
        return sumOfValues(valuenames) <= threshold;
    }
    
    public int sumOfValues(String... valuenames) { 
        int sum = 0;
        for(MemoryMapParsingResult res : getResults()) {
            for(String s : valuenames) {
                if(res.getName().equals(s)) {
                    sum+=res.getValue();
                }
            }
        }
        return sum;
    }
    
    public int sumOfValues(List<String> values) { 
        int sum = 0;
        for(MemoryMapParsingResult res : getResults()) {
            for(String s : values) {
                if(res.getName().equals(s)) {
                    sum+=res.getValue();
                }
            }
        }
        return sum;
    }
    
    /**
     * Fetches the previous MemoryMap build. Takes all succesful, but failed builds. 
     * 
     * Goes to the end of list.
     */ 
    public MemoryMapBuildAction getPreviousAction(AbstractBuild<?,?> base) {
        MemoryMapBuildAction action = null;
        AbstractBuild<?,?> start = base;
        while(true) {
            start = start.getPreviousCompletedBuild();
            if(start == null) {
                return null;
            }
            action = start.getAction(MemoryMapBuildAction.class);            
            if(action != null) {
                return action;
            }
        }
    }
    
    public MemoryMapBuildAction getPreviousAction() {
        MemoryMapBuildAction action = null;
        AbstractBuild<?,?> start = build;
        while(true) {
            start = start.getPreviousCompletedBuild();
            if(start == null) {
                return null;
            }
            action = start.getAction(MemoryMapBuildAction.class);            
            if(action != null && (action.getResults() != null && action.getResults().size() > 0)) {
                return action;
            }
        }
    }

    public void doDrawMemoryMapUsageGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dataset = new DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel>();
        MemoryMapProjectAction.GraphCategories category = MemoryMapProjectAction.GraphCategories.valueOf(req.getParameter("category"));
        List<String> valuesInCategory = categoryMap.get(category);
        int max = Integer.MIN_VALUE;
        
        for(MemoryMapBuildAction membuild = this; membuild != null; membuild = membuild.getPreviousAction()) {
            ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(membuild.build);
            List<MemoryMapParsingResult> result = membuild.getResults();
            for(MemoryMapParsingResult res : result) {
                int sum = membuild.sumOfValues(valuesInCategory);
                if(sum >= max) {
                    max = sum; 
                }
                
                if(valuesInCategory.contains(res.getName())) {
                    dataset.add(res.getValue(), res.getName(), label);
                }
            }
        }

        
        JFreeChart chart = createChart(dataset.build(), category.toString(), "Words", (int)((double)max*1.1), 0);
        ChartUtil.generateGraph( req, rsp, chart, 400, 300 );     
    }    

    /**
     * @return the results
     */
    public List<MemoryMapParsingResult> getResults() {
        return results;
    }

    /**
     * @param results the results to set
     */
    public void setResults(List<MemoryMapParsingResult> results) {
        this.results = results;
    }
    
    protected JFreeChart createChart( CategoryDataset dataset, String title, String yaxis, int max, int min ) {
        final JFreeChart chart = ChartFactory.createStackedAreaChart( title, // chart                                                                                                                                       // title
                        null, // unused
                        yaxis, // range axis label
                        dataset, // data
                        PlotOrientation.VERTICAL, // orientation
                        true, // include legend
                        true, // tooltips
                        false // urls
        );

        final LegendTitle legend = chart.getLegend();

        legend.setPosition( RectangleEdge.BOTTOM );

        chart.setBackgroundPaint( Color.white );

        final CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint( Color.WHITE );
        plot.setOutlinePaint( null );
        plot.setRangeGridlinesVisible( true );
        plot.setRangeGridlinePaint( Color.black );

        CategoryAxis domainAxis = new ShiftedCategoryAxis( null );
        plot.setDomainAxis( domainAxis );
        domainAxis.setCategoryLabelPositions( CategoryLabelPositions.UP_90 );
        domainAxis.setLowerMargin( 0.0 );
        domainAxis.setUpperMargin( 0.0 );
        domainAxis.setCategoryMargin( 0.0 );

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits( NumberAxis.createIntegerTickUnits() );
        rangeAxis.setUpperBound( max );
        rangeAxis.setLowerBound( min );
        
        final StackedAreaRenderer renderer = (StackedAreaRenderer) plot.getRenderer();
        renderer.setBaseStroke( new BasicStroke( 2.0f ) );
        //ColorPalette.apply( renderer );
        plot.setInsets( new RectangleInsets( 5.0, 0, 0, 5.0 ) );
        return chart;
    }
}