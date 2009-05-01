/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fll.xml.XMLUtils;

/**
 * Summary dialog for subjective scores.
 * 
 * @author jpschewe
 * @version $Revision$
 */
/* package */class SummaryDialog extends JDialog {

  public SummaryDialog(final SubjectiveFrame owner) {
    super(owner, true);

    final Container cpane = getContentPane();
    cpane.setLayout(new BorderLayout());

    final TableModel[] models = buildTableModels(owner.getChallengeDocument(), owner.getScoreDocument());
    final JTable teamTable = new JTable(models[0]);
    teamTable.setDefaultRenderer(Integer.class, CustomCellRenderer.INSTANCE);
    final JPanel teamPanel = new JPanel(new BorderLayout());
    final JLabel teamLabel = new JLabel("# of scores for each team in each category");
    final JScrollPane teamTableScroller = new JScrollPane(teamTable);
    teamPanel.add(teamLabel, BorderLayout.NORTH);
    teamPanel.add(teamTableScroller, BorderLayout.CENTER);
    cpane.add(teamPanel, BorderLayout.CENTER);

    final JTable summaryTable = new JTable(models[1]);
    final JLabel summaryLabel = new JLabel("# of teams in each category with the specified # of scores");
    final JPanel summaryPanel = new JPanel(new BorderLayout());
    summaryPanel.add(summaryLabel, BorderLayout.NORTH);
    final JScrollPane summaryTableScroller = new JScrollPane(summaryTable);
    final Dimension sumPrefSize = summaryTable.getPreferredSize();
    summaryTableScroller.setPreferredSize(new Dimension(sumPrefSize.width, sumPrefSize.height * 2));
    summaryPanel.add(summaryTableScroller, BorderLayout.CENTER);
    cpane.add(summaryPanel, BorderLayout.NORTH);

    final JButton closeButton = new JButton("Close");
    closeButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        setVisible(false);
        dispose();
      }
    });
    cpane.add(closeButton, BorderLayout.SOUTH);
  }

  /**
   * @param _challengeDocument
   * @param _scoreDocument
   * @return [0] is the team table model, [1] is the summary table model
   */
  private TableModel[] buildTableModels(final Document _challengeDocument, final Document _scoreDocument) {
    final Map<Integer, Integer[]> data = new HashMap<Integer, Integer[]>();

    final List<String> columnNames = new LinkedList<String>();

    final List<Element> subjectiveCategories = XMLUtils.filterToElements(_challengeDocument.getDocumentElement().getElementsByTagName("subjectiveCategory"));
    for (int catIdx = 0; catIdx < subjectiveCategories.size(); catIdx++) {
      final Element subjectiveElement = subjectiveCategories.get(catIdx);
      final String category = subjectiveElement.getAttribute("name");
      final String categoryTitle = subjectiveElement.getAttribute("title");
      columnNames.add(categoryTitle);

      final List<Element> goals = XMLUtils.filterToElements(subjectiveElement.getElementsByTagName("goal"));
      final Element categoryElement = (Element) _scoreDocument.getDocumentElement().getElementsByTagName(category).item(0);
      for (final Element scoreElement : XMLUtils.filterToElements(categoryElement.getElementsByTagName("score"))) {
        int numValues = 0;
        for (final Element goalElement : goals) {
          final String goalName = goalElement.getAttribute("name");
          final String value = scoreElement.getAttribute(goalName);
          if (null != value
              && !"".equals(value)) {
            numValues++;
            break;
          }
        } // end foreach goal

        final int teamNumber = Integer.parseInt(scoreElement.getAttribute("teamNumber"));
        if (!data.containsKey(teamNumber)) {
          final Integer[] counts = new Integer[subjectiveCategories.size()];
          Arrays.fill(counts, 0);
          data.put(teamNumber, counts);
        }

        if (numValues > 0
            || Boolean.parseBoolean(scoreElement.getAttribute("NoShow"))) {
          // if there is a score or a No Show, then increment counter for this
          // team/category combination
          data.get(teamNumber)[catIdx]++;
        } // end if score

      } // end foreach score row
    }// end foreach category

    // sort the data and map it into an easier form for table consumption
    final List<Integer[]> summaryData = new ArrayList<Integer[]>();

    final List<List<Integer>> tableData = new LinkedList<List<Integer>>();
    final List<Integer> teamNumbers = new ArrayList<Integer>(data.keySet());
    Collections.sort(teamNumbers);
    for (final Integer teamNumber : teamNumbers) {
      final Integer[] counts = data.get(teamNumber);
      for (int column = 0; column < counts.length; ++column) {
        // make sure that counts[column] rows exist in summaryData
        while (summaryData.size() < counts[column] + 1) {
          final Integer[] summaryRow = new Integer[columnNames.size()];
          Arrays.fill(summaryRow, 0);
          summaryData.add(summaryRow);
        }
        final Integer[] summaryRow = summaryData.get(counts[column]);
        ++summaryRow[column];
      }
      final List<Integer> row = new LinkedList<Integer>();
      row.add(teamNumber);
      for (final Integer value : counts) {
        row.add(value);
      }
      tableData.add(row);
    }

    return new TableModel[] { new CountTableModel(tableData, columnNames), new SummaryTableModel(summaryData, columnNames) };
  }

  /**
   * Table model for the summary data
   */
  private static final class SummaryTableModel extends AbstractTableModel {
    public SummaryTableModel(final List<Integer[]> summaryData, final List<String> columnNames) {
      _summaryData = summaryData;
      _columnNames = columnNames;
    }

    private final List<Integer[]> _summaryData;

    private final List<String> _columnNames;

    @Override
    public Class<?> getColumnClass(final int column) {
      if (0 == column) {
        return String.class;
      } else {
        return Integer.class;
      }
    }

    public int getRowCount() {
      return _summaryData.size();
    }

    public int getColumnCount() {
      return _columnNames.size() + 1;
    }

    @Override
    public String getColumnName(final int column) {
      if (column == 0) {
        return "# of scores";
      } else {
        return _columnNames.get(column - 1);
      }
    }

    public Object getValueAt(final int row, final int column) {
      if (column == 0) {
        return row;
      } else {
        final Integer[] rowData = _summaryData.get(row);
        return rowData[column - 1];
      }
    }
  }

  /**
   * Table model for the counts of scores for each team.
   */
  private static final class CountTableModel extends AbstractTableModel {
    public CountTableModel(final List<List<Integer>> data, final List<String> columnNames) {
      _data = data;
      _columnNames = columnNames;
    }

    private final List<List<Integer>> _data;

    private final List<String> _columnNames;

    @Override
    public Class<?> getColumnClass(final int column) {
      if (0 == column) {
        return String.class;
      } else {
        return Integer.class;
      }
    }

    public int getRowCount() {
      return _data.size();
    }

    public int getColumnCount() {
      return _columnNames.size() + 1;
    }

    @Override
    public String getColumnName(final int column) {
      if (column == 0) {
        return "Team Number";
      } else {

        return _columnNames.get(column - 1);
      }
    }

    public Object getValueAt(final int row, final int column) {
      final List<Integer> rowData = _data.get(row);
      return rowData.get(column);
    }
  }

  /**
   * Show all 0's in red.
   */
  private static final class CustomCellRenderer extends DefaultTableCellRenderer {
    public static final CustomCellRenderer INSTANCE = new CustomCellRenderer();

    @Override
    public Component getTableCellRendererComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final boolean hasFocus,
                                                   final int row,
                                                   final int column) {
      final Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value instanceof Number
          && ((Number) value).intValue() == 0) {
        comp.setForeground(Color.RED);
      } else {
        comp.setForeground(Color.BLACK);
      }
      return comp;
    }
  }
}
