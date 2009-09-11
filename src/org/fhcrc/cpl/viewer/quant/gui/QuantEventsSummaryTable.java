package org.fhcrc.cpl.viewer.quant.gui;

import org.fhcrc.cpl.viewer.quant.QuantEvent;
import org.fhcrc.cpl.viewer.quant.QuantEventAssessor;
import org.fhcrc.cpl.toolbox.Rounder;

import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.*;

/**
     *  Display quantitative event info in a table.  Each row has a checkbox for event selection.
 * If events were already selected prior to displaying this table (as indicated by passed-in event
 * indices), they are indicated as selected, and their checkboxes are disabled.
 *
 * Ratio is indicated by a number, and also by a slider indicating the ratio in log space
 */
public class QuantEventsSummaryTable extends JTable
{
    //shading for alternating peptides.  Not a great idea if the table is re-sorted
    protected List<Integer> shadedTableRows = new ArrayList<Integer>();

    //List of events that have already been selected, should not be allowed to be deselected
    protected List<Integer> alreadySelectedRows = new ArrayList<Integer>();

    protected List<QuantEvent> quantEvents = new ArrayList<QuantEvent>();

    protected Map<String, Integer> fractionNameNumberMap = new HashMap<String, Integer>();

    protected TableColumn logRatioSliderColumn;
    protected TableColumn checkboxColumn;
    protected TableColumn proteinColumn;
    protected TableColumn fractionColumn;
    protected TableColumn assessmentColumn;
    protected TableColumn geneColumn;

    protected Map<String, List<String>> proteinGenesMap;

    protected int quantCurationColumnIndex;


    protected QuantEventChangeListener changeListener = new QuantEventChangeListener();

    protected int ratioColumnIndex = 0;

    protected TableRowSorter<TableModel> sorter;




    DefaultTableModel model = new DefaultTableModel(0, 13)
    {
        //all cells uneditable
        public boolean isCellEditable(int row, int column)
        {
            if (column == 0)
            {
                if (alreadySelectedRows == null || !alreadySelectedRows.contains(row))
                    return true;
            }
            return false;
        }

        public Class getColumnClass(int columnIndex)
        {
            String columnName = getColumnName(columnIndex);

            if (columnIndex == 0)
                return Boolean.class;
            else if ("Ratio".equals(columnName))
                    return Float.class;
            else if ("LogRatio".equals(columnName))
                return JSlider.class;
            else return String.class;
        }
    };

    /**
     * Hide the checkbox column.  There's no undoing this
     */
    public void hideSelectionColumn()
    {
        this.removeColumn(checkboxColumn);
    }

    /**
     * Hide the Protein column.  There's no undoing this
     */
    public void hideProteinColumn()
    {
        this.removeColumn(proteinColumn);
//        proteinColumn.setMaxWidth(0);
    }

    /**
     * Hide the fraction column.  There's no undoing this
     */
    public void hideFractionColumn()
    {
        this.removeColumn(fractionColumn);
//        fractionColumn.setMaxWidth(0);

    }

    /**
     * Hide the Assessment column.  There's no undoing this
     */
    public void hideAssessmentColumn()
    {
        this.removeColumn(assessmentColumn);
//        assessmentColumn.setMaxWidth(0);
    }

    /**
     * Hide the Gene column.  There's no undoing this
     */
    public void hideGeneColumn()
    {
        this.removeColumn(geneColumn);
//        geneColumn.setMaxWidth(0);
    }

    public QuantEventsSummaryTable()
    {
        setModel(model);
        sorter = new TableRowSorter<TableModel>(model);


        int columnNum = 0;

        List<String> columnNames = new ArrayList<String>();

        checkboxColumn = getColumnModel().getColumn(columnNum++);
        checkboxColumn.setHeaderRenderer(new CheckBoxHeader(new SelectAllListener()));        
        checkboxColumn.setHeaderValue("");
        columnNames.add("");
        checkboxColumn.setPreferredWidth(20);
        checkboxColumn.setMaxWidth(20);

        geneColumn = getColumnModel().getColumn(columnNum++);
        geneColumn.setHeaderValue("Gene");
        columnNames.add("Gene");
        geneColumn.setPreferredWidth(90);

        proteinColumn = getColumnModel().getColumn(columnNum++);
        proteinColumn.setHeaderValue("Protein");
        columnNames.add("Protein");
        proteinColumn.setPreferredWidth(90);

        TableColumn peptideColumn = getColumnModel().getColumn(columnNum++);
        peptideColumn.setHeaderValue("Peptide");
        columnNames.add("Peptide");
        peptideColumn.setPreferredWidth(170);
        peptideColumn.setMinWidth(140);

        fractionColumn = getColumnModel().getColumn(columnNum++);
        fractionColumn.setHeaderValue("Fraction");
        columnNames.add("Fraction");
        getColumnModel().getColumn(columnNum).setHeaderValue("Charge");
        columnNames.add("Charge");
        getColumnModel().getColumn(columnNum++).setPreferredWidth(45);
        getColumnModel().getColumn(columnNum).setHeaderValue("Prob");
        columnNames.add("Prob");
        
        getColumnModel().getColumn(columnNum++).setPreferredWidth(50);
        getColumnModel().getColumn(columnNum).setHeaderValue("Ratio");
        columnNames.add("Ratio");

        ratioColumnIndex = columnNum;
        getColumnModel().getColumn(columnNum++).setPreferredWidth(50);
        getColumnModel().getColumn(columnNum++).setHeaderValue("Light");
        columnNames.add("Light");

        getColumnModel().getColumn(columnNum++).setHeaderValue("Heavy");
        columnNames.add("Heavy");


        logRatioSliderColumn = getColumnModel().getColumn(columnNum);
        logRatioSliderColumn.setHeaderValue("LogRatio");
        columnNames.add("LogRatio");

        JSliderRenderer sliderRenderer = new JSliderRenderer();
        logRatioSliderColumn.setCellRenderer(sliderRenderer);
        logRatioSliderColumn.setPreferredWidth(280);
        logRatioSliderColumn.setMinWidth(100);
        //special comparator for slider column
        sorter.setComparator(columnNum++, new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2)
            {
                return o1 > o2 ? 1 : o1 < o2 ? -1 : 0;
            }
        });
        assessmentColumn = getColumnModel().getColumn(columnNum++);
        assessmentColumn.setHeaderValue("Assessment");
        columnNames.add("Assessment");

        quantCurationColumnIndex = columnNum;
        getColumnModel().getColumn(columnNum++).setHeaderValue("Evaluation");
        columnNames.add("Evaluation");


        getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        setRowSorter(sorter);
