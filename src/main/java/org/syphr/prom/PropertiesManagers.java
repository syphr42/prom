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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class provides builder methods to construct {@link PropertiesManager
 * managers} with various options.
 * 
 * @author Gregory P. Moyer
 */
public class PropertiesManagers
{
    /**
     * Build a new manager for the given properties file.
     * 
     * @param <T>
     *            the type of key used for the new manager
     * 
     * @param file
     *            the file system location of the properties represented here
     * @param descriptorType
     *            the enumeration of keys in the properties file
     * @return a new manager
     */
    public static <T extends Enum<T> & PropertyDescriptor> PropertiesManager<T> newManager(File file,
                                                                                           Class<T> descriptorType)
    {
        return new PropertiesManager<T>(file,
                                        descriptorType,
                                        PropertyDescriptorUtils.getDefaultTranslator(descriptorType),
                                        new DefaultEvaluator(),
                                        Executors.newCachedThreadPool());
    }

    /**
     * Build a new manager for the given properties file.
     * 
     * @param <T>
     *            the type of key used for the new manager
     * 
     * @param file
     *            the file system location of the properties represented here
     * @param descriptorType
     *            the enumeration of keys in the properties file
     * @param executor
     *            a service to handle potentially long running tasks, such as
     *            interacting with the file system
     * @return a new manager
     */
    public static <T extends Enum<T> & PropertyDescriptor> PropertiesManager<T> newManager(File file,
                                                                                           Class<T> descriptorType,
                                                                                           ExecutorService executor)
    {
        return new PropertiesManager<T>(file,
                                        descriptorType,
                                        PropertyDescriptorUtils.getDefaultTranslator(descriptorType),
                                        new DefaultEvaluator(),
                                        executor);
    }

    /**
     * Build a new manager for the given properties file.
     * 
     * @param <T>
     *            the type of key used for the new manager
     * 
     * @param file
     *            the file system location of the properties represented here
     * @param descriptorType
     *            the enumeration of keys in the properties file
     * @param translator
     *            the translator to convert between Enum names and property keys
     * @param executor
     *            a service to handle potentially long running tasks, such as
     *            interacting with the file system
     * @return a new manager
     */
    public static <T extends Enum<T> & PropertyDescriptor> PropertiesManager<T> newManager(File file,
                                                                                           Class<T> descriptorType,
                                                                                           Translator<T> translator,
                                                                                           ExecutorService executor)
    {
        return new PropertiesManager<T>(file,
                                        descriptorType,
                                        translator,
                                        new DefaultEvaluator(),
                                        executor);
    }

    /**
     * Build a new manager for the given properties file.
     * 
     * @param <T>
     *            the type of key used for the new manager
     * 
     * @param file
     *            the file system location of the properties represented here
     * @param descriptorType
     *            the enumeration of keys in the properties file
     * @param evaluator
     *            the evaluator to convert nested property references into fully
     *            evaluated strings
     * @param executor
     *            a service to handle potentially long running tasks, such as
     *            interacting with the file system
     * @return a new manager
     */
    public static <T extends Enum<T> & PropertyDescriptor> PropertiesManager<T> newManager(File file,
                                                                                           Class<T> descriptorType,
                                                                                           Evaluator evaluator,
                                                                                           ExecutorService executor)
    {
        return new PropertiesManager<T>(file,
                                        descriptorType,
                                        PropertyDescriptorUtils.getDefaultTranslator(descriptorType),
                                        evaluator,
                                        executor);
    }

    /**
     * Private constructor. This class implements a factory pattern to build
     * {@link PropertiesManager managers} and can not be instantiated.
     */
    private PropertiesManagers()
    {
        /*
         * Factory pattern.
         */
    }
}
