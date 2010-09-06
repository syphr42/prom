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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PropertiesManagerTest
{
    private static final double FLOATING_POINT_DELTA = 0.00001;

    private static File TEST_DATA_DIR = new File("target/test-data");

    private static File TEST_PROPS_1 = new File(TEST_DATA_DIR, "test1.properties");
    private static File TEST_PROPS_2 = new File(TEST_DATA_DIR, "test2.properties");
    private static File TEST_PROPS_2_DEFAULT = new File(TEST_DATA_DIR, "default.test2.properties");

    private static String BASE_PROPS_1_RESOURCE_PATH = "/test.base.1.properties";
    private static String BASE_PROPS_2_RESOURCE_PATH = "/test.base.2.properties";
    private static String BASE_PROPS_2_DEFAULT_RESOURCE_PATH = "/default.test.base.2.properties";

    private static Translator<Key1> TRANSLATOR1;
    private static ExecutorService EXECUTOR;

    private static Properties test2DefaultProperties;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Assert.assertTrue("Unable to create \"" + TEST_DATA_DIR.getAbsolutePath() + "\"",
                          TEST_DATA_DIR.isDirectory() || TEST_DATA_DIR.mkdirs());

        InputStream baseIn1 = PropertiesManagerTest.class.getResourceAsStream(BASE_PROPS_1_RESOURCE_PATH);
        Assert.assertNotNull("Base properties 1 is missing", baseIn1);

        OutputStream baseOut1 = new FileOutputStream(TEST_PROPS_1);
        IOUtils.copy(baseIn1, baseOut1);

        baseIn1.close();
        baseOut1.close();

        InputStream baseIn2 = PropertiesManagerTest.class.getResourceAsStream(BASE_PROPS_2_RESOURCE_PATH);
        Assert.assertNotNull("Base properties 2 is missing", baseIn2);

        OutputStream baseOut2 = new FileOutputStream(TEST_PROPS_2);
        IOUtils.copy(baseIn2, baseOut2);

        baseIn2.close();
        baseOut2.close();

        InputStream baseIn2Default = PropertiesManagerTest.class.getResourceAsStream(BASE_PROPS_2_DEFAULT_RESOURCE_PATH);
        Assert.assertNotNull("Base properties 2 default is missing", baseIn2Default);

        baseIn2Default.mark(Integer.MAX_VALUE);

        OutputStream baseOut2Default = new FileOutputStream(TEST_PROPS_2_DEFAULT);
        IOUtils.copy(baseIn2Default, baseOut2Default);

        baseIn2Default.reset();
        test2DefaultProperties = new Properties();
        test2DefaultProperties.load(baseIn2Default);

        baseIn2Default.close();
        baseOut2Default.close();

        TRANSLATOR1 = new Translator<Key1>()
        {
            @Override
            public String getPropertyName(Key1 propertyKey)
            {
                return propertyKey.name().toLowerCase().replace('_', '-');
            }

            @Override
            public Key1 getPropertyKey(String propertyName)
            {
                String enumName = propertyName.toUpperCase().replace('-', '_');
                return Key1.valueOf(enumName);
            }
        };

        EXECUTOR = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
        EXECUTOR.shutdownNow();
        FileUtils.deleteDirectory(TEST_DATA_DIR);
    }

    private PropertiesManager<Key1> test1Manager;
    private PropertiesManager<Key2> test2Manager;
    
    private EventMonitor<Key1> monitor1;
    private EventMonitor<Key2> monitor2;

    @Before
    public void setUp() throws IOException
    {
        test1Manager = PropertiesManagers.newManager(TEST_PROPS_1, Key1.class, TRANSLATOR1, EXECUTOR);
        test1Manager.addPropertyListener(monitor1 = new EventMonitor<Key1>());
        test1Manager.load();

        test2Manager = PropertiesManagers.newManager(TEST_PROPS_2, TEST_PROPS_2_DEFAULT, Key2.class, EXECUTOR);
        test2Manager.addPropertyListener(monitor2 = new EventMonitor<Key2>());
        test2Manager.load();
    }

    @Test
    public void testGetBooleanProperty()
    {
        Assert.assertEquals("Failed to retrieve boolean value",
                            test2Manager.getBooleanProperty(Key2.VALUE_BOOLEAN),
                            Boolean.parseBoolean(getTest2DefaultProperty(Key2.VALUE_BOOLEAN)));
    }

    @Test
    public void testGetIntegerProperty()
    {
        Assert.assertEquals("Failed to retrieve integer value",
                            test2Manager.getIntegerProperty(Key2.VALUE_INT),
                            Integer.parseInt(getTest2DefaultProperty(Key2.VALUE_INT)));
    }

    @Test
    public void testGetLongProperty()
    {
        Assert.assertEquals("Failed to retrieve long value",
                            test2Manager.getLongProperty(Key2.VALUE_LONG),
                            Long.parseLong(getTest2DefaultProperty(Key2.VALUE_LONG)));
    }

    @Test
    public void testGetFloatProperty()
    {
        Assert.assertEquals("Failed to retrieve float value",
                            test2Manager.getFloatProperty(Key2.VALUE_FLOAT),
                            Float.parseFloat(getTest2DefaultProperty(Key2.VALUE_FLOAT)),
                            FLOATING_POINT_DELTA);
    }

    @Test
    public void testGetDoubleProperty()
    {
        Assert.assertEquals("Failed to retrieve double value",
                            test2Manager.getDoubleProperty(Key2.VALUE_DOUBLE),
                            Double.parseDouble(getTest2DefaultProperty(Key2.VALUE_DOUBLE)),
                            FLOATING_POINT_DELTA);
    }

    @Test
    public void testGetEnumProperty()
    {
        Assert.assertEquals("Failed to retrieve enum value",
                            test2Manager.getEnumProperty(Key2.VALUE_ENUM, Color.class),
                            Color.valueOf(getTest2DefaultProperty(Key2.VALUE_ENUM).toUpperCase()));
    }

    private String getTest2DefaultProperty(Key2 key)
    {
        return test2DefaultProperties.getProperty(test2Manager.getTranslator().getPropertyName(key));
    }

    @Test
    public void testAutoTrim()
    {
        test2Manager.setAutoTrim(false);
        String notTrimmed = test2Manager.getProperty(Key2.VALUE_STRING_TRIM);

        test2Manager.setAutoTrim(true);
        String trimmed = test2Manager.getProperty(Key2.VALUE_STRING_TRIM);

        Assert.assertFalse("The trimmed and not trimmed values should not be equal",
                           trimmed.equals(notTrimmed));
        Assert.assertTrue("The trimmed and not trimmed values should be equal after manually trimming",
                          trimmed.equals(notTrimmed.trim()));
    }

    @Test
    public void testKeySet()
    {
        Assert.assertTrue("Default keys were not discovered properly",
                          test2Manager.keySet()
                                      .containsAll(EnumSet.allOf(Key2.class)));
    }
    
    @Test
    public void testIsLoadedFalse()
    {
        PropertiesManager<Key1> prom = PropertiesManagers.newManager(TEST_PROPS_1, Key1.class);
        Assert.assertFalse("Manager incorrectly reported that it was loaded", prom.isLoaded());
    }
    
    @Test
    public void testIsLoadedTrue() throws IOException
    {
        PropertiesManager<Key1> prom = PropertiesManagers.newManager(TEST_PROPS_1, Key1.class);
        prom.load();
        
        Assert.assertTrue("Manager incorrectly reported that it was not loaded", prom.isLoaded());
    }
    
    @Test(expected=IllegalStateException.class)
    public void testEnsureLoaded() throws IOException
    {
        PropertiesManager<Key1> prom = PropertiesManagers.newManager(TEST_PROPS_1, Key1.class);
        prom.getProperty(Key1.SOME_KEY);
    }
    
    @Test
    public void testManagedPropertiesCached()
    {
        Assert.assertSame("Single property managers are not cached correctly",
                          test1Manager.getManagedProperty(Key1.SOME_KEY),
                          test1Manager.getManagedProperty(Key1.SOME_KEY));
    }
    
    @Test
    public void testSetPropertyString()
    {
        final String value = "a non-default value";
        test1Manager.setProperty(Key1.SOME_KEY, value);
        Assert.assertEquals("Failed to set string property",
                            value,
                            test1Manager.getProperty(Key1.SOME_KEY));
    }

    @Test
    public void testSetPropertyEnum()
    {
        final Color value = Color.BLUE;
        test1Manager.setProperty(Key1.SOME_KEY, value);
        Assert.assertEquals("Failed to set Enum property",
                            value,
                            test1Manager.getEnumProperty(Key1.SOME_KEY,
                                                         Color.class));
    }
    
    @Test
    public void testResetPropertyToDefault()
    {
        test1Manager.setProperty(Key1.SOME_KEY, "a non-default value");
        test1Manager.resetProperty(Key1.SOME_KEY);
        Assert.assertEquals("Failed to reset an individual property",
                            Key1.SOME_KEY.getDefaultValue(),
                            test1Manager.getProperty(Key1.SOME_KEY));
    }
    
    @Test
    public void testEventNotSentWhenSetDoesNotChangeValue()
    {
        test1Manager.setProperty(Key1.SOME_KEY, Key1.SOME_KEY.getDefaultValue());
        Assert.assertFalse("Invalid event generated", monitor1.isChanged());
    }
    
    @Test
    public void testEventNotSentWhenResetDoesNotChangeValue()
    {
        test1Manager.resetProperty(Key1.SOME_KEY);
        Assert.assertFalse("Invalid event generated", monitor1.isReset());
    }
    
    public static class EventMonitor<T extends Enum<T>> implements PropertyListener<T>
    {
        private final AtomicInteger saved = new AtomicInteger(0);
        private final AtomicInteger reset = new AtomicInteger(0);
        private final AtomicInteger loaded = new AtomicInteger(0);
        private final AtomicInteger changed = new AtomicInteger(0);
        
        @Override
        public void saved(PropertyEvent<T> event)
        {
            saved.getAndIncrement();
        }
        
        @Override
        public void reset(PropertyEvent<T> event)
        {
            reset.getAndIncrement();
        }
        
        @Override
        public void loaded(PropertyEvent<T> event)
        {
            loaded.getAndIncrement();
        }
        
        @Override
        public void changed(PropertyEvent<T> event)
        {
            changed.getAndIncrement();
        }

        public boolean isSaved()
        {
            return getSavedCount() > 0;
        }

        public boolean isReset()
        {
            return getResetCount() > 0;
        }

        public boolean isLoaded()
        {
            return getLoadedCount() > 0;
        }

        public boolean isChanged()
        {
            return getChangedCount() > 0;
        }
        
        public int getSavedCount()
        {
            return saved.get();
        }
        
        public int getResetCount()
        {
            return reset.get();
        }
        
        public int getLoadedCount()
        {
            return loaded.get();
        }
        
        public int getChangedCount()
        {
            return changed.get();
        }
    }

    public static enum Key1 implements Defaultable
    {
        SOME_KEY("some key's value!");

        private String defaultValue;

        private Key1(String defaultValue)
        {
            this.defaultValue = defaultValue;
        }

        @Override
        public String getDefaultValue()
        {
            return defaultValue;
        }
    }

    public static enum Key2
    {
        VALUE_BOOLEAN,
        VALUE_INT,
        VALUE_LONG,
        VALUE_FLOAT,
        VALUE_DOUBLE,
        VALUE_ENUM,
        VALUE_STRING,
        VALUE_STRING_TRIM,
        VALUE_NESTED;
    }

    public static enum Color
    {
        RED, GREEN, BLUE;
    }
}
