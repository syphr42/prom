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
 * This class provide a naive stack implementation with the ability to move back
 * and forth through the stack. It is naive in that it maintains references to
 * all of the values, not differences between them. However, it is suitable for
 * simple values, such as primitive wrappers or Strings.
 * 
 * @param <T>
 *            the type of element tracked by this stack
 * 
 * @author Gregory P. Moyer
 */
/* default */class ChangeStack<T>
{
    /**
     * The stack of changes being tracked.
     */
    private final List<T> stack;

    /**
     * The current location in the stack. This will change as operations are
     * performed (such as changing the value, undoing a change, or redoing a
     * change).
     */
    private int currentLoc;

    /**
     * The location of the last value that was saved. When a save event occurs,
     * this will be updated to the current location. It is assumed that this is
     * the current location at the time the stack is registered.
     */
    private int savedLoc;

    /**
     * Create a new stack with the given initial value.
     * 
     * @param value
     *            the initial value of this stack (it is assumed that this is
     *            also the last saved value)
     */
    public ChangeStack(T value)
    {
        stack = new ArrayList<T>();
        stack.add(value);
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
    public synchronized void push(T value) throws NullPointerException
    {
        if (value.equals(getCurrentValue()))
        {
            return;
        }

        if (stack.size() > currentLoc + 1)
        {
            Iterator<T> iter = stack.listIterator(currentLoc + 1);
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
     * Record that the current location has been saved. It is guaranteed that
     * after this method completes and before any other methods in this class
     * are called, {@link #isModified()} will return <code>false</code>.
     */
    public synchronized void saved()
    {
        savedLoc = currentLoc;
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
    public synchronized T getCurrentValue()
    {
        return stack.get(currentLoc);
    }

    /**
     * Retrieve the last saved value for the property to which this stack is
     * registered.
     * 
     * @return the saved value
     */
    public synchronized T getSavedValue()
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
    public synchronized T undo()
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
    public synchronized T redo()
    {
        if (!isRedoPossible())
        {
            return getCurrentValue();
        }

        return stack.get(++currentLoc);
    }
}
