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

import java.util.Properties;

/**
 * This class provides a set of utilities for interacting with the
 * {@link PropertyDescriptor} Enums. Most client code should not need this utilities,
 * except for {@link #getPropertyName(Enum)} which most {@link PropertyDescriptor}
 * implementations will use to delegate {@link PropertyDescriptor#getPropertyName()}.
 *
 * @author Gregory P. Moyer
 */
public class PropertyDescriptorUtils
{
    /**
     * Translate a property key into an Enum.
     *
     * @param <T>
     *            the type of Enum to use as the key representative
     * @param enumType
     *            the class that contains the Enum constant that will be returned
     * @param property
     *            the property to translate
     * @return the Enum value representing the given property
     * @throws IllegalArgumentException
     *             if the given Enum type does not have an appropriate value
     * @throws NullPointerException
     *             if the property is <code>null</code>
     */
    public static <T extends Enum<T> & PropertyDescriptor> T getPropertyDescriptor(Class<T> enumType,
                                                                                   String property)
    {
        String enumName = property.toUpperCase().replace('.', '_');
        return Enum.valueOf(enumType, enumName);
    }

    /**
     * Translate an Enum into a property name.
     *
     * @param <T>
     *            the type of Enum to be converted
     * @param descriptor
     *            the Enum instance to convert
     * @return a property name (key) that represents the given Enum instance
     */
    public static <T extends Enum<T> & PropertyDescriptor> String getPropertyName(T descriptor)
    {
        return descriptor.name().toLowerCase().replace('_', '.');
    }

    /**
     * Retrieve a {@link Properties} instance that contains all of the default values
     * defined for the given {@link PropertyDescriptor}.<br>
     * <br>
     * This method only makes sense when the default values are defined with the Enum
     * constants.
     *
     * @param <T>
     *            the type of {@link PropertyDescriptor descriptors} whose default values
     *            are requested
     * @param enumType
     *            the class that contains the appropriate {@link PropertyDescriptor
     *            descriptors}
     * @param translator
     *            a translator to convert between {@link PropertyDescriptor descriptors}
     *            and property names
     * @return a {@link Properties} instance containing the default values stored in the
     *         given {@link PropertyDescriptor descriptor} type
     */
    public static <T extends Enum<T> & PropertyDescriptor> Properties getDefaultProperties(Class<T> enumType,
                                                                                           Translator<T> translator)
    {
        Properties defaults = new Properties();

        for (T descriptor : enumType.getEnumConstants())
        {
            defaults.setProperty(translator.getPropertyName(descriptor),
                                 descriptor.getDefaultValue());
        }

        return defaults;
    }

    /**
     * Get the default translator to convert back and forth between Enums and property
     * names (keys).
     *
     * @param <T>
     *            the type of Enum representing the properties
     * @param enumType
     *            the Enum class used to represent the properties
     * @return the default translator implementation
     */
    public static <T extends Enum<T> & PropertyDescriptor> Translator<T> getDefaultTranslator(final Class<T> enumType)
    {
        return new Translator<T>()
        {
            @Override
            public String getPropertyName(T propertyDescriptor)
            {
                return PropertyDescriptorUtils.getPropertyName(propertyDescriptor);
            }

            @Override
            public T getPropertyDescriptor(String propertyName)
            {
                return PropertyDescriptorUtils.getPropertyDescriptor(enumType, propertyName);
            }
        };
    }

    /**
     * This is a static utility class for which there is no reason to create an instance.
     */
    private PropertyDescriptorUtils()
    {
        /*
         * Static utility class.
         */
    }
}