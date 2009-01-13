package org.intermine.web.logic.results;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.intermine.metadata.FieldDescriptor;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreWriterInterMineImpl;
import org.intermine.objectstore.query.BagConstraint;
import org.intermine.objectstore.query.ConstraintOp;
import org.intermine.objectstore.query.ObjectStoreBag;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCloner;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryHelper;
import org.intermine.objectstore.query.QuerySelectable;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.pathquery.Path;
import org.intermine.util.DynamicUtil;
import org.intermine.util.TypeUtil;
import org.intermine.web.logic.Constants;
import org.intermine.web.logic.bag.InterMineBag;
import org.intermine.web.logic.profile.Profile;
import org.intermine.web.logic.results.flatouterjoins.MultiRow;
import org.intermine.web.logic.results.flatouterjoins.MultiRowFirstValue;
import org.intermine.web.logic.results.flatouterjoins.MultiRowValue;
import org.intermine.web.logic.session.SessionMethods;

/**
 * A pageable and configurable table of data.
 *
 * @author Andrew Varley
 * @author Kim Rutherford
 */
public class PagedTable
{
    private static final int FIRST_SELECTED_FIELDS_COUNT = 25;
    private WebTable webTable;
    private List<String> columnNames = null;
    private int startRow = 0;

    private int pageSize = Constants.DEFAULT_TABLE_SIZE;
    private List<Column> columns;
    private String tableid;

    private List<MultiRow<ResultsRow<MultiRowValue<ResultElement>>>> rows = null;

    // object ids that have been selected in the table
    // TODO this may be more memory efficient with an IntPresentSet
    // note: if allSelected != -1 then this map contains those objects that are NOT selected
    private Map<Integer, String> selectionIds = new LinkedHashMap<Integer, String>();

    // the index of the column the has all checkbox checked
    private int allSelected = -1;

    private String selectedClass;
    private int selectedColumn;

    /**
     * Construct a PagedTable with a list of column names
     * @param webTable the WebTable that this PagedTable will display
     */
    public PagedTable(WebTable webTable) {
        super();
        this.webTable = webTable;
    }

    /**
     * Construct a PagedTable with a list of column names
     * @param webTable the WebTable that this PagedTable will display
     * @param pageSize the number of records to show on each page.  Default value is 10.
     */
    public PagedTable(WebTable webTable, int pageSize) {
        super();
        this.webTable = webTable;
        this.pageSize = pageSize;
    }

    /**
     * @return the allSelected
     */
    public int getAllSelected() {
        return allSelected;
    }
    
    /**
     * Get the list of column configurations
     *
     * @return the List of columns in the order they are to be displayed
     */
    public List<Column> getColumns() {
        return Collections.unmodifiableList(getColumnsInternal());
    }

    private List<Column> getColumnsInternal() {
        if (columns == null) {
            columns = webTable.getColumns();
        }
        return columns;
    }

    /**
     * Return the column names
     * @return the column names
     */
    public List<String> getColumnNames() {
        if (columnNames == null) {
            columnNames = new ArrayList<String>();
            Iterator<Column> iter = getColumns().iterator();
            while (iter.hasNext()) {
                String columnName = iter.next().getName();
                columnNames.add(columnName);
            }
        }
        return columnNames;
    }

    /**
     * Return the number of visible columns.  Used by JSP pages.
     * @return the number of visible columns.
     */
    public int getVisibleColumnCount() {
        int count = 0;
        for (Iterator<Column> i = getColumnsInternal().iterator(); i.hasNext();) {
            Column obj = i.next();
            if (obj.isVisible()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Move a column left
     *
     * @param index the index of the column to move
     */
    public void moveColumnLeft(int index) {
        if (index > 0 && index <= getColumnsInternal().size() - 1) {
            getColumnsInternal().add(index - 1, getColumnsInternal().remove(index));
        }
    }

    /**
     * Move a column right
     *
     * @param index the index of the column to move
     */
    public void moveColumnRight(int index) {
        if (index >= 0 && index < getColumnsInternal().size() - 1) {
            getColumnsInternal().add(index + 1, getColumnsInternal().remove(index));
        }
    }
    
    /**
     * Swap 2 columns
     * 
     * @param index1 the index of column 1
     * @param index2 the index of column 2
     */
    public void swapColumns(int index1, int index2) {
        if (index2 < index1) {
            int tmp = index2;
            index2 = index1;
            index1 = tmp;
        }
        getColumnsInternal().add(index1, getColumnsInternal().remove(index2));
        getColumnsInternal().add(index2, getColumnsInternal().remove(index1 + 1));
    }

    /**
     * Set the page size of the table
     *
     * @param pageSize the page size
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        startRow = (startRow / pageSize) * pageSize;
        updateRows();
    }

    /**
     * Get the page size of the current page
     *
     * @return the page size
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Get the index of the first row of this page
     * @return the index
     */
    public int getStartRow() {
        return startRow;
    }

    /**
     * Get the page index.
     * @return current page index
     */
    public int getPage() {
        return (startRow / pageSize);
    }

    /**
     * Set the page size and page together.
     *
     * @param page page number
     * @param size page size
     */
    public void setPageAndPageSize(int page, int size) {
        this.pageSize = size;
        this.startRow = size * page;
        updateRows();
    }

    /**
     * Get the index of the last row of this page
     * @return the index
     */
    public int getEndRow() {
        return startRow + getRows().size() - 1;
    }

    /**
     * Go to the first page
     */
    public void firstPage() {
        startRow = 0;
        updateRows();
    }

    /**
     * Check if were are on the first page
     * @return true if we are on the first page
     */
    public boolean isFirstPage() {
        return (startRow == 0);
    }

    /**
     * Go to the last page
     */
    public void lastPage() {
        startRow = ((getExactSize() - 1) / pageSize) * pageSize;
        updateRows();
    }

    /**
     * Check if we are on the last page
     * @return true if we are on the last page
     */
    public boolean isLastPage() {
        return (!isSizeEstimate() && getEndRow() == getEstimatedSize() - 1);
    }

    /**
     * Go to the previous page
     */
    public void previousPage() {
        if (startRow >= pageSize) {
            startRow -= pageSize;
        }
        updateRows();
    }

    /**
     * Go to the next page
     */
    public void nextPage() {
        startRow += pageSize;
        updateRows();
    }

    /**
     * Return the currently visible rows of the table as a List of Lists of ResultElement objects.
     *
     * @return the resultElementRows of the table
     */
    public List<MultiRow<ResultsRow<MultiRowValue<ResultElement>>>> getRows() {
        if (rows == null) {
            updateRows();
        }
        return rows;
    }

    /**
     * Return all the resultElementRows of the table as a List of Lists.
     *
     * @return all the resultElementRows of the table
     */
    public WebTable getAllRows() {
        return webTable;
    }

    /**
     * Get the (possibly estimated) number of resultElementRows of this table
     * @return the number of resultElementRows
     */
    public int getEstimatedSize() {
        return webTable.getEstimatedSize();
    }

    /**
     * Check whether the result of getSize is an estimate
     * @return true if the size is an estimate
     */
    public boolean isSizeEstimate() {
        return webTable.isSizeEstimate();
    }

    /**
     * Get the exact number of resultElementRows of this table
     * @return the number of resultElementRows
     */
    public int getExactSize() {
        return webTable.size();
    }

    /**
     * Add an object id and its field value
     * that has been selected in the table.
     * @param objectId the id to select
     * @param columnIndex the column of the selected id
     */
    public void selectId(Integer objectId, int columnIndex) {
        if (allSelected == -1) {
            ResultElement resultElement = findIdInVisible(objectId);
            if (resultElement != null) {
                if (resultElement.getField() == null) {
                    selectionIds.put(objectId, null);
                } else {
                    selectionIds.put(objectId, resultElement.getField().toString());
                }
                setSelectedColumn(columnIndex);
                setSelectedClass(TypeUtil.unqualifiedName(columns.get(columnIndex).getType()
                                    .getName()));
            }
        } else {
            // remove because the all checkbox is on
            selectionIds.remove(objectId);
        }
    }

    /**
     * Remove the object with the given object id from the list of selected objects.
     * @param objectId the object store id
     */
    public void deSelectId(Integer objectId) {
       if (allSelected == -1) {
           selectionIds.remove(objectId);
           if (selectionIds.size() <= 0) {
                setSelectedClass(null);
                setSelectedColumn(-1);
            }
       } else {
           // add because the all checkbox is on
           ResultElement resultElement = findIdInVisible(objectId);
           if (resultElement != null) {
               if (resultElement.getField() == null) {
                   selectionIds.put(objectId, null);
               } else {
                   selectionIds.put(objectId, resultElement.getField().toString());
               }
           }
           if (isEmptySelection()) {
               clearSelectIds();
           }
       }
    }

    /**
     * Search the visible rows and return the first ResultElement with the given ID./
     */
    private ResultElement findIdInVisible(Integer id) {
        for (MultiRow<ResultsRow<MultiRowValue<ResultElement>>> mr : getRows()) {
            for (ResultsRow<MultiRowValue<ResultElement>> rv : mr) {
                for (MultiRowValue<ResultElement> mrv : rv) {
                    // We don't need to check other MultiRowValues are we've already seen them
                    if (mrv instanceof MultiRowFirstValue) {
                        ResultElement resultElement = mrv.getValue();
                        if ((resultElement != null) && (resultElement.getId().equals(id))
                            && (resultElement.isKeyField())) {
                            return resultElement;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Return the fields for the first selected objects.  Return the first
     * FIRST_SELECTED_FIELDS_COUNT fields.  If there are more than that, append "...".  If a whole
     * column is selected return an empty list, the jsp will display and 'All selected' message.
     * @param os the ObjectStore
     * @param classKeysMap map of key field for a given class name
     * @return the list
     */
    public List<String> getFirstSelectedFields(ObjectStore os,
                                               Map<String, List<FieldDescriptor>> classKeysMap) {
        Set<String> retList = new LinkedHashSet<String>();
        // only find values if individual elements selected, not if whole column selected
        if (allSelected == -1) {
            Iterator<SelectionEntry> selectedEntryIter = selectedEntryIterator();
            boolean seenNullField = false;
            while (selectedEntryIter.hasNext()) {
                if (retList.size() < FIRST_SELECTED_FIELDS_COUNT) {
                    SelectionEntry entry = selectedEntryIter.next();
                    String fieldValue = entry.fieldValue;
                    if (fieldValue == null) {
                        // the select column doesn't have a value for this object; use class keys to
                        // find a value
                        InterMineObject object;
                        try {
                            Integer id = entry.id;
                            object = os.getObjectById(id);
                            if (object == null) {
                                throw new RuntimeException("internal error - unknown object id: "
                                        + id);
                            }
                            String classKeyFieldValue = findClassKeyValue(classKeysMap, object);
                            if (classKeyFieldValue == null) {
                                seenNullField = true;
                            } else {
                                retList.add(classKeyFieldValue);
                            }
                        } catch (ObjectStoreException e) {
                            seenNullField = true;
                        }
                    } else {
                        retList.add(fieldValue);
                    }
                } else {
                    retList.add("...");
                    return new ArrayList<String>(retList);
                }
            }
            if (seenNullField) {
                // if there are null that we can't find a field value for, just append "..." because
                // showing "[no value]" in the webapp is of no value
                retList.add("...");
            }
        }
        return new ArrayList<String>(retList);
    }

    /**
     * Return the first non-null class key field for the object with the given id.
     */
    private String findClassKeyValue(Map<String, List<FieldDescriptor>> classKeysMap,
                                     InterMineObject object) {
        try {
            String objectClassName = DynamicUtil.getFriendlyName(object.getClass());
            List<FieldDescriptor> classKeyFds = classKeysMap.get(objectClassName);
            for (FieldDescriptor fd: classKeyFds) {
                Object value = TypeUtil.getFieldValue(object, fd.getName());
                if (value != null) {
                    return value.toString();
                }
            }
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Return selected object ids of the current page as a String[], needed for jsp multibox.
     * @return selected ids as Strings.
     */
    public String[] getCurrentSelectedIdStrings() {
        return getCurrentSelectedIdStringsList().toArray(new String[0]);
    }

    /**
     * Return selected object ids of the current page as a List.
     * @return the list.
     */
    public List<String> getCurrentSelectedIdStringsList() {
        List<String> selected = new ArrayList<String>();
        if (allSelected == -1) {
            if (!selectionIds.isEmpty()) {
                for (MultiRow<ResultsRow<MultiRowValue<ResultElement>>> multiRow : getRows()) {
                    for (ResultsRow<MultiRowValue<ResultElement>> resultsRow : multiRow) {
                        for (MultiRowValue<ResultElement> multiRowValue : resultsRow) {
                            if (multiRowValue instanceof MultiRowFirstValue) {
                                ResultElement resElt = multiRowValue.getValue();
                                if (resElt != null) {
                                    if (selectionIds.containsKey(resElt.getId())) {
                                        selected.add(resElt.getId().toString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            for (MultiRow<ResultsRow<MultiRowValue<ResultElement>>> multiRow : getRows()) {
                for (ResultsRow<MultiRowValue<ResultElement>> resultsRow : multiRow) {
                    MultiRowValue<ResultElement> multiRowValue = resultsRow.get(allSelected);
                    if (multiRowValue instanceof MultiRowFirstValue) {
                        ResultElement resElt = multiRowValue.getValue();
                        if (resElt != null) {
                            if (!selectionIds.containsKey(resElt.getId())) {
                                selected.add(resElt.getId().toString());
                            }
                        }
                    }
                }
            }
            selected.add(columns.get(allSelected).getColumnId());
        }
        return selected;
    }

    /**
     * Clear the table selection
     */
    public void clearSelectIds() {
        selectionIds.clear();
        allSelected = -1;
        selectedClass = null;
    }

    private class SelectionEntry
    {
        Integer id;
        String fieldValue;
    }

    /**
     * Return an Iterator over the selected id/fieldname pairs
     */
    private Iterator<SelectionEntry> selectedEntryIterator() {
        if (allSelected == -1) {
            return new Iterator<SelectionEntry>() {
                Iterator<Map.Entry<Integer, String>> selectionIter =
                    selectionIds.entrySet().iterator();
                public boolean hasNext() {
                    return selectionIter.hasNext();
                }
                public SelectionEntry next() {
                    SelectionEntry retEntry = new SelectionEntry();
                    Map.Entry<Integer, String> entry = selectionIter.next();
                    retEntry.id = entry.getKey();
                    retEntry.fieldValue = entry.getValue();
                    return retEntry;
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
        return new Iterator<SelectionEntry>() {
            SelectionEntry nextEntry = null;
            int currentIndex = 0;
            int multiRowIndex = 0;
            {
                moveToNext();
            }

            private void moveToNext() {
                while (true) {
                    try {
                        MultiRow<ResultsRow<MultiRowValue<ResultElement>>> multiRow
                            = getAllRows().getResultElements(currentIndex);
                        if (multiRow.size() <= multiRowIndex) {
                            currentIndex++;
                            multiRowIndex = 0;
                        } else {
                            ResultsRow<MultiRowValue<ResultElement>> row
                                = multiRow.get(multiRowIndex);
                            MultiRowValue<ResultElement> value = row.get(allSelected);
                            if (value instanceof MultiRowFirstValue) {
                                ResultElement element = value.getValue();
                                Integer elementId = element.getId();
                                if (!selectionIds.containsKey(elementId)) {
                                    nextEntry = new SelectionEntry();
                                    nextEntry.id = elementId;
                                    if (element.getField() == null) {
                                        nextEntry.fieldValue = null;
                                    } else {
                                        nextEntry.fieldValue = element.getField().toString();
                                    }
                                    break;
                                }
                            }
                            multiRowIndex++;
                        }
                    } catch (IndexOutOfBoundsException e) {
                        nextEntry = null;
                        break;
                    }
                }
            }

            public boolean hasNext() {
                return nextEntry != null;
            }

            public SelectionEntry next() {
                SelectionEntry retVal = nextEntry;
                moveToNext();
                return retVal;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };

    }

    /**
     * Return an Iterator over the selected Ids
     * @return the Iterator
     */
    public Iterator<Integer> selectedIdsIterator() {
        return new Iterator<Integer>() {
            Iterator<SelectionEntry> selectedEntryIter = selectedEntryIterator();
            public boolean hasNext() {
               return selectedEntryIter.hasNext();
            }
            public Integer next() {
                return selectedEntryIter.next().id;
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * If a whole column is selected, return its index, otherwise return -1.
     * @return the index of the column that is selected
     */
    public int getAllSelectedColumn() {
        if (selectionIds.isEmpty()) {
            return allSelected;
        }
        return -1;
    }

    /**
     * Select a whole column.
     * @param columnSelected the column index
     */
    public void setAllSelectedColumn(int columnSelected) {
        if (columnSelected == -1) {
            selectedClass = null;
        } else {
            Class<?> columnClass = getAllRows().getColumns().get(columnSelected).getType();
            selectedClass = TypeUtil.unqualifiedName(columnClass.getName());
        }
        this.allSelected = columnSelected;
    }

    /**
     * @return the selectedColumn
     */
    public int getSelectedColumn() {
        return selectedColumn;
    }

    /**
     * @param selectedColumn the selectedColumn to set
     */
    public void setSelectedColumn(int selectedColumn) {
        this.selectedColumn = selectedColumn;
    }

    /**
     * @return the selectedClass
     */
    public String getSelectedClass() {
        return selectedClass;
    }

    /**
     * @param selectedClass the selectedClass to set
     */
    public void setSelectedClass(String selectedClass) {
        this.selectedClass = selectedClass;
    }

    /**
     * Set the rows fields to be a List of Lists of values from ResultElement objects from
     * getResultElementRows().
     */
    private void updateRows() {
        List<MultiRow<ResultsRow<MultiRowValue<ResultElement>>>> newRows
            = new ArrayList<MultiRow<ResultsRow<MultiRowValue<ResultElement>>>>();
        String invalidStartMessage = "Invalid start row of table: " + getStartRow();
        if (getStartRow() < 0) {
            throw new PageOutOfRangeException(invalidStartMessage);
        }

        try {
            if (getStartRow() == 0) {
                // no problem - 0 is always valid
            } else {
                getAllRows().getResultElements(getStartRow());
            }
        } catch (IndexOutOfBoundsException e) {
            throw new PageOutOfRangeException(invalidStartMessage);
        }

        for (int i = getStartRow(); i < getStartRow() + getPageSize(); i++) {
            try {
                newRows.add(getAllRows().getResultElements(i));
            } catch (IndexOutOfBoundsException e) {
                // we're probably at the end of the results object, so stop looping
                break;
            }
        }
        this.rows = newRows;
    }

    /**
     * Return the maximum retrievable index for this PagedTable.  This will only ever return less
     * than getExactSize() if the underlying data source has a restriction on the maximum index
     * that can be retrieved.
     * @return the maximum retrieved index
     */
    public int getMaxRetrievableIndex() {
        return webTable.getMaxRetrievableIndex();
    }

    /**
     * Return the class from the data model for the data displayed in indexed column.
     * This may be the parent class of a field e.g. if column displays A.field where
     * field is a String and A is a class in the model this method will return A.
     * @param index of column to find type for
     * @return the class or parent class for the indexed column
     */
    public Class<?> getTypeForColumn(int index) {
        return webTable.getColumns().get(index).getType();
    }

    /**
     * Set the column names
     * @param columnNames a list of Strings
     */
    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    /**
     * @return the webTable
     */
    public WebTable getWebTable() {
        return webTable;
    }

    /**
     * @return the tableid
     */
    public String getTableid() {
        return tableid;
    }

    /**
     * @param tableid the tableid to set
     */
    public void setTableid(String tableid) {
        this.tableid = tableid;
    }

    /**
     * Returns indexes of columns, that should be displayed.
     * @return indexes
     */
    public List<Integer> getVisibleIndexes() {
        List<Integer> ret = new ArrayList<Integer>();
        for (int i = 0; i < getColumns().size(); i++) {
            if (getColumns().get(i) != null && getColumns().get(i).isVisible()) {
                ret.add(new Integer(getColumns().get(i).getIndex()));
            }
        }

        return ret;
    }

    /**
     * Return true if and only if nothing is selected
     * @return true if and only if nothing is selected
     */
    public boolean isEmptySelection() {
        return !selectedIdsIterator().hasNext();
    }

    /**
     * Return true if a whole column is selected either by selecting the whole column checkbox or
     * by selecting each row individually.
     * @return if a column is selected
     */
    public boolean isAllRowsSelected() {
        if (allSelected == -1) {
            int selectedCount = selectionIds.size();
            if (selectedCount > 0) {
                // If there is at least one more row than there are selected elements, it's not
                // all selected.  We use get()/try/catch to avoid calling size() which can be slow
                try {
                    getAllRows().get(selectedCount);
                    // success
                    return false;
                } catch (IndexOutOfBoundsException e) {
                    return true;
                }
            }
            return false;
        }
        return selectionIds.size() == 0;
    }

    /**
     * Return a Query that produces a Results object of the selected objects.
     * @return the query
     */
    public Query getBagCreationQuery() {
        if (allSelected == -1) {
            Query query = new Query();
            QueryClass qc = new QueryClass(InterMineObject.class);
            query.addFrom(qc);
            query.addToSelect(qc);

            BagConstraint bc = new BagConstraint(new QueryField(qc, "id"),
                                                 ConstraintOp.IN, selectionIds.keySet());

            query.setConstraint(bc);

            return query;
        }
        WebResults webResults = (WebResults) getAllRows();
        Results results = webResults.getInterMineResults();
        Query origQuery = results.getQuery();
        Query newQuery = QueryCloner.cloneQuery(origQuery);

        Map<String, QuerySelectable> pathToQueryNodeMap = webResults.getPathToQueryNode();
        Path path = columns.get(allSelected).getPath().getPrefix();
        QuerySelectable qn = pathToQueryNodeMap.get(path.toStringNoConstraints());

        int nodeIndex = origQuery.getSelect().indexOf(qn);

        QuerySelectable newSelectable = newQuery.getSelect().get(nodeIndex);
        newQuery.clearSelect();
        QueryClass newNode;
        //if (newSelectable instanceof QueryClass) {
            newNode = (QueryClass) newSelectable;
        /*} else {
            QueryObjectPathExpression qope = (QueryObjectPathExpression) newSelectable;
            // We need to morph the query from an outer join to an inner join. This will not
            // affect the results, which are ids from the path expression, except by removing
            // "null".
            newNode = new QueryClass(qope.getType());
            newQuery.addFrom(newNode);
            QueryClass lastQc = newNode;
            QueryObjectPathExpression nextQope = qope.getQope();
            while (nextQope != null) {
                QueryClass newQc = new QueryClass(nextQope.getType());
                newQuery.addFrom(newQc);
                QueryHelper.addAndConstraint(newQuery, new ContainsConstraint(
                            new QueryObjectReference(newQc, qope.getFieldName()),
                            ConstraintOp.CONTAINS, lastQc));
                qope = nextQope;
                lastQc = newQc;
                nextQope = qope.getQope();
            }
            QueryClass rootQc = qope.getQueryClass();
            QueryHelper.addAndConstraint(newQuery, new ContainsConstraint(
                        new QueryObjectReference(rootQc, qope.getFieldName()),
                        ConstraintOp.CONTAINS, lastQc));
        }*/

        newQuery.addToSelect(newNode);
        BagConstraint bc =
            new BagConstraint(new QueryField(newNode, "id"),
                              ConstraintOp.NOT_IN, selectionIds.keySet());
        QueryHelper.addAndConstraint(newQuery, bc);
        return newQuery;

    }

    /**
     * @return the selectionIds
     */
    public Map<Integer, String> getSelectionIds() {
        return selectionIds;
    }

    /**
     * @param selectionIds the selectionIds to set
     */
    public void setSelectionIds(Map<Integer, String> selectionIds) {
        this.selectionIds = selectionIds;
    }

    /**
     * Adds the selected objects to the given bag in the given objectstore.
     *
     * @param osw the ObjectStoreWriter
     * @param osb the bag to write to
     * @throws ObjectStoreException if an error occurs
     */
    public void addSelectedToBag(ObjectStoreWriter osw, ObjectStoreBag osb)
    throws ObjectStoreException {
        if (allSelected == -1) {
            osw.addAllToBag(osb, selectionIds.keySet());
        } else {
            osw.addToBagFromQuery(osb, getBagCreationQuery());
        }
    }

    /**
     * Adds the selected objects to the given bag in the given objectstore.
     * Assumes table contains same type of object as bag.
     * @param os object store
     * @param bag bag to add to
     * @throws ObjectStoreException if an error occurs
     */
    public void addSelectedToBag(ObjectStore os, InterMineBag bag)
    throws ObjectStoreException {
        ObjectStoreWriter osw = null;
        try {
            osw = new ObjectStoreWriterInterMineImpl(os);
            ObjectStoreBag osb = bag.getOsb();
            addSelectedToBag(osw, osb);
        } finally {
            if (osw != null) {
                osw.close();
            }
        }
    }


    /**
     * remove the selected elements from the bag.  No action is taken if user selects all records
     * to be deleted.
     *
     * @param bagName name of bag
     * @param profile user's profile
     * @param os object store
     * @param session user's session
     * @param bagSize size of bag
     * @return number of objects removed from the list
     * @exception Exception if the application business logic throws
     *  an exception
     */
    public int removeFromBag(String bagName, Profile profile, ObjectStore os, HttpSession session,
                              int bagSize)
    throws Exception {
        int i = 0;
        if (bagSize == selectionIds.size()) {
            return i;
        }

        Map<String, InterMineBag> savedBags = profile.getSavedBags();
        InterMineBag interMineBag = savedBags.get(bagName);
        ObjectStoreWriter osw = null;
        try {
            osw = new ObjectStoreWriterInterMineImpl(os);
            if (allSelected != -1) {
                Set<Integer> keys = selectionIds.keySet();
                for (Integer key : interMineBag.getContentsAsIds()) {
                    if (!keys.contains(key)) {
                        osw.removeFromBag(interMineBag.getOsb(), key);
                        i++;
                    }
                }
            } else {
                for (Integer key : selectionIds.keySet()) {
                    osw.removeFromBag(interMineBag.getOsb(), key);
                    i++;
                }
            }
        } finally {
            if (osw != null) {
                osw.close();
            }
        }
        SessionMethods.invalidateBagTable(session, bagName);
        return i;
    }
}
