package org.fhcrc.cpl.viewer.quant.gui;

import org.fhcrc.cpl.viewer.quant.QuantEvent;
import org.fhcrc.cpl.toolbox.Rounder;

import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.MouseListener;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.ItemEvent;

/**
     *  Display quantitative event info in a table.  Each row has a checkbox for event selection.
 * If events were already selected prior to displaying this table (as indicated by passed-in event
 * indices), they are indicated as selected, and their checkboxes are disabled.
 *
 * Ratio is indicated by a number, and also by a slider indicating the ratio in log space
 */
public class QuantEventsSummaryTable extends JTable
{
    //shading for alternating peptides.  Not a great idea if the table is resorted
    protected List<Integer> shadedTableRows = new ArrayList<Integer>();

    //List of events that have already been selected, should not be allowed to be deselected
    protected List<Integer> alreadySelectedRows = new ArrayList<Integer>();

    protected List<QuantEvent> quantEvents = new ArrayList<QuantEvent>();

    protected Map<String, Integer> fractionNameNumberMap = new HashMap<String, Integer>();

    DefaultTableModel model = new DefaultTableModel(0, 11)
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
            switch (columnIndex)
            {
                case 0:
                    //the checkbox
                    return Boolean.class;
                case 7:
                    //log ratio slider
                    return JSlider.class;
                default:
                    return String.class;
            }
        }
    };

    /**
     * Hide the checkbox column.  There's no undoing this
     */
    public void hideSelectionColumn()
    {
        this.removeColumn(getColumnModel().getColumn(0));
    }

    /**
     * Hide the Protein column.  There's no undoing this
     */
    public void hideProteinColumn()
    {
        this.removeColumn(getColumnModel().getColumn(1));
    }

    /**
     * Hide the fraction column.  There's no undoing this
     */
    public void hideFractionColumn()
    {
        this.removeColumn(getColumnModel().getColumn(2));
    }

    public QuantEventsSummaryTable()
    {
        setModel(model);

        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(model);


        int columnNum = 0;

        TableColumn checkboxColumn = getColumnModel().getColumn(columnNum++);
        checkboxColumn.setHeaderValue("");
        checkboxColumn.setPreferredWidth(20);
        checkboxColumn.setMaxWidth(20);

        TableColumn proteinColumn = getColumnModel().getColumn(columnNum++);
        proteinColumn.setHeaderValue("Protein");
        proteinColumn.setPreferredWidth(90);

        TableColumn peptideColumn = getColumnModel().getColumn(columnNum++);
        peptideColumn.setHeaderValue("Peptide");
        peptideColumn.setPreferredWidth(170);
        peptideColumn.setMinWidth(140);

        getColumnModel().getColumn(columnNum++).setHeaderValue("Fraction");
        getColumnModel().getColumn(columnNum).setHeaderValue("Charge");
        getColumnModel().getColumn(columnNum++).setPreferredWidth(45);
        getColumnModel().getColumn(columnNum++).setHeaderValue("Probability");
        getColumnModel().getColumn(columnNum++).setHeaderValue("Ratio");
        getColumnModel().getColumn(columnNum++).setHeaderValue("Light");
        getColumnModel().getColumn(columnNum++).setHeaderValue("Heavy");
        TableColumn logRatioSliderColumn = getColumnModel().getColumn(columnNum);
        logRatioSliderColumn.setHeaderValue("LogRatio");
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
        getColumnModel().getColumn(columnNum++).setHeaderValue("Evaluation");

        getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        setRowSorter(sorter);
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
    }

    public void addEvent(QuantEvent quantEvent, boolean alreadySelected)
    {
        String previousPeptide = "";
        int numRows = model.getRowCount();
        boolean previousRowShaded = false;

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

        float ratioBound = 10f;
        float logRatioBounded =
                (float) Math.log(Math.min(ratioBound, Math.max(1.0f / ratioBound, quantEvent.getRatio())));
        int logRatioIntegerizedHundredScale =
                (int) (logRatioBounded * 100 / (2 * Math.log(ratioBound))) + 50;

        int colNum = 0;
        model.setValueAt(false, numRows, colNum++);
        model.setValueAt(quantEvent.getProtein(), numRows, colNum++);
        model.setValueAt(quantEvent.getPeptide(), numRows, colNum++);
        model.setValueAt("" + fractionNum, numRows, colNum++);
        model.setValueAt("" + quantEvent.getCharge(), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getPeptideProphet(),3), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getRatio(),3), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getLightIntensity(),1), numRows, colNum++);
        model.setValueAt("" + Rounder.round(quantEvent.getHeavyIntensity(),1), numRows, colNum++);
        model.setValueAt(logRatioIntegerizedHundredScale, numRows, colNum++);
        model.setValueAt(QuantEvent.convertCurationStatusToString(quantEvent.getQuantCurationStatus()),
                numRows, colNum++);

        if (alreadySelected)
        {
            alreadySelectedRows.add(numRows);
            model.setValueAt(true, numRows, 0);
        }
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
        for (int i=0; i<quantEvents.size(); i++)
        {
            QuantEvent quantEvent = quantEvents.get(i);
            boolean alreadySelected = (alreadySelectedEventIndices != null &&
                alreadySelectedEventIndices.contains(i));
            addEvent(quantEvent, alreadySelected);
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

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column)
        {
            slider.setValue((Integer)value);
            return slider;
        }
    }

//        public class CheckBoxRenderer implements TableCellRenderer
//        {
//            protected JCheckBox checkBox = new JCheckBox();
//
//            public CheckBoxRenderer()
//            {
//            }
//
//            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
//                                                           boolean hasFocus, int row, int column)
//            {
//                if (alreadySelectedRows.contains(row))
//                {
//                    checkBox.setSelected(true);
//                }
//                return checkBox;
//            }
//        }


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
        public void itemStateChanged(ItemEvent e) {
            Object source = e.getSource();
            if (source instanceof AbstractButton == false) return;
            boolean checked = e.getStateChange() == ItemEvent.SELECTED;
            for(int x = 0, y = getRowCount(); x < y; x++)
            {
                setValueAt(new Boolean(checked),x,0);
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
}
