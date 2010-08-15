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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class creates a management API for a {@link Properties} file whose keys are
 * described by an Enum.
 *
 * @param <T>
 *            the type of the Enum that represents the keys of the properties file
 *
 * @author Gregory P. Moyer
 */
public class PropertiesManager<T extends Enum<T>>
{
    /**
     * Listeners that are waiting for property events, such as loading or saving the file.
     */
    private final List<PropertyListener<T>> listeners = new CopyOnWriteArrayList<PropertyListener<T>>();

    /**
     * A cache of {@link ManagedProperty managed properties} that are created on demand.
     */
    private final Map<T, ManagedProperty<T>> managedPropertyCache = new HashMap<T, ManagedProperty<T>>();

    /**
     * An object used to retrieve the raw, internal value of a given property. This is
     * intended for use with the {@link #evaluator}.
     */
    private final Retriever retriever = createRetriever();

    /**
     * The evaluator used to convert property references into fully evaluated property
     * values.
     */
    private final Evaluator evaluator;

    /**
     * The logger instance used to record notable information regarding the properties
     * manager.
     */
    private final Logger logger;

    /**
     * An executor to handle operations that could take a long time, such as interacting
     * with a file system for loading or saving the properties file.
     */
    private final ExecutorService executor;

    /**
     * The properties file represented by this manager.
     */
    private final File file;

    /**
     * The enumeration of keys in this properties file.
     */
    // TODO might be able to ditch this - need to make sure inferred types will work correctly
    private final Class<T> descriptorType;

    /**
     * The object that determines how to translate between Enum names and property keys.
     */
    private final Translator<T> translator;

    /**
     * The properties instance with which this manager will interact.
     */
    private final ManagedProperties properties;

    /**
     * A flag to determine whether or not default values are stored to the file when
     * saved. The default value is <code>false</code>.
     *
     * @see #isSavingDefaults()
     * @see #setSavingDefaults(boolean)
     */
    private boolean savingDefaults;

    /**
     * A flag that determines whether or not property values are automatically trimmed as
     * they are read. The default is <code>true</code>.
     *
     * @see #isAutoTrim()
     * @see #setAutoTrim(boolean)
     */
    private boolean autoTrim = true;

    /**
     * The comment that will be written to the top of the properties file when and if it
     * is written. This can be <code>null</code>, in which case no extra comment is
     * written. The default value is <code>null</code>.
     *
     * @see #setComment(String)
     */
    private String comment;

    /**
     * Construct a new manager for the given properties file.
     * 
     * @param file
     *            the file system location of the properties represented here
     * @param defaults
     *            default values for the properties represented here
     * @param descriptorType
     *            the enumeration of keys in the properties file
     * @param translator
     *            the translator to convert between Enum names and property keys
     * @param evaluator
     *            the evaluator to convert nested property references into fully
     *            evaluated strings
     * @param executor
     *            a service to handle potentially long running tasks, such as
     *            interacting with the file system
     */
    public PropertiesManager(File file,
                             Properties defaults,
                             Class<T> descriptorType,
                             Translator<T> translator,
                             Evaluator evaluator,
                             ExecutorService executor)
    {
        logger = Logger.getLogger(PropertiesManager.class.getPackage().getName());

        this.file = file;
        this.descriptorType = descriptorType;
        this.translator = translator;
        this.evaluator = evaluator;
        this.executor = executor;

        properties = new ManagedProperties(copyProperties(defaults));
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
     * Set the flag that determines whether or not values will be automatically trimmed of
     * whitespace as they are read.
     *
     * @param autoTrim
     *            the flag to set
     */
    public void setAutoTrim(boolean autoTrim)
    {
        this.autoTrim = autoTrim;
    }

    /**
     * Determine whether or not values will be automatically trimmed of whitespace as they
     * are read.
     *
     * @return <code>true</code> if values will be trimmed; <code>false</code> otherwise
     */
    public boolean isAutoTrim()
    {
        return autoTrim;
    }

    /**
     * Set a comment that will be written to the file that stores the properties managed
     * by this instance.<br>
     * <br>
     * The default is no comment.
     *
     * @param comment
     *            the comment to set
     */
    public void setComment(String comment)
    {
        this.comment = comment;
    }

    /**
     * @return the file represented by this properties manager
     */
    public File getFile()
    {
        return file;
    }

    /**
     * Get an object that will encapsulate the functionality of this manager specific to a
     * single property. This makes it easier to watch, modify, and generally interact with
     * a single property.
     *
     * @param property
     *            the property to manage
     * @return the encapsulation of the functionality of this manager as it pertains to a
     *         single property
     */
    public ManagedProperty<T> getManagedProperty(T property)
    {
        synchronized (managedPropertyCache)
        {
            ManagedProperty<T> managedProperty = managedPropertyCache.get(property);

            if (managedProperty == null)
            {
                managedProperty = new ManagedProperty<T>(property, this);
                managedPropertyCache.put(property, managedProperty);
            }

            return managedProperty;
        }
    }

    /**
     * Add a listener for property events (such as change, save, load).
     *
     * @param listener
     *            the listener to add
     */
    public void addPropertyListener(PropertyListener<T> listener)
    {
        listeners.add(listener);
    }

    /**
     * Remove a property listener.
     *
     * @param listener
     *            the listener to remove
     */
    public void removePropertyListener(PropertyListener<T> listener)
    {
        listeners.remove(listener);
    }

    /**
     * Retrieve the object that translates between Enum instances and property names
     * (keys).
     *
     * @return the property name translator
     */
    public Translator<T> getTranslator()
    {
        return translator;
    }

    /**
     * Retrieve the object that converts nested property references into evaluated
     * strings.
     *
     * @return the nested property evaluator
     */
    protected Evaluator getEvaluator()
    {
        return evaluator;
    }

    /**
     * Retrieve a {@link Properties} instance that contains the properties managed by this
     * instance.<br>
     * <br>
     * Please note that the returned {@link Properties} instance is not connected in any
     * way to this manager and is only a snapshot of what the properties looked like at
     * the time the request was fulfilled.
     *
     * @return a {@link Properties} instance containing the properties managed by this
     *         instance
     */
    public Properties getProperties()
    {
        try
        {
            ensureLoaded();
        }
        catch (PropertyException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return new Properties();
        }

        return properties.getProperties();
    }

    /**
     * Retrieve the set of keys currently in use by this manager. This encompasses and key
     * which currently has a value or a default value associated with it. Normally, this
     * should have the same contents as {@link EnumSet#allOf(Class)}, but it is not
     * guaranteed.<br>
     * <br>
     * An example of where this set would not have the same contents as the set of Enums
     * would be if at least one property key has no value defined for it and no default
     * value associated with it. In that case, it would not be included in this set.
     *
     * @return the set of keys currently in use by this manager
     */
    public Set<T> keySet()
    {
        Set<T> keys = new TreeSet<T>();

        try
        {
            ensureLoaded();
        }
        catch (PropertyException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return keys;
        }

        synchronized (properties)
        {
            for (Object keyObj : properties.combinedKeySet())
            {
                try
                {
                    keys.add(getTranslator().getPropertyDescriptor(keyObj.toString()));
                }
                catch (IllegalArgumentException e)
                {
                    /*
                     * Skip unknown properties.
                     */
                    continue;
                }
            }
        }

        return keys;
    }

    /**
     * Get the current value of the given property.<br>
     * <br>
     * This method will block and wait for the properties to be loaded if they have not
     * been already. See {@link #reload()}.
     *
     * @param property
     *            the property to retrieve
     * @return the value of the given property (or <code>null</code> if an error occurred
     *         while attempting to read the properties)
     */
    public String getProperty(T property)
    {
        return getEvaluator().evaluate(getRawProperty(property), getRetriever());
    }

    /**
     * Retrieve the value of the given property as a boolean.
     *
     * @see #getProperty(Enum)
     *
     * @param property
     *            the property to retrieve
     * @return <code>true</code> if the value of the property is "true" (case
     *         insensitive); <code>false</code> otherwise
     */
    public boolean getBooleanProperty(T property)
    {
        return Boolean.parseBoolean(getProperty(property));
    }

    /**
     * Retrieve the value of the given property as an integer.
     *
     * @see #getProperty(Enum)
     *
     * @param property
     *            the property to retrieve
     * @return the integer value of the given property or the default value if the current
     *         value is not a valid integer
     * @throws NumberFormatException
     *             if both the current and default values are not integers
     */
    public int getIntegerProperty(T property)
    {
        try
        {
            return Integer.parseInt(getProperty(property));
        }
        catch (NumberFormatException e)
        {
            logger.log(Level.INFO, "Property " + property + ": " + e.getMessage(), e);
            return Integer.parseInt(getDefaultProperty(property));
        }
    }

    /**
     * Retrieve the value of the given property as a long.
     *
     * @see #getProperty(Enum)
     *
     * @param property
     *            the property to retrieve
     * @return the long value of the given property or the default value if the current
     *         value is not a valid long
     * @throws NumberFormatException
     *             if both the current and default values are not longs
     */
    public long getLongProperty(T property)
    {
        try
        {
            return Long.parseLong(getProperty(property));
        }
        catch (NumberFormatException e)
        {
            logger.log(Level.INFO, "Property " + property + ": " + e.getMessage(), e);
            return Long.parseLong(getDefaultProperty(property));
        }
    }

    /**
     * Retrieve the value of the given property as a float.
     *
     * @see #getProperty(Enum)
     *
     * @param property
     *            the property to retrieve
     * @return the float value of the given property or the default value if the current
     *         value is not a valid float
     * @throws NumberFormatException
     *             if both the current and default values are not floats
     */
    public float getFloatProperty(T property)
    {
        try
        {
            return Float.parseFloat(getProperty(property));
        }
        catch (NumberFormatException e)
        {
            logger.log(Level.INFO, "Property " + property + ": " + e.getMessage(), e);
            return Float.parseFloat(getDefaultProperty(property));
        }
    }

    /**
     * Retrieve the value of the given property as a double.
     *
     * @see #getProperty(Enum)
     *
     * @param property
     *            the property to retrieve
     * @return the double value of the given property or the default value if the current
     *         value is not a valid double
     * @throws NumberFormatException
     *             if both the current and default values are not doubles
     */
    public double getDoubleProperty(T property)
    {
        try
        {
            return Double.parseDouble(getProperty(property));
        }
        catch (NumberFormatException e)
        {
            logger.log(Level.INFO, "Property " + property + ": " + e.getMessage(), e);
            return Double.parseDouble(getDefaultProperty(property));
        }
    }

    /**
     * Retrieve the value of the given property as an enum constant of the given type.<br>
     * <br>
     * Note that this method requires the Enum constants to all have upper case names
     * (following Java naming conventions). This allows for a case insensitivity in the
     * properties file.
     *
     * @see #getProperty(Enum)
     *
     * @param <E>
     *            the type of enum that will be returned
     * @param property
     *            the property to retrieve
     * @param type
     *            the enum type to which the property will be converted
     * @return the enum constant corresponding to the value of the given property or the
     *         default value if the current value is not a valid instance of the given
     *         type
     * @throws IllegalArgumentException
     *             if both the current and default values are not valid constants of the
     *             given type
     */
    public <E extends Enum<E>> E getEnumProperty(T property, Class<E> type)
    {
        try
        {
            return Enum.valueOf(type, getProperty(property).toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            logger.log(Level.INFO, "Property " + property + ": " + e.getMessage(), e);
            return Enum.valueOf(type, getDefaultProperty(property).toUpperCase());
        }
    }

    /**
     * Get the current value of the given property, but without translating references. If
     * {@link #isAutoTrim() auto trim} is enabled, the value will also be trimmed of
     * whitespace.<br>
     * <br>
     * This method will block and wait for the properties to be loaded if they have not
     * been already. See {@link #reload()}.
     *
     * @param property
     *            the property to retrieve
     * @return the value of the given property (or <code>null</code> if an error occurred
     *         while attempting to read the properties)
     */
    public String getRawProperty(T property)
    {
        try
        {
            ensureLoaded();
        }
        catch (PropertyException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return null;
        }

        String propertyName = getTranslator().getPropertyName(property);
        String value;
        synchronized (properties)
        {
            value = properties.getProperty(propertyName);
        }

        if (value != null && isAutoTrim())
        {
            value = value.trim();
        }

        return value;
    }

    /**
     * Determine whether or not the given property is set to its default value.
     *
     * @param property
     *            the property to check
     * @return <code>true</code> if the given property has its default value;
     *         <code>false</code> otherwise
     */
    public boolean isDefault(T property)
    {
        return getRawProperty(property).equals(getDefaultPropertyRaw(property));
    }

    /**
     * Retrieve the default raw value of the given property. This functionality is local
     * by design - nothing outside of the properties manager should be directly requesting
     * the default value (mostly because it can come from different sources, such as a
     * default file or the enum constant itself). Also, client code should not be
     * concerned with what the default value is specifically, just what the value is and
     * whether or not it is default (see {@link #isDefault(Enum)}).<br>
     * <br>
     * Note that if {@link #isAutoTrim() auto trim} is enabled, this value will be trimmed
     * of whitespace.
     *
     * @param property
     *            the property whose default value is requested
     * @return the default raw value of the given property
     */
    private String getDefaultPropertyRaw(T property)
    {
        String value = properties.getDefaultValue(getTranslator().getPropertyName(property));

        if (isAutoTrim())
        {
            value = value.trim();
        }

        return value;
    }

    /**
     * Retrieve the evaluated default value of the given property.<br>
     * <br>
     * For an explanation of why this functionality is hidden, see
     * {@link #getDefaultPropertyRaw(Enum)}.
     *
     * @param property
     *            the property whose default value is requested
     * @return the default raw value of the given property
     */
    private String getDefaultProperty(T property)
    {
        return getEvaluator().evaluate(getDefaultPropertyRaw(property), getRetriever());
    }

    /**
     * Determine whether or not one property holds references to another property.
     *
     * @param property1
     *            the property to check for references
     * @param property2
     *            the target referenced property
     * @return <code>true</code> if the first property references the second;
     *         <code>false</code> otherwise
     */
    public boolean isReferencing(T property1, T property2)
    {
        return getEvaluator().isReferencing(getRawProperty(property1),
                                            getTranslator().getPropertyName(property2),
                                            getRetriever());
    }

    /**
     * Determine whether or not a reference to another property exists in the value of the
     * given property at the given position. If such a reference does exist, return it.
     *
     * @param property
     *            the property to search for the requested reference
     * @param position
     *            the position to check for a reference
     * @return the appropriate reference if one exists; <code>null</code> otherwise
     */
    public Reference referenceAt(T property, int position)
    {
        return getEvaluator().referenceAt(getRawProperty(property), position, getRetriever());
    }

    /**
     * Reload the current values of all properties.<br>
     * <br>
     * This method will block and wait for the properties to be loaded. See
     * {@link #reloadNB()} for a non-blocking version.
     *
     * @throws PropertyException
     *             if there is an error while attempting to load the properties
     */
    public void reload() throws PropertyException
    {
        try
        {
            Future<Void> task = reloadNB();
            task.get();
        }
        catch (ExecutionException e)
        {
            throw new PropertyException(e.getCause());
        }
        catch (InterruptedException e)
        {
            throw new PropertyException("Reloading of the properties file \""
                                        + getFile().getAbsolutePath() + "\" was interrupted.");
        }
    }

    /**
     * Reload the current values of all properties.<br>
     * <br>
     * This method will not block to wait for the properties to be loaded. See
     * {@link #reload()} for a blocking version.
     *
     * @return a task representing this load request
     */
    public Future<Void> reloadNB()
    {
        return load(true, true);
    }

    /**
     * Load the properties file with the option of whether or not to notify listeners.<br>
     * <br>
     * This method will not block to wait for the load to complete.
     *
     * @param notifyListeners
     *            if <code>true</code>, listeners will be notified; otherwise there will
     *            be no notifications
     * @param reload
     *            if <code>true</code>, properties will be always be loaded; otherwise,
     *            properties will only be loaded if they have not already been loaded
     * @return a task representing this load request
     */
    private Future<Void> load(final boolean notifyListeners, final boolean reload)
    {
        Callable<Void> task = new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                synchronized (properties)
                {
                    if (!reload && properties.isInitiated())
                    {
                        return null;
                    }

                    properties.clear();
                    properties.load(getFile());
                }

                if (notifyListeners)
                {
                    firePropertiesLoaded();
                }

                return null;
            }
        };

        return executor.submit(task);
    }

