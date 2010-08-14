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

/**
 * This interface defines added functionality required of Enums to be used with the
 * {@link PropertiesManager}.
 *
 * @author Gregory P. Moyer
 */
public interface PropertyDescriptor
{
    /**
     * This method is not meant to be used directly by client code. The API provided by
     * {@link PropertiesManager} and {@link ManagedProperty} will determine where to find
     * the default value when necessary. This value may be <code>null</code> if the
     * defaults are stored in a separate file.
     *
     * @return the default value of this property or <code>null</code> if defaults are
     *         stored in a separate file
     */
    public String getDefaultValue();
}