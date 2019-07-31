/*
 * Copyright 2019 Björn Kautler
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

package net.kautler.command.restriction;

import net.kautler.command.api.restriction.Restriction;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A directory of restrictions that can be looked up by their type.
 *
 * @param <M> the class of the messages this lookup can provide
 */
public class RestrictionLookup<M> {
    /**
     * The restrictions.
     */
    private final Collection<Restriction<? super M>> restrictions = new CopyOnWriteArrayList<>();

    /**
     * The restrictions by class. As the actual restriction instances are proxied by CDI, this map cannot be
     * built automatically from the available restrictions, but only on the fly.
     */
    private final Map<Class<?>, Restriction<? super M>> restrictionByClass = new ConcurrentHashMap<>();

    /**
     * Adds the given restrictions to the set of available restrictions in this lookup.
     *
     * @param restrictions the restrictions to add
     */
    public void addAllRestrictions(Collection<Restriction<? super M>> restrictions) {
        this.restrictions.addAll(restrictions);
    }

    /**
     * Returns the restriction instance that fits to the given class or {@code null}.
     *
     * @param restrictionClass the restriction class to look up.
     * @return the restriction instance that fits to the given class or {@code null}
     */
    public Restriction<? super M> getRestriction(Class<?> restrictionClass) {
        return restrictionByClass
                .computeIfAbsent(restrictionClass,
                        key -> restrictions.stream()
                                // we cannot use a map as the classes are proxied by CDI
                                .filter(key::isInstance)
                                .findAny()
                                .orElse(null));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RestrictionLookup.class.getSimpleName() + "[", "]")
                .add("restrictions=" + restrictions)
                .add("restrictionByClass=" + restrictionByClass)
                .toString();
    }
}
