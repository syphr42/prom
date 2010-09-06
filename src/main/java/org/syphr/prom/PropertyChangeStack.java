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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// TODO need a history limit

/**
 * This class provide a stack of changes to a single property with the ability
 * to move back and forth through the stack.
 * 
 * @param <T>
 *            the type of the Enum that represents the property keys used by the
 *            manager to which an instance of this class will be registered
 * 
 * @author Gregory P. Moyer
 */
/* default */class PropertyChangeStack<T extends Enum<T>>
{
    /**
     * The stack of changes being tracked.
     */
    private final List<String> stack = new ArrayList<String>();

    /**
     * The current location in the stack. This will change as operations are
     * performed (such as changing the value, undoing a change, or redoing a
     * change).
     */
    private int currentLoc = -1;

    /**
     * The location of the last value that was saved. When a save event occurs,
     * this will be updated to the current location. It is assumed that this is
     * the current location at the time the stack is registered.
     */
    private int savedLoc = -1;

    /**
     * Register this stack with the given manager. One registration has
     * occurred, this instance may not be registered again to either the same
     * manager or any other.
     * 
     * @param manager
     *            the manager to which this instance will listen for events
     * @throws IllegalStateException
     *             if this stack has already been registered
     */
    public synchronized void register(ManagedProperty<T> manager) throws IllegalStateException
    {
        if (currentLoc >= 0)
        {
            throw new IllegalStateException("The stack has already been registered");
        }

        manager.addPropertyListener(new PropertyListener<T>()
        {
            @Override
            public void changed(PropertyEvent<T> event)
            {
                push(event.getSource().getProperty(event.getProperty()));
            }

            @Override
            public void loaded(PropertyEvent<T> event)
            {
                push(event.getSource().getProperty(event.getProperty()));
            }

            @Override
            public void reset(PropertyEvent<T> event)
            {
                push(event.getSource().getProperty(event.getProperty()));
            }

            @Override
            public void saved(PropertyEvent<T> event)
            {
                synchronized (PropertyChangeStack.this)
                {
                    savedLoc = currentLoc;
                }
            }
        });

        stack.add(manager.getRawProperty());
        savedLoc = ++currentLoc;
    }

    /**
     * Push the given value onto the stack. This will invalidate all future
     * changes, which removes redo capability until an undo is called.<br>
     * <br>
     * Note that this method will do nothing if the new value is the same as the
     * current value.
     * 
     * @param value
     *            the new value to push
     * @throws NullPointerException
     *             if the given value is <code>null</code>
     */
    private synchronized void push(String value) throws NullPointerException
    {
        if (value.equals(getCurrentValue()))
        {
            return;
        }

        if (stack.size() > currentLoc + 1)
        {
            Iterator<String> iter = stack.listIterator(currentLoc + 1);
            while (iter.hasNext())
            {
                iter.next();
                iter.remove();
            }
        }

        stack.add(value);
        currentLoc++;
    }

    /**
     * Determine whether or not the current value is the same as the saved
     * value. This method will return <code>true</code> if the two values are
     * equal, even if modifications have been made between the last saved value
     * and the current value.
     * 
     * @return <code>true</code> if the {@link #getCurrentValue() current value}
     *         is equal to the {@link #getSavedValue() last saved value};
     *         <code>false</code> otherwise
     */
    public synchronized boolean isModified()
    {
        return !getCurrentValue().equals(getSavedValue());
    }

    /**
     * Retrieve the current value for the property to which this stack is
     * registered.
     * 
     * @return the current value
     */
    public synchronized String getCurrentValue()
    {
        return stack.get(currentLoc);
    }

    /**
     * Retrieve the last saved value for the property to which this stack is
     * registered.
     * 
     * @return the saved value
     */
    public synchronized String getSavedValue()
    {
        return stack.get(savedLoc);
    }

    /**
     * Determine whether or not {@link #undo()} can be called.
     * 
     * @return <code>true</code> if there is at least one past value on the
     *         stack; <code>false</code> otherwise
     */
    public synchronized boolean isUndoPossible()
    {
        return currentLoc > 0;
    }

    /**
     * Determine whether or not {@link #redo()} can be called.
     * 
     * @return <code>true</code> if there is at least one future value on the
     *         stack; <code>false</code> otherwise
     */
    public synchronized boolean isRedoPossible()
    {
        return currentLoc < stack.size() - 1;
    }

    /**
     * Undo the last modification. After this call completes, {@link #redo()}
     * may be used until a new modification is made. If undo is not possible,
     * the {@link #getCurrentValue() current value} will be returned with no
     * change.
     * 
     * @return the new {@link #getCurrentValue() current value}
     */
    public synchronized String undo()
    {
        if (!isUndoPossible())
        {
            return getCurrentValue();
        }

        return stack.get(--currentLoc);
    }

    /**
     * Redo the last undo. This method will only have an effect after at least
     * one successful call to {@link #undo()} and before any new modifications
     * are made. If redo is not possible, the {@link #getCurrentValue() current
     * value} will be returned with no change.
     * 
     * @return the new {@link #getCurrentValue() current value}
     */
    public synchronized String redo()
    {
        if (!isRedoPossible())
        {
            return getCurrentValue();
        }

        return stack.get(++currentLoc);
    }
}
