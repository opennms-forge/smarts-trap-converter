/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.tools;

import com.google.common.collect.Iterables;
import org.opennms.netmgt.config.DefaultEventConfDao;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.Mask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for loading and handling OpenNMS events.
 */
public class OpenNMSEvents {
    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSEvents.class);

    private final DefaultEventConfDao eventConfDao;

    public OpenNMSEvents(File file) {
        eventConfDao = new DefaultEventConfDao();
        try {
            eventConfDao.setConfigResource(new FileSystemResource(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        eventConfDao.afterPropertiesSet();
    }

    public OpenNMSEvents(DefaultEventConfDao eventConfDao) throws IOException {
        this.eventConfDao = Objects.requireNonNull(eventConfDao);
    }

    public DefAndEventMatcher getMatcher() {
        return new DefAndEventMatcher(eventConfDao);
    }

    public Map<SmartsTrapDef, List<Event>> mapDefinitionsToEventConfs(Set<SmartsTrapDef> defs) {
        final Map<SmartsTrapDef, List<Event>> defToEventConf = new LinkedHashMap<>();
        final DefAndEventMatcher matcher = getMatcher();
        for (SmartsTrapDef def : defs) {
            defToEventConf.put(def, matcher.getMatchingEvents(def));
        }
        return defToEventConf;
    }

    public void clearVarbinds() {
        // Clear all of the varbinds in the mask!
        for (org.opennms.netmgt.xml.eventconf.Event event : eventConfDao.getAllEvents()) {
            final Mask mask = event.getMask();
            if (mask == null) {
                continue;
            }
            mask.getVarbinds().clear();
        }
        eventConfDao.saveCurrent();
        eventConfDao.reload();
    }

    public static String getFirstOrNull(Event e, String maskElement) {
        final List<String> vals = e.getMaskElementValues(maskElement);
        if (vals != null) {
            return Iterables.getFirst(vals, null);
        } else {
            return null;
        }
    }

}