//        model.setColumnIdentifiers(columnNames.toArray(new String[columnNames.size()]));
    }

    /**
     * Returns model, not view, index
     * @return
     */
    public int getSelectedIndex()
    {
        ListSelectionModel lsm = this.getSelectionModel();
        if (lsm.isSelectionEmpty())
            return -1;
        // Find out which indexes are selected.
        int minIndex = lsm.getMinSelectionIndex();
        int maxIndex = lsm.getMaxSelectionIndex();
        if (minIndex == maxIndex)
        {
            return convertRowIndexToModel(minIndex);
        }
        else
            return -1;
    }

    protected Color altRowColor = new Color(235, 235, 235);
    /**
     * Shades alternate peptides in different colors.
     */
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
    {
        Component c = super.prepareRenderer(renderer, row, column);
        if (isCellSelected(row, column))
        {
            c.setBackground(UIManager.getColor("Table.selectionBackground"));
            Color selectedForegroundColor = UIManager.getColor("Table.selectionForeground");
            if (alreadySelectedRows != null && alreadySelectedRows.contains(row))
                selectedForegroundColor = Color.GRAY;
            c.setForeground(selectedForegroundColor);
        }
        else
        {
            Color rowColor = UIManager.getColor("Table.background");
            if (shadedTableRows.contains(row))
                rowColor = altRowColor;
            c.setBackground(rowColor);
            Color unselectedForegroundColor = UIManager.getColor("Table.foreground");
            if (alreadySelectedRows != null && alreadySelectedRows.contains(row))
                unselectedForegroundColor = Color.GRAY;
            c.setForeground(unselectedForegroundColor);
        }
        return c;
    }

    /**
     * Remove all properties from table
     */
    public void clearProperties()
    {
        while (model.getRowCount() > 0)
        {
            model.removeRow(0);
        }
    }

    /**
     * Map fraction names to numbers
     * @param events
     */
    protected void buildFractionNameNumberMap(List<QuantEvent> events)
    {
        Set<String> fractionNames = new HashSet<String>();
        for (QuantEvent quantEvent : events)
        {
            String fractionName = quantEvent.getFraction();
            if (fractionName != null)
            {
                fractionNames.add(fractionName);
            }
        }
        if (fractionNames.isEmpty())
            fractionNameNumberMap = null;
        else
        {
            fractionNameNumberMap = new HashMap<String, Integer>();
            List<String> fractionNamesList = new ArrayList<String>(fractionNames);
            Collections.sort(fractionNamesList);
            for (int i=0; i<fractionNamesList.size(); i++)
                fractionNameNumberMap.put(fractionNamesList.get(i), i+1);
        }
    }

    public void setEvents(List<QuantEvent> events)
    {
        buildFractionNameNumberMap(events);

        for (QuantEvent quantEvent : events)
        {
            addEvent(quantEvent, false);
        }
        quantEvents = new ArrayList<QuantEvent>(events);
    }

    public void addEvent(QuantEvent quantEvent, boolean alreadySelected)
    {
        String previousPeptide = "";
        int numRows = model.getRowCount();
        boolean previousRowShaded = false;

        quantEvent.addQuantCurationStatusListener(changeListener);
        

        if (numRows > 0)
        {
            previousPeptide = model.getValueAt(numRows-1, 2).toString();
            if (shadedTableRows.contains(numRows-1))
                previousRowShaded = true;
        }
        boolean shaded = ((previousRowShaded && quantEvent.getPeptide().equals(previousPeptide)) ||
                (!previousRowShaded && !quantEvent.getPeptide().equals(previousPeptide)));
        if (shaded)
            shadedTableRows.add(numRows);

        model.setRowCount(numRows + 1);

        int fractionNum = 0;
        if (fractionNameNumberMap != null && quantEvent.getFraction() != null &&
            fractionNameNumberMap.containsKey(quantEvent.getFraction()))
            fractionNum = fractionNameNumberMap.get(quantEvent.getFraction());



        int colNum = 0;
        model.setValueAt(false, numRows, colNum++);
        String geneValue = "";
        if (proteinGenesMap != null && proteinGenesMap.containsKey(quantEvent.getProtein()))
        {
            //it would be better to do this once and cache it
            StringBuffer geneValueBuf = new StringBuffer();
            boolean first = true;
            for (String gene : proteinGenesMap.get(quantEvent.getProtein()))
            {
                if (!first)
                    geneValueBuf.append(",");
                geneValueBuf.append(gene);
                first = false;
            }
            geneValue = geneValueBuf.toString();
        }
        model.setValueAt(geneValue, numRows, colNum++);
        model.setValueAt(quantEvent.getProtein(), numRows, colNum++);
        model.setValueAt(quantEvent.getPeptide(), numRows, colNum++);
        model.setValueAt("" + fractionNum, numRows, colNum++);
        model.setValueAt("" + quantEvent.getCharge(), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getPeptideProphet(),3), numRows, colNum++);
        model.setValueAt((float) Rounder.round(quantEvent.getRatio(),3), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getLightIntensity(),1), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getHeavyIntensity(),1), numRows, colNum++);
        model.setValueAt(integerizeRatio(quantEvent.getRatio()), numRows, colNum++);
        String assessmentString = "";
        QuantEventAssessor.QuantEventAssessment assessment = quantEvent.getAlgorithmicAssessment();
        if (assessment != null)
            assessmentString = QuantEventAssessor.flagReasonCodes[assessment.getStatus()];
        model.setValueAt(assessmentString, numRows, colNum++);
        model.setValueAt(QuantEvent.convertCurationStatusToString(quantEvent.getQuantCurationStatus()),
                numRows, colNum++);

        if (alreadySelected)
        {
            alreadySelectedRows.add(numRows);
            model.setValueAt(true, numRows, 0);
        }
    }

    protected int integerizeRatio(float ratio)
    {
        float ratioBound = 10f;
        float logRatioBounded =
                (float) Math.log(Math.min(ratioBound, Math.max(1.0f / ratioBound, ratio)));
        return (int) (logRatioBounded * 100 / (2 * Math.log(ratioBound))) + 50;
    }

    public void displayEvents(List<QuantEvent> quantEvents)
    {
        displayEvents(quantEvents, null);
    }

    /**
     * Display a list of events.  Indicate selection for the events that have already been selected
     * @param quantEvents
     * @param alreadySelectedEventIndices
     */
    public void displayEvents(List<QuantEvent> quantEvents, List<Integer> alreadySelectedEventIndices)
    {
        clearProperties();
        buildFractionNameNumberMap(quantEvents);
        if (fractionNameNumberMap == null || fractionNameNumberMap.isEmpty() || fractionNameNumberMap.size() == 1)
            hideFractionColumn();
        this.quantEvents = quantEvents;
        shadedTableRows = new ArrayList<Integer>();
        boolean anyEventHasAssessment = false;

        for (int i=0; i<quantEvents.size(); i++)
        {
            QuantEvent quantEvent = quantEvents.get(i);
            if (quantEvent.getAlgorithmicAssessment() != null)
                anyEventHasAssessment = true;
            boolean alreadySelected = (alreadySelectedEventIndices != null &&
                alreadySelectedEventIndices.contains(i));
            addEvent(quantEvent, alreadySelected);
        }
        if (!anyEventHasAssessment)
            hideAssessmentColumn();
        if (proteinGenesMap == null)
        {
            hideGeneColumn();
        }
    }

    /**
     * Return all checked rows except the alreadySelectedRows rows
     * @return
     */
    public List<QuantEvent> getSelectedEvents()
    {
        List<QuantEvent> selectedQuantEvents = new ArrayList<QuantEvent>();
        for (int i=0; i<model.getRowCount(); i++)
        {
            Boolean isSelected = (Boolean) model.getValueAt(i, 0);
            if (isSelected && !alreadySelectedRows.contains(i))
            {
                selectedQuantEvents.add(quantEvents.get(i));
            }
        }
        return selectedQuantEvents;
    }

    public class JSliderRenderer implements TableCellRenderer
    {
        protected JSlider slider = new JSlider();

        public JSliderRenderer()
        {
            slider.setMinimum(0);
            slider.setMaximum(100);
            slider.setPaintLabels(false);
            slider.setPaintTicks(false);
            slider.setMajorTickSpacing(25); 
            slider.setPreferredSize(new Dimension(280, 15));
            slider.setPreferredSize(new Dimension(100, 15));
            slider.setToolTipText("Log ratio, bounded at 0.1 and 10");
        }

        public JSliderRenderer(float ratio)
        {
            this();
            slider.setValue(integerizeRatio(ratio));
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column)
        {
            //if value is an integer, set the slider.  Otherwise, it's a String for the header, ignore
            if (Integer.class.isAssignableFrom(value.getClass()))
                slider.setValue((Integer)value);
            return slider;
        }
    }

    /**
     * Make the header of the logratio column display a slider with the given ratio
     * @param ratio
     */
    public void setLogRatioHeaderRatio(float ratio)
    {
        JSliderRenderer renderer = new JSliderRenderer(ratio);
        renderer.slider.setToolTipText("Protein log ratio");
        logRatioSliderColumn.setHeaderRenderer(renderer);
    }

    protected class RatioRowFilter extends RowFilter<TableModel, Object>
    {
        protected float maxLowRatioValue;
        protected float minHighRatioValue;

        public RatioRowFilter(float maxLowRatioValue, float minHighRatioValue)
        {
            this.maxLowRatioValue = maxLowRatioValue;
            this.minHighRatioValue = minHighRatioValue;
        }

        public boolean include(RowFilter.Entry entry)
        {
            float ratio = (Float) entry.getValue(ratioColumnIndex);
            return include(ratio);
        }

        public boolean include(float ratio)
        {
            return (ratio <= maxLowRatioValue || ratio >= minHighRatioValue);
        }
    }

    public void showOnlyExtremeRatios(float maxLowRatioValue, float minHighRatioValue)
    {
        RowFilter<TableModel, Object> rf = new RatioRowFilter(maxLowRatioValue, minHighRatioValue);
        sorter.setRowFilter(rf);
    }


    class CheckBoxHeader extends JCheckBox
            implements TableCellRenderer, MouseListener
    {
        protected CheckBoxHeader rendererComponent;
        protected int column;
        protected boolean mousePressed = false;
        public CheckBoxHeader(ItemListener itemListener)
        {
            rendererComponent = this;
            rendererComponent.addItemListener(itemListener);
        }
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column)
        {
            if (table != null)
            {
                JTableHeader header = table.getTableHeader();
                if (header != null)
                {
                    rendererComponent.setForeground(header.getForeground());
                    rendererComponent.setBackground(header.getBackground());
                    rendererComponent.setFont(header.getFont());
                    header.addMouseListener(rendererComponent);
                }
            }
            setColumn(column);
            rendererComponent.setText("Check All");
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));
            return rendererComponent;
        }
        protected void setColumn(int column) {
            this.column = column;
        }
        public int getColumn() {
            return column;
        }
        protected void handleClickEvent(MouseEvent e) {
            if (mousePressed) {
                mousePressed=false;
                JTableHeader header = (JTableHeader)(e.getSource());
                JTable tableView = header.getTable();
                TableColumnModel columnModel = tableView.getColumnModel();
                int viewColumn = columnModel.getColumnIndexAtX(e.getX());
                int column = tableView.convertColumnIndexToModel(viewColumn);

                if (viewColumn == this.column && e.getClickCount() == 1 && column != -1) {
                    doClick();
                }
            }
        }
        public void mouseClicked(MouseEvent e) {
            handleClickEvent(e);
            ((JTableHeader)e.getSource()).repaint();
        }
        public void mousePressed(MouseEvent e) {
            mousePressed = true;
        }
        public void mouseReleased(MouseEvent e) {
        }
        public void mouseEntered(MouseEvent e) {
        }
        public void mouseExited(MouseEvent e) {
        }
    }

    class SelectAllListener implements ItemListener
    {
        public void itemStateChanged(ItemEvent e)
        {
            Object source = e.getSource();
            if (!(source instanceof AbstractButton)) return;
            boolean checked = e.getStateChange() == ItemEvent.SELECTED;
            for(int x = 0, y = getRowCount(); x < y; x++)
            {
                setValueAt(checked, x, 0);
            }
        }
    }

    public Map<String, Integer> getFractionNameNumberMap()
    {
        return fractionNameNumberMap;
    }

    public void setFractionNameNumberMap(Map<String, Integer> fractionNameNumberMap)
    {
        this.fractionNameNumberMap = fractionNameNumberMap;
    }

    public Map<String, List<String>> getProteinGenesMap()
    {
        return proteinGenesMap;
    }

    public void setProteinGenesMap(Map<String, List<String>> proteinGenesMap)
    {
        this.proteinGenesMap = proteinGenesMap;
    }

    protected class QuantEventChangeListener implements ActionListener
    {
        public void actionPerformed(ActionEvent event)
        {
            for (int row=0; row<quantEvents.size(); row++)
            {
                int currentValue =QuantEvent.parseCurationStatusString(
                        (String) model.getValueAt(row, quantCurationColumnIndex));
                int newValue = quantEvents.get(row).getQuantCurationStatus();
                if (currentValue != newValue)
                    model.setValueAt(QuantEvent.convertCurationStatusToString(
                            newValue), row, quantCurationColumnIndex);
            }
            updateUI();
        }
    }
}
