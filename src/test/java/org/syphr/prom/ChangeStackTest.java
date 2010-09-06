/*
 * Copyright 2010 Gregory P. Moyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.syphr.prom;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

public class ChangeStackTest
{
    private static final String INITIAL_VALUE = "initial value";
    private static final String VALUE_1 = "value 1";
    private static final String VALUE_2 = "value 2";
    
    private ChangeStack<String> stack;
    
    @Before
    public void setup()
    {
        stack = new ChangeStack<String>(INITIAL_VALUE);
    }
    
    @Test
    public void testIsModified()
    {
        Assert.assertFalse("Stack incorrectly reports modification", stack.isModified());
        
        stack.push(VALUE_1);
        Assert.assertTrue("Stack incorrectly reports no modification", stack.isModified());
    }

    @Test
    public void testGetCurrentValue()
    {
        Assert.assertEquals("Incorrect current value before modification",
                            INITIAL_VALUE,
                            stack.getCurrentValue());

        stack.push(VALUE_1);
        Assert.assertEquals("Incorrect current value after modification",
                            VALUE_1,
                            stack.getCurrentValue());
    }

    @Test
    public void testGetSavedValue()
    {
        Assert.assertEquals("Incorrect saved value before modification",
                            INITIAL_VALUE,
                            stack.getSavedValue());

        stack.push(VALUE_1);
        Assert.assertEquals("Incorrect saved value before modification",
                            INITIAL_VALUE,
                            stack.getSavedValue());
    }

    @Test
    public void testIsUndoPossible()
    {
        Assert.assertFalse("Undo should not be possible before modification",
                           stack.isUndoPossible());

        stack.push(VALUE_1);
        Assert.assertTrue("Undo should be possible before modification",
                          stack.isUndoPossible());
    }
    
    @Test
    public void testUndo()
    {
        stack.push(VALUE_1);
        Assert.assertEquals("Value should equal original value after undo",
                            INITIAL_VALUE,
                            stack.undo());
    }
    
    @Test
    public void testUndoNotPossible()
    {
        Assert.assertEquals("Value should equal current because undo is not possible",
                            stack.getCurrentValue(),
                            stack.undo());
    }
    
    @Test
    public void testIsRedoPossible()
    {
        stack.push(VALUE_1);
        Assert.assertFalse("Redo should not be possible before undo",
                           stack.isRedoPossible());

        stack.undo();
        Assert.assertTrue("Redo should be possible after undo without further modification",
                          stack.isRedoPossible());

        stack.push(VALUE_2);
        Assert.assertFalse("Redo should not be possible after modification",
                           stack.isRedoPossible());
    }

    @Test
    public void testRedo()
    {
        stack.push(VALUE_1);
        stack.undo();

        Assert.assertEquals("Value should equal new value after redo",
                            VALUE_1,
                            stack.redo());
    }
    
    @Test
    public void testRedoNotPossible()
    {
        Assert.assertEquals("Value should equal current because redo is not possible",
                            stack.getCurrentValue(),
                            stack.redo());
    }
}
