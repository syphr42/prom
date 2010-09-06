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
import java.util.Properties;
import java.util.Set;

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
     * The main properties object that backs this instance. All core properties
     * work will be delegated to this object.
     */
    private final Properties properties;
    
    /**
     * A copy of the default property values supplied by the client.
     */
    private final Properties defaults;

    /**
     * A marker that indicates whether or not the properties have been
     * initialized by attempting to read them from a file and either succeeding
     * or determining that the file does not exist (in which case default values
     * are used).
     */
    private volatile Status status = Status.UNINITIALIZED;
    
    /**
     * A flag to determine whether or not default values are stored to the file when
     * saved. The default value is <code>false</code>.
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
        this.properties = new Properties(this.defaults);
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
     * Set the flag that determines whether or not default values are saved to the
     * properties file when and if it is written.
     *
     * @param savingDefaults
     *            the flag to set
     */
    public void setSavingDefaults(boolean savingDefaults)
    {
        this.savingDefaults = savingDefaults;
    }

    /**
     * Determine whether or not default values will be written to the properties file when
     * and if it is saved.
     *
     * @return <code>true</code> if default values will be written out; <code>false</code>
     *         otherwise
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
     *             if there is a file system error while attempting to read
     *             the file
     */
    public void load(File file) throws IOException
    {
        synchronized (properties)
        {
            clear();

            /*
             * We do not want to throw a FileNotFoundException here because it
             * is OK if the file does not exist. In this case, default values
             * will be used.
             */
            if (file.isFile())
            {
                InputStream inputStream = new FileInputStream(file);
                try
                {
                    properties.load(inputStream);
                }
                finally
                {
                    inputStream.close();
                }
            }

            status = Status.INITIALIZED;
        }
    }

    /**
     * Ensure that this instance is in a state such that it has valid property
     * values. In other words, if {@link #isLoaded()} returns <code>true</code>,
     * this method will do nothing. If {@link #isLoaded()} returns
     * <code>false</code>, this method will throw an exception.
     * 
     * @see #isLoaded()
     * 
     * @throws IllegalStateException
     *             if the properties have not yet been loaded
     */
    private void ensureLoaded() throws IllegalStateException
    {
        if (!isLoaded())
        {
            throw new IllegalStateException("Illegal access: properties have not yet been loaded");
        }
    }

    /**
     * Determine whether or not this instance has initialized its properties. If
     * this method does not return <code>true</code>, the properties within must
     * not be accessed until {@link #load(File)} is called successfully.<br>
     * <br>
     * There is no way to unload properties. Therefore, once an instance has
     * been loaded, the only way this method could return <code>false</code> is
     * if a subsequent call to {@link #load(File)} fails.
     * 
     * @return <code>true</code> if this instance has initialized its
     *         properties; <code>false</code> otherwise
     */
    public boolean isLoaded()
    {
        return Status.INITIALIZED.equals(status);
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
        synchronized (properties)
        {
            ensureLoaded();

            FileOutputStream outputStream = new FileOutputStream(file);
            try
            {
                properties.store(outputStream, comment);
            }
            finally
            {
                outputStream.close();
            }
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
        synchronized (properties)
        {
            ensureLoaded();
            return properties.getProperty(propertyName);
        }
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
        synchronized (properties)
        {
            ensureLoaded();
            
            /*
             * If the new value is the same as the old, then there is nothing to
             * do.
             */
            if (value.equals(properties.getProperty(propertyName)))
            {
                return false;
            }

            /*
             * If the new value is the default and we aren't saving defaults,
             * remove it.
             */
            if (!isSavingDefaults()
                && value.equals(getDefaultValue(propertyName)))
            {
                properties.remove(propertyName);
            }
            else
            {
                properties.setProperty(propertyName, value);
            }

            return true;
        }
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
        synchronized (properties)
        {
            ensureLoaded();

            String previousValue = properties.getProperty(propertyName);
            if (previousValue == null
                || previousValue.equals(getDefaultValue(propertyName)))
            {
                return false;
            }

            if (isSavingDefaults())
            {
                properties.setProperty(propertyName,
                                       defaults.getProperty(propertyName));
            }
            else
            {
                properties.remove(propertyName);
            }

            return true;
        }
    }

    /**
     * Reset all values to the default values.
     */
    public void resetToDefaults()
    {
        synchronized (properties)
        {
            ensureLoaded();

            properties.clear();

            if (isSavingDefaults())
            {
                properties.putAll(defaults);
            }
        }
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
        synchronized (properties)
        {
            ensureLoaded();
            return properties.stringPropertyNames();
        }
    }

    /**
     * Revert this instance to an uninitialized state where it contains no
     * property values. This method does not affect default values.
     */
    public void clear()
    {
        synchronized (properties)
        {
            status = Status.UNINITIALIZED;
            properties.clear();
        }
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
        synchronized (properties)
        {
            ensureLoaded();

            Properties defaultsCopy = new Properties();
            defaultsCopy.putAll(defaults);

            Properties propertiesCopy = new Properties(defaultsCopy);
            propertiesCopy.putAll(properties);

            return propertiesCopy;
        }
    }
    
    /**
     * This Enum provides the possible states that a {@link ManagedProperties}
     * instance can occupy.
     *
     * @author Gregory P. Moyer
     */
    private static enum Status
    {
        /**
         * The properties file has not yet been read and so no values should be
         * used in the properties instance until
         * {@link ManagedProperties#load(File)} has been called.
         */
        UNINITIALIZED,

        /**
         * The properties object has been initialized. This does not mean that
         * it read any properties from the file (because it is OK if the
         * properties file does not exist). Instead, this state indicates that
         * the {@link ManagedProperties#load(File)} was called and completed
         * successfully. This means that values can be retrieved and set on this
         * instance.
         */
        INITIALIZED,
    }
}