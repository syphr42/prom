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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class provides supporting API around {@link Properties} to allow easier
 * management of the properties within as they pertain to an actual file on the
 * file system and the lifecycle of load/modify/save.
 * 
 * @author Gregory P. Moyer
 */
/* default */class ManagedProperties
{
    /**
     * A collection of properties and their associated stacks. This map will
     * have, at a minimum, every property for which a default value was
     * specified.
     */
    private final ConcurrentMap<String, ChangeStack<String>> properties;

    /**
     * A copy of the default property values supplied by the client.
     */
    private final Properties defaults;

    /**
     * A flag to determine whether or not default values are stored to the file
     * when saved. The default value is <code>false</code>.
     * 
     * @see #isSavingDefaults()
     * @see #setSavingDefaults(boolean)
     */
    private volatile boolean savingDefaults;

    /**
     * Construct a new managed instance.
     * 
     * @param defaults
     *            the default values (note that these will be copied to a new
     *            object so that the caller cannot change the underlying
     *            defaults used in this instance)
     */
    public ManagedProperties(Properties defaults)
    {
        this.defaults = copyProperties(defaults);

        this.properties = new ConcurrentHashMap<String, ChangeStack<String>>(this.defaults.size());
        for (Entry<Object, Object> entry : defaults.entrySet())
        {
            setValue(entry.getKey().toString(),
                     entry.getValue().toString(),
                     false);
        }
    }

    /**
     * Copy the given properties to a new object.
     * 
     * @param source
     *            the source from which to copy
     * @return a copy of the source or <code>null</code> if the source is
     *         <code>null</code>
     */
    private Properties copyProperties(Properties source)
    {
        Properties copy = new Properties();

        if (source != null)
        {
            copy.putAll(source);
        }

        return copy;
    }

    /**
     * Set the flag that determines whether or not default values are saved to
     * the properties file when and if it is written.
     * 
     * @param savingDefaults
     *            the flag to set
     */
    public void setSavingDefaults(boolean savingDefaults)
    {
        this.savingDefaults = savingDefaults;
    }

    /**
     * Determine whether or not default values will be written to the properties
     * file when and if it is saved.
     * 
     * @return <code>true</code> if default values will be written out;
     *         <code>false</code> otherwise
     */
    public boolean isSavingDefaults()
    {
        return savingDefaults;
    }

    /**
     * Load the given file.
     * 
     * @param file
     *            the file containing the current property values (this file
     *            does not have to exist)
     * @throws IOException
     *             if there is a file system error while attempting to read the
     *             file
     */
    public void load(File file) throws IOException
    {
        /*
         * We do not want to throw a FileNotFoundException here because it is OK
         * if the file does not exist. In this case, default values will be
         * used.
         */
        if (!file.isFile())
        {
            return;
        }

        Properties tmpProperties = new Properties();

        InputStream inputStream = new FileInputStream(file);
        try
        {
            tmpProperties.load(inputStream);
        }
        finally
        {
            inputStream.close();
        }

        for (Entry<Object, Object> entry : tmpProperties.entrySet())
        {
            setValue(entry.getKey().toString(),
                     entry.getValue().toString(),
                     true);
        }
    }

    /**
     * Save the current state of the properties within this instance to the
     * given file.
     * 
     * @param file
     *            the file to which the current properties and their values will
     *            be written
     * @param comment
     *            an optional comment to put at the top of the file (
     *            <code>null</code> means no comment)
     * @throws IOException
     *             if there is an error writing the given file
     */
    public void save(File file, String comment) throws IOException
    {
        // FIXME there is a synchronization issue here - the value of a property
        // could change between save and sync; this could be fixed by an atomic
        // getAndSync() method on ChangeStack, but then if the save operation
        // fails, the stacks would be incorrectly marked as synced
        
        Properties tmpProperties = new Properties();

        for (Entry<String, ChangeStack<String>> entry : properties.entrySet())
        {
            String propertyName = entry.getKey();
            String value = entry.getValue().getCurrentValue();

            if (!isSavingDefaults()
                && value.equals(getDefaultValue(propertyName)))
            {
                continue;
            }

            tmpProperties.setProperty(propertyName, value);
        }

        FileOutputStream outputStream = new FileOutputStream(file);
        try
        {
            tmpProperties.store(outputStream, comment);

            for (ChangeStack<String> stack : properties.values())
            {
                stack.synced();
            }
        }
        finally
        {
            outputStream.close();
        }
    }

    /**
     * Retrieve the value associated with the given property.
     * 
     * @param propertyName
     *            the property whose value is requested
     * @return the value associated with the given property or <code>null</code>
     *         if no such property exists
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    public String getProperty(String propertyName) throws IllegalStateException
    {
        ChangeStack<String> stack = properties.get(propertyName);
        if (stack == null)
        {
            return null;
        }

        return stack.getCurrentValue();
    }

    /**
     * Set a new value for the given property.
     * 
     * @param propertyName
     *            the property whose value will be set
     * @param value
     *            the new value to set
     * @return <code>true</code> if the value of the given property changed as a
     *         result of this call; <code>false</code> otherwise
     * @throws NullPointerException
     *             if the give value is <code>null</code>
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    public boolean setProperty(String propertyName, String value) throws NullPointerException,
                                                                 IllegalStateException
    {
        return setValue(propertyName, value, false);
    }

    /**
     * Push or sync the given value to the appropriate stack. This method will
     * create a new stack if this property has never had a value before.
     * 
     * @param propertyName
     *            the property whose value will be set
     * @param value
     *            the value to set
     * @param sync
     *            a flag to determine whether the value is
     *            {@link ChangeStack#sync(Object) synced} or simply
     *            {@link ChangeStack#push(Object) pushed}
     * @return <code>true</code> if the value of the given property changed as a
     *         result of this call; <code>false</code> otherwise
     * @throws NullPointerException
     *             if the given value is <code>null</code>
     */
    private boolean setValue(String propertyName, String value, boolean sync) throws NullPointerException
    {
        ChangeStack<String> stack = properties.get(propertyName);
        if (stack == null)
        {
            // FIXME when a new stack is created this means this is a new
            // property with no default - this should be considered "modified"
            // but it will not be since a new stack is in a "not modified" state

            ChangeStack<String> newStack = new ChangeStack<String>(value);
            stack = properties.putIfAbsent(propertyName, newStack);
            if (stack == null)
            {
                return true;
            }
        }

        return sync ? stack.sync(value) : stack.push(value);
    }

    /**
     * Get the default value for the given property.
     * 
     * @param propertyName
     *            the property whose associated default value is requested
     * @return the default value for the given property or <code>null</code> if
     *         there is no default value
     */
    public String getDefaultValue(String propertyName)
    {
        return defaults.getProperty(propertyName);
    }

    /**
     * Reset the value associated with specified property to its default value.
     * 
     * @param propertyName
     *            the property whose associated value should be reset
     * @return <code>true</code> if the value of the given property changed as a
     *         result of this call; <code>false</code> otherwise
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    public boolean resetToDefault(String propertyName) throws IllegalStateException
    {
        /*
         * If this property was added with no default value, all we can do is
         * remove the property entirely.
         */
        String defaultValue = getDefaultValue(propertyName);
        if (defaultValue == null)
        {
            return properties.remove(propertyName) != null;
        }

        /*
         * Every property with a default value is guaranteed to have a stack.
         * Since we just confirmed the existence of a default value, we know the
         * stack is available.
         */
        return properties.get(propertyName).push(defaultValue);
    }

    /**
     * Reset all values to the default values.
     */
    public void resetToDefaults()
    {
        for (Iterator<Entry<String, ChangeStack<String>>> iter = properties.entrySet()
                                                                           .iterator(); iter.hasNext();)
        {
            Entry<String, ChangeStack<String>> entry = iter.next();

            String defaultValue = getDefaultValue(entry.getKey());
            if (defaultValue == null)
            {
                iter.remove();
                continue;
            }

            entry.getValue().push(defaultValue);
        }
    }

    /**
     * Determine whether or not the given property has been modified since it
     * was last load or saved.
     * 
     * @param propertyName
     *            the property to check
     * @return <code>true</code> if this property has been modified since the
     *         last time it was loaded or saved; <code>false</code> otherwise
     */
    public boolean isModified(String propertyName)
    {
        ChangeStack<String> stack = properties.get(propertyName);
        if (stack == null)
        {
            return false;
        }

        return stack.isModified();
    }

    /**
     * Determine whether or not any property has been modified since the last
     * load or save.
     * 
     * @return <code>true</code> if any property known to this instance has been
     *         modified since the last load or save; <code>false</code>
     *         otherwise
     */
    public boolean isModified()
    {
        for (ChangeStack<String> stack : properties.values())
        {
            if (stack.isModified())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieve a set of property names currently in use by this instance. This
     * includes default and non-default properties.
     * 
     * @return the set of property names currently in use
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    public Set<String> getPropertyNames() throws IllegalStateException
    {
        return Collections.unmodifiableSet(properties.keySet());
    }

    /**
     * Retrieve a {@link Properties} instance that contains the properties
     * within this instance.<br>
     * <br>
     * Please note that the returned {@link Properties} instance is not
     * connected in any way to this instance and is only a snapshot of what the
     * properties looked like at the time the request was fulfilled.
     * 
     * @return a {@link Properties} instance containing the properties within
     *         this instance
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    public Properties getProperties() throws IllegalStateException
    {
        Properties exportProperties = new Properties();
        for (Entry<String, ChangeStack<String>> entry : properties.entrySet())
        {
            exportProperties.setProperty(entry.getKey(),
                                         entry.getValue().getCurrentValue());
        }

        return exportProperties;
    }
}