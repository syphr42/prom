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

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

public class PropertyChangeStackTest
{
    private static File TEST_DATA_DIR = new File("target/test-data");
    private static File TEST_PROPS = new File(TEST_DATA_DIR, PropertyChangeStack.class.getSimpleName() + ".properties");
    
    private ManagedProperty<Key> manager;
    private PropertyChangeStack<Key> stack;
    
    @Before
    public void setup() throws IOException
    {
        PropertiesManager<Key> mainManager = PropertiesManagers.newManager(TEST_PROPS, Key.class);
        mainManager.load();
        
        manager = mainManager.getManagedProperty(Key.SOME_KEY);
        stack = new PropertyChangeStack<Key>();
        
        stack.register(manager);
    }
    
    @Test
    public void testIsModified()
    {
        Assert.assertFalse("Stack incorrectly reports modification", stack.isModified());
        
        manager.setProperty("some other value");
        Assert.assertTrue("Stack incorrectly reports no modification", stack.isModified());
    }

    @Test
    public void testGetCurrentValue()
    {
        Assert.assertEquals("Incorrect current value before modification",
                            Key.SOME_KEY.getDefaultValue(),
                            stack.getCurrentValue());

        final String value = "some other value";
        manager.setProperty(value);
        Assert.assertEquals("Incorrect current value after modification",
                            value,
                            stack.getCurrentValue());
    }

    @Test
    public void testGetSavedValue()
    {
        Assert.assertEquals("Incorrect saved value before modification",
                            Key.SOME_KEY.getDefaultValue(),
                            stack.getSavedValue());

        final String value = "some other value";
        manager.setProperty(value);
        Assert.assertEquals("Incorrect saved value before modification",
                            Key.SOME_KEY.getDefaultValue(),
                            stack.getSavedValue());
    }
    
    @Test
    public void testIsUndoPossible()
    {
        Assert.assertFalse("Undo should not be possible before modification",
                            stack.isUndoPossible());

        final String value = "some other value";
        manager.setProperty(value);
        Assert.assertTrue("Undo should be possible before modification",
                            stack.isUndoPossible());
    }
    
    @Test
    public void testUndo()
    {
        final String value = "some other value";
        manager.setProperty(value);
        Assert.assertEquals("Value should equal original value after undo",
                            Key.SOME_KEY.getDefaultValue(),
                            stack.undo());
    }
    
    @Test(expected=IllegalStateException.class)
    public void testUndoNotPossible()
    {
        stack.undo();
    }
    
    @Test
    public void testIsRedoPossible()
    {
        final String value1 = "some other value";
        manager.setProperty(value1);
        Assert.assertFalse("Redo should not be possible before undo",
                           stack.isRedoPossible());

        stack.undo();
        Assert.assertTrue("Redo should be possible after undo without further modification",
                          stack.isRedoPossible());

        final String value2 = "some new value";
        manager.setProperty(value2);
        Assert.assertFalse("Redo should not be possible after modification",
                           stack.isRedoPossible());
    }

    @Test
    public void testRedo()
    {
        final String value = "some other value";
        manager.setProperty(value);
        stack.undo();

        Assert.assertEquals("Value should equal new value after redo",
                            value,
                            stack.redo());
    }
    
    @Test(expected=IllegalStateException.class)
    public void testRedoNotPossible()
    {
        stack.redo();
    }
    
    public static enum Key implements Defaultable
    {
        SOME_KEY("some key's value!");

        private String defaultValue;

        private Key(String defaultValue)
        {
            this.defaultValue = defaultValue;
        }

        @Override
        public String getDefaultValue()
        {
            return defaultValue;
        }
    }
}