    /**
     * Set the given property using an Enum constant. This will not write the new value to
     * the file system.<br>
     * <br>
     * Please note that the Enum value set here is case insensitive. See
     * {@link #getEnumProperty(Enum, Class)} for additional details.
     *
     * @see #saveProperty(Enum, Enum)
     *
     * @param <E>
     *            the type of Enum value to set
     * @param property
     *            the property whose value is being set
     * @param value
     *            the value to set
     * @throws PropertyException
     *             if the properties file has not yet been loaded and an error occurs
     *             while trying to do so
     */
    public <E extends Enum<E>> void setProperty(T property, E value) throws PropertyException
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Cannot set a null value, use reset instead");
        }

        setProperty(property, value.name().toLowerCase());
    }

    /**
     * Set the given property using an object's string representation. This will not write
     * the new value to the file system.
     *
     * @see #saveProperty(Enum, Object)
     *
     * @param property
     *            the property whose value is being set
     * @param value
     *            the value to set
     * @throws PropertyException
     *             if the properties file has not yet been loaded and an error occurs
     *             while trying to do so
     */
    public void setProperty(T property, Object value) throws PropertyException
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Cannot set a null value, use reset instead");
        }

        setProperty(property, value.toString());
    }

    /**
     * Set the given property using a string. This will not write the new value to the
     * file system.
     *
     * @see #saveProperty(Enum, String)
     *
     * @param property
     *            the property whose value is being set
     * @param value
     *            the value to set
     * @throws PropertyException
     *             if the properties file has not yet been loaded and an error occurs
     *             while trying to do so
     */
    public void setProperty(T property, String value) throws PropertyException
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Cannot set a null value, use reset instead");
        }

        ensureLoaded();

        final String propertyName = getTranslator().getPropertyName(property);
        synchronized (properties)
        {
            /*
             * If the new value is the same as the old, then there is nothing to do.
             */
            Object previousValue = properties.getProperty(propertyName);
            if (value.equals(previousValue))
            {
                return;
            }

            /*
             * If the new value is the default and we aren't saving defaults, remove it.
             */
            if (!isSavingDefaults() && value.equals(properties.getDefaultValue(propertyName)))
            {
                properties.remove(propertyName);
            }
            else
            {
                properties.setProperty(propertyName, value);
            }
        }

        firePropertyChanged(property);
    }

    /**
     * Save the given property using an Enum constant. See
     * {@link #saveProperty(Enum, String)} for additional details.<br>
     * <br>
     * Please note that the Enum value saved here is case insensitive. See
     * {@link #getEnumProperty(Enum, Class)} for additional details.
     *
     * @param <E>
     *            the type of Enum value to save
     * @param property
     *            the property whose value is being saved
     * @param value
     *            the value to save
     * @throws PropertyException
     *             if there is an error while attempting to load and/or save the
     *             properties
     */
    public <E extends Enum<E>> void saveProperty(T property, E value) throws PropertyException
    {
        saveProperty(property, value.name());
    }

    /**
     * Save the given property using an object's string representation. See
     * {@link #saveProperty(Enum, String)} for additional details.
     *
     * @param property
     *            the property whose value is being saved
     * @param value
     *            the value to save
     * @throws PropertyException
     *             if there is an error while attempting to load and/or save the
     *             properties
     */
    public void saveProperty(T property, Object value) throws PropertyException
    {
        saveProperty(property, value.toString());
    }

    /**
     * Modify the value of the given property and save all properties to permanent
     * storage.<br>
     * <br>
     * This method will block and wait for the properties to be loaded if they have not
     * been already. See {@link #reload()}.
     *
     * @param property
     *            the property whose value is being saved
     * @param value
     *            the value to save
     * @throws PropertyException
     *             if there is an error while attempting to load and/or save the
     *             properties
     */
    public void saveProperty(T property, String value) throws PropertyException
    {
        // TODO potential sync issue here - can't lock on properties because the
        // worker thread will deadlock
        setProperty(property, value);
        save();
    }

    /**
     * Save the current values of all properties.<br>
     * <br>
     * This method will block and wait for the properties to be saved. See
     * {@link #saveNB()} for a non-blocking version.
     *
     * @throws PropertyException
     *             if there is an error while attempting to save the properties
     */
    public void save() throws PropertyException
    {
        try
        {
            Future<Void> task = saveNB();
            task.get();
        }
        catch (ExecutionException e)
        {
            throw new PropertyException(e.getCause());
        }
        catch (InterruptedException e)
        {
            throw new PropertyException("Saving of the properties file \""
                                        + getFile().getAbsolutePath() + "\" was interrupted.");
        }
    }

    /**
     * Save the current values of all properties.<br>
     * <br>
     * This method will not block to wait for the properties to be saved. See
     * {@link #save()} for a blocking version.
     *
     * @return a task representing this save request
     */
    public Future<Void> saveNB()
    {
        return save(true);
    }

    /**
     * Save the properties file with the option of whether or not to notify listeners.<br>
     * <br>
     * This method will not block to wait for the save to complete.
     *
     * @param notifyListeners
     *            if <code>true</code>, listeners will be notified; otherwise there will
     *            be no notifications
     * @return a task representing this save request
     */
    private Future<Void> save(final boolean notifyListeners)
    {
        Callable<Void> task = new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                synchronized (properties)
                {
                    FileOutputStream outputStream = new FileOutputStream(getFile());
                    try
                    {
                        properties.store(outputStream, comment);
                    }
                    finally
                    {
                        outputStream.close();
                    }
                }

                if (notifyListeners)
                {
                    firePropertiesSaved();
                }

                return null;
            }
        };

        return executor.submit(task);
    }

    /**
     * Reset the given property to its default value. This will not write the new value to
     * the file system.
     *
     * @param property
     *            the property whose value is being reset
     * @throws PropertyException
     *             if the properties file has not yet been loaded and an error occurs
     *             while trying to do so
     */
    public void resetProperty(T property) throws PropertyException
    {
        ensureLoaded();

        String propertyName = getTranslator().getPropertyName(property);
        synchronized (properties)
        {
            Object previousValue = properties.get(propertyName);
            if (previousValue == null
                || previousValue.equals(properties.getDefaultValue(propertyName)))
            {
                return;
            }

            properties.resetToDefault(propertyName, isSavingDefaults());
        }

        firePropertyReset(property);
    }

    /**
     * Reset the properties to the original defaults.
     */
    public void reset()
    {
        properties.resetToDefaults(isSavingDefaults());
        firePropertiesReset();
    }

    /**
     * Build a new {@link Retriever} instance that will be used by the
     * {@link #getEvaluator() evaluator} to request the values of nested property
     * references.
     *
     * @return a new {@link Retriever}
     */
    protected Retriever createRetriever()
    {
        return new Retriever()
        {
            @Override
            public String retrieve(String name)
            {
                return properties.getProperty(name);
            }
        };
    }

    /**
     * Get the {@link Retriever} instance used internally to fetch values using an
     * {@link Evaluator}.
     *
     * @see #createRetriever()
     * @see Evaluator
     *
     * @return the retriever instance
     */
    protected Retriever getRetriever()
    {
        return retriever;
    }

    /**
     * Load the properties file if it is not currently loaded. If the file has already
     * been loaded, this will be a no-op.
     *
     * @throws PropertyException
     *             if an error occurs while attempting to read the properties file
     */
    private void ensureLoaded() throws PropertyException
    {
        if (!properties.isInitiated())
        {
            try
            {
                Future<Void> task = load(true, false);
                task.get();
            }
            catch (ExecutionException e)
            {
                throw new PropertyException(e.getCause());
            }
            catch (InterruptedException e)
            {
                throw new PropertyException("Loading of the properties file \""
                                            + getFile().getAbsolutePath() + "\" was interrupted.");
            }
        }
    }

    /**
     * Notify all listeners that the properties have been loaded.
     */
    private void firePropertiesLoaded()
    {
        PropertyEvent<T> event = null;

        for (PropertyListener<T> l : listeners)
        {
            if (event == null)
            {
                event = new PropertyEvent<T>(this, null);
            }

            l.loaded(event);
        }
    }

    /**
     * Notify all listeners that the properties have been saved.
     */
    private void firePropertiesSaved()
    {
        PropertyEvent<T> event = null;

        for (PropertyListener<T> l : listeners)
        {
            if (event == null)
            {
                event = new PropertyEvent<T>(this, null);
            }

            l.saved(event);
        }
    }

    /**
     * Notify all listeners that a property has changed.
     *
     * @param property
     *            the property whose value has changed
     */
    private void firePropertyChanged(T property)
    {
        PropertyEvent<T> event = null;

        for (PropertyListener<T> l : listeners)
        {
            if (event == null)
            {
                event = new PropertyEvent<T>(this, property);
            }

            l.changed(event);
        }
    }

    /**
     * Notify all listeners that a property has been reset.
     *
     * @param property
     *            the property whose value has changed
     */
    private void firePropertyReset(T property)
    {
        PropertyEvent<T> event = null;

        for (PropertyListener<T> l : listeners)
        {
            if (event == null)
            {
                event = new PropertyEvent<T>(this, property);
            }

            l.reset(event);
        }
    }

    /**
     * Notify all listeners that all of the properties have been reset.
     */
    private void firePropertiesReset()
    {
        firePropertyReset(null);
    }

    /**
     * This class provides API in addition to the standard {@link Properties} API to allow
     * easier management of the properties within as they pertain to an actual file on the
     * file system and the lifecycle of load/modify/save.
     *
     * @author Gregory P. Moyer
     */
    private static class ManagedProperties extends Properties
    {
        /**
         * Serialization ID
         */
        private static final long serialVersionUID = 1L;

        /**
         * A flag that determines whether or not this instance has been initiated with
         * properties read from a resource (i.e. file or stream).
         */
        private volatile boolean initiated;

        /**
         * Construct a new managed instance.
         * 
         * @param defaults
         *            the default values
         */
        public ManagedProperties(Properties defaults)
        {
            super(defaults);
        }

        /**
         * Retrieve a set of all of the keys with values that make up this properties
         * instance (both keys with direct values and those with default values).
         *
         * @return the combined set of keys representing properties with direct values or
         *         default values
         */
        public synchronized Set<Object> combinedKeySet()
        {
            Set<Object> keys = new HashSet<Object>(keySet());
            keys.addAll(defaults.keySet());

            return keys;
        }

        /**
         * Determine whether or not this instance has been loaded with properties from a
         * resource (i.e. file or stream).
         *
         * @return <code>true</code> if this instance has been loaded; <code>false</code>
         *         otherwise
         */
        public boolean isInitiated()
        {
            return initiated;
        }

        /**
         * Load the given file.
         * 
         * @param file
         *            the file containing the current property values
         * @throws IOException
         *             if there is a file system error while attempting to read
         *             the file
         */
        public synchronized void load(File file) throws IOException
        {
            if (file.isFile())
            {
                InputStream inputStream = new FileInputStream(file);
                try
                {
                    load(inputStream);
                }
                finally
                {
                    inputStream.close();
                }
            }

            initiated = true;
        }

        /**
         * Get the default value for the given key.
         *
         * @param key
         *            the key whose associated default value is requested
         * @return the default value for the given key or <code>null</code> if there is no
         *         default value
         */
        public synchronized String getDefaultValue(String key)
        {
            return defaults.getProperty(key);
        }

        /**
         * Reset the value associated with specified key to its default value.
         *
         * @param key
         *            the key whose associated value should be reset
         * @param savingDefaults
         *            a flag to determine whether or not default values are being saved to
         *            the file system
         */
        public synchronized void resetToDefault(String key, boolean savingDefaults)
        {
            if (savingDefaults)
            {
                setProperty(key, defaults.getProperty(key));
            }
            else
            {
                remove(key);
            }
        }

        /**
         * Reset all values to the default values.
         *
         * @param savingDefaults
         *            a flag that determines whether or not default values should be part
         *            of the main properties or left as a separate set (as a separate set,
         *            they will not be written to the file system when the properties are
         *            written)
         */
        public synchronized void resetToDefaults(boolean savingDefaults)
        {
            super.clear();

            if (savingDefaults)
            {
                for (Entry<?, ?> entry : defaults.entrySet())
                {
                    put(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public synchronized void clear()
        {
            super.clear();
            initiated = false;
        }

        /**
         * Retrieve a {@link Properties} instance that contains the properties managed by
         * this instance.<br>
         * <br>
         * Please note that the returned {@link Properties} instance is not connected in
         * any way to this instance and is only a snapshot of what the properties looked
         * like at the time the request was fulfilled.
         *
         * @return a {@link Properties} instance containing the properties managed by this
         *         instance
         */
        public synchronized Properties getProperties()
        {
            Properties propertiesDefaults = new Properties();
            propertiesDefaults.putAll(defaults);

            Properties properties = new Properties(propertiesDefaults);
            properties.putAll(this);

            return properties;
        }

        /*
         * Overridden for documentation purposes. No additional fields necessary from the
         * super.
         */
        @Override
        public synchronized int hashCode()
        {
            return super.hashCode();
        }

        /*
         * Overridden for documentation purposes. No additional fields necessary from the
         * super.
         */
        @Override
        public synchronized boolean equals(Object o)
        {
            return super.equals(o);
        }
    }
}
