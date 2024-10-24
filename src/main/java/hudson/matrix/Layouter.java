/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.matrix;

import java.util.List;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Used to assist thegeneration of config table.
 *
 * <p>
 * {@link Axis Axes} are split into four groups.
 * {@link #x Ones that are displayed as columns},
 * {@link #y Ones that are displayed as rows},
 * {@link #z Ones that are listed as bullet items inside table cell},
 * and those which only have one value, and therefore doesn't show up
 * in the table. 
 *
 * <p>
 * Because of object reuse inside {@link Layouter}, this class is not thread-safe.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Layouter<T> {
    public final List<Axis> x,y,z;
    /**
     * Axes that only have one value.
     */
    private final List<Axis> trivial = new ArrayList<Axis>();
    /**
     * Number of data columns and rows.
     */
    private int xSize, ySize, zSize;

    /*
     * Combinations that are currently active in project
     */
    private HashSet<String> activeCombinations;


    public Layouter(List<Axis> x, List<Axis> y, List<Axis> z, HashSet<String> activeCombinations) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.activeCombinations = activeCombinations;
        init();
    }

    /**
     * Automatically split axes to x,y, and z.
     */
    public Layouter(AxisList axisList, HashSet<String> activeCombinations) {
        x = new ArrayList<Axis>();
        y = new ArrayList<Axis>();
        z = new ArrayList<Axis>();

        List<Axis> nonTrivialAxes = new ArrayList<Axis>();
        for (Axis a : axisList) {
            if(a.size()>1)
                nonTrivialAxes.add(a);
            else
                trivial.add(a);
        }

        switch(nonTrivialAxes.size()) {
        case 0:
            break;
        case 1:
            z.add(nonTrivialAxes.get(0));
            break;
        case 2:
            // use the longer axis in Y
            Axis a = nonTrivialAxes.get(0);
            Axis b = nonTrivialAxes.get(1);
            x.add(a.size() > b.size() ? b : a);
            y.add(a.size() > b.size() ? a : b);
            break;
        default:
            // for size > 3, use x and y, and try to pack y more
            for( int i=0; i<nonTrivialAxes.size(); i++ )
                (i%3==1?x:y).add(nonTrivialAxes.get(i));
        }
        
        this.activeCombinations = activeCombinations;
        init();
    }

    private void init() {
        xSize = calculateChildrenNumber(x,-1);
        ySize = calculateChildrenNumber(y,-1);
        zSize = calculateChildrenNumber(z,-1);
    }

    /**
     * Computes the width of n-th X-axis.
     */
    public int width(int n) {
        return calculateChildrenNumber(x,n);
    }

    /**
     * Computes the repeat count of n-th X-axis.
     */
    public int repeatX(int n) {
        int w = 1;
        for( n--; n>=0; n-- )
            w *= x.get(n).size();
        return w;
    }

    /**
     * Computes the height of n-th Y-axis.
     */    
    public int height(int n) {
        return calculateChildrenNumber(y,n);
    }

    public int getVisibleCells(int rowIndex, int heightIndex, Row row) {
        int height = height(heightIndex);
        if(height == 1) {
            return row.getVisibleChildren();
        }

        List<Row> rows = this.getRows();
        int visibleCellsInGroup = 0;
        for(int i = rowIndex; i < rowIndex + height; i++) {
            Row r = rows.get(i);
            visibleCellsInGroup += r.getVisibleCells();
        }

        return visibleCellsInGroup;
    }

    private int calculateChildrenNumber(List<Axis> listAxis, int startIndex) {
        this.getRows().get(startIndex);

        int size = 1;
        for( startIndex++ ; startIndex<listAxis.size(); startIndex++ )
            size *= listAxis.get(startIndex).size();
        return size;
    }

    /**
     * Gets list of {@link Row}s to be displayed.
     *
     * The {@link Row} object is reused, so every value
     * in collection returns the same object (but with different values.)
     */
    public List<Row> getRows() {
        return new AbstractList<Row>() {
            final Row row = new Row();
            public Row get(int index) {
                row.index = index;
                return row;
            }

            public int size() {
                return ySize;
            }
        };
    }

    /**
     * Represents a row, which is a collection of {@link Column}s.
     */
    public final class Row extends AbstractList<Column> {
        private int index;
        final Column col = new Column();

        @Override
        public Column get(int index) {
            col.xp = index;
            col.yp = Row.this.index;
            return col;
        }

        @Override
        public int size() {
            return xSize;
        }

        
        public int getIndex() {
            return index;
        }

        public Integer getVisibleCells() {
            int cellsNumber = 0;
            for(int i = 1; i <= this.size(); i++) {
                Column col = this.get(i);
                cellsNumber += col.getVisibleCells();
            }
            return cellsNumber;
        }

        public Integer getVisibleChildren() {
            int cellsNumber = 0;
            for(int i = 1; i <= this.size(); i++) {
                Column col = this.get(i);
                cellsNumber += col.getVisibleCells();
            }
            return cellsNumber;
        }

        public String drawYHeader(int n) {
            int base = calculateChildrenNumber(y,n);
            if(index/base==(index-1)/base && index!=0)  return null;    // no need to draw a new value

            Axis axis = y.get(n);
            return axis.value((index/base)%axis.getValues().size());
        }
    }

    protected abstract T getT(Combination c);

    public final class Column extends AbstractList<T> {
        /**
         * Cell position.
         */
        private int xp,yp;

        private final Map<String,String> m = new HashMap<String,String>();

        public T get(int zp) {
            return getT(getCombination(zp));
        }

        public Combination getCombination(int zp) {
            m.clear();
            buildMap(xp,x);
            buildMap(yp,y);
            buildMap(zp,z);
            for (Axis a : trivial) {
                if (a.size() > 0) {
                    m.put(a.getName(), a.value(0));
                }
            }
            return new Combination(m);
        }

        private void buildMap(int p, List<Axis> axes) {
            int n = p;
            for( int i= axes.size()-1; i>=0; i-- ) {
                Axis a = axes.get(i);
                m.put(a.getName(), a.value(n%a.size()));
                n /= a.size();
            }
        }

        public Integer getVisibleCells() {
            Integer cellsCount = 0;
            for(int i = 1; i <= this.size(); i++) {
                Combination c = this.getCombination(i);
                cellsCount += activeCombinations.contains(c.toString()) ? 1 : 0;
            }
            return cellsCount;
        }

        public int size() {
            return zSize;
        }
    }
}
