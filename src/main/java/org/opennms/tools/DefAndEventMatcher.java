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

import com.google.common.base.Strings;
import org.opennms.netmgt.config.DefaultEventConfDao;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.xml.eventconf.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.opennms.tools.OpenNMSEvents.getFirstOrNull;
import static org.opennms.tools.SmartsOidUtils.getMatchingOid;

/**
 * Attempts to match Smarts trap definitions to the corresponding OpenNMS events
 */
public class DefAndEventMatcher {
    private static final Logger LOG = LoggerFactory.getLogger(DefAndEventMatcher.class);
    private final DefaultEventConfDao eventConfDao;

    public DefAndEventMatcher(DefaultEventConfDao eventConfDao) {
        this.eventConfDao = Objects.requireNonNull(eventConfDao);
    }

    public List<Event> getMatchingEvents(SmartsTrapDef def) {
        // Try matching the exact enterprise, generic and specific
        List<Event> matches = match(def.getEnterprise(), def.getTrapNumber(), def.getSpecific());
        if (matches.size() > 0) {
            return matches;
        }
        // Next, try matching the formatted enterprise, generic and specific
        // (This will help match event definitions containing wildcards that we generated)
        matches = match(Converter.formatEnterprise(def.getEnterprise()), def.getTrapNumber(), def.getSpecific());
        if (matches.size() > 0) {
            return matches;
        }
        // There was no direct match, try searching using a generated event
        org.opennms.netmgt.xml.event.Event event = generateCorrepsondingEvent(def, true);
        org.opennms.netmgt.xml.eventconf.Event eventConf = eventConfDao.findByEvent(event);
        if (eventConf != null && !isEnterpriseDefault(eventConf)) {
            return Collections.singletonList(eventConf);
        }
        // Try it without the trailing dot
        event = generateCorrepsondingEvent(def, false);
        eventConf = eventConfDao.findByEvent(event);
        if (eventConf != null && !isEnterpriseDefault(eventConf)) {
            return Collections.singletonList(eventConf);
        }
        return Collections.emptyList();
    }

    private List<Event> match(String enterprise, String trapNumer, String specific) {
        final String defKey = String.format("%s/%s/%s", enterprise, trapNumer, specific);
        return eventConfDao.getAllEvents().stream()
                .filter(e -> {
                    final String eventKey = String.format("%s/%s/%s",
                            getFirstOrNull(e, "id"), getFirstOrNull(e, "generic"), getFirstOrNull(e, "specific"));
                    return defKey.equals(eventKey);
                })
                .collect(Collectors.toList());
    }

    private static boolean isEnterpriseDefault(org.opennms.netmgt.xml.eventconf.Event event) {
        return event.getUei() != null && event.getUei().endsWith("EnterpriseDefault");
    }

    /**
     * Given some definition, generate an event that would match the filter.
     *
     * @param smartsTrapDef
     * @return
     */
    private static org.opennms.netmgt.xml.event.Event generateCorrepsondingEvent(SmartsTrapDef def, boolean removeLastPortionOfEnterprise) {
        final EventBuilder eventBuilder = new EventBuilder(null, "smarter");

        final String enterprise = getMatchingOid(def.getEnterprise());
        if (!Strings.isNullOrEmpty(enterprise)) {
            final String betterEnterprise;
            if (!enterprise.startsWith(".")) {
                // Prepend a "." if missing
                betterEnterprise = "." + enterprise;
            } else {
                betterEnterprise = enterprise;
            }

            final String effectiveEnterprise;
            if (removeLastPortionOfEnterprise && betterEnterprise.endsWith(".0")) {
                effectiveEnterprise = betterEnterprise.substring(0, betterEnterprise.length() - 2);
            } else {
                effectiveEnterprise = betterEnterprise;
            }
            eventBuilder.setEnterpriseId(effectiveEnterprise);
        }

        final String trapNumber = getMatchingOid(def.getTrapNumber());
        if (!Strings.isNullOrEmpty(trapNumber)) {
            eventBuilder.setGeneric(Integer.parseInt(trapNumber));
        }

        final String specific = getMatchingOid(def.getSpecific());
        if (!Strings.isNullOrEmpty(specific)) {
            eventBuilder.setSpecific(Integer.parseInt(specific));
        }

        return eventBuilder.getEvent();
    }
}
