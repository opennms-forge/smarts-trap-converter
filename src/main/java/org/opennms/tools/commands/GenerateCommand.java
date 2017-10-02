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

package org.opennms.tools.commands;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.kohsuke.args4j.Option;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.features.mibcompiler.services.JsmiMibParser;
import org.opennms.netmgt.config.DefaultEventConfDao;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.Events;
import org.opennms.tools.Converter;
import org.opennms.tools.DefAndEventMatcher;
import org.opennms.tools.OpenNMSEvents;
import org.opennms.tools.SmartsTools;
import org.opennms.tools.SmartsTrapDef;
import org.opennms.tools.smarts.config.MibMappingConfig;
import org.opennms.tools.smarts.config.SmartsToolsConfig;
import org.opennms.tools.smarts.config.SmartsToolsConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.opennms.tools.OpenNMSEvents.getFirstOrNull;

public class GenerateCommand implements Command {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateCommand.class);

    @Option(name = "-c", usage = "yaml configuration", metaVar = "CONFIG")
    private File configFile = new File("smarts-tools.yaml");

    private final JsmiMibParser mibParser = new JsmiMibParser();
    private SmartsToolsConfig config;
    private SmartsTools smartsTools;
    private final Map<String, Events> generatedEventsByFile = new LinkedHashMap<>();

    @Override
    public void execute() throws Exception {
        final SmartsToolsConfigDao configDao = new SmartsToolsConfigDao(configFile);
        config = configDao.getConfig();
        smartsTools = new SmartsTools(config);

        // SMARTS Definitions
        final List<SmartsTrapDef> smartsTrapDefs = smartsTools.getSmartsTrapDefinitions();

        // Existing events in OpenNMS
        final OpenNMSEvents nmsEvents = smartsTools.getOpenNMSEvents();

        // Now try and match each of the SMARTS trap definitions against
        // corresponding event definitions
        final Map<SmartsTrapDef, List<Event>> defToEventConf = nmsEvents.mapDefinitionsToEventConfs(Sets.newLinkedHashSet(smartsTrapDefs));

        // Find the definitions that don't have any existing event configuration
        final Set<SmartsTrapDef> defsWithoutEventConf = defToEventConf.entrySet()
                .stream()
                .filter(e -> e.getValue().isEmpty())
                .map(e -> e.getKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Keep track of the definitions which have no event conf and
        // were not enriched with any MIB data
        final Set<SmartsTrapDef> defsWithoutMibs = new LinkedHashSet<>();
        defsWithoutMibs.addAll(defsWithoutEventConf);

        // Attempt to enrich the definitions with MIB data
        for (MibMappingConfig mappingConfig : config.getMibs().getMappings()) {
            // Find the definitions that match
            final Set<SmartsTrapDef> matchingDefs = getMatchingDefinitions(mappingConfig, defsWithoutEventConf);
            if (matchingDefs.isEmpty()) {
                continue;
            }
            // Generate the events from the MIBs
            final Events eventsFromMibs = generateEventsFromMibs(mappingConfig);
            // Generate the events from the definitions and enrich those
            // with the information from the MIBs
            // NOTE: This will throw an exception if one of the matched definitions cannot be matched
            // with a generated event from the MIB
            final Events eventsToSave = generateEventsFromDefs(matchingDefs, eventsFromMibs, mappingConfig.getUeiPrefix());
            generatedEventsByFile.put(mappingConfig.getTarget(), eventsToSave);
            defsWithoutMibs.removeAll(matchingDefs);
        }

        // Handle the events that were not enriched
        final Events eventsFromDefs = generateEventsFromDefs(defsWithoutMibs);
        generatedEventsByFile.put("Smarts.nomibs.events.xml", eventsFromDefs);

        // Enrich any existing event configuration that may not have alarm data
        final Events existingEventsEnriched = enrichExistingEvents(defToEventConf);
        generatedEventsByFile.put("Smarts.enriched.events.xml", existingEventsEnriched);


        // Save
        for (Map.Entry<String, Events> entry : generatedEventsByFile.entrySet()) {
            final String fileName = entry.getKey();
            final Events events = entry.getValue();
            final File eventsFile = Paths.get(config.getOutput().getEvents(), fileName).toFile();
            LOG.info("Writing {} events to {}.", events.getEvents().size(), eventsFile);
            JaxbUtils.marshal(events, eventsFile);
        }
    }

    private Events enrichExistingEvents(Map<SmartsTrapDef, List<Event>> defToEventConfs) {
        final Events existingEventsEnriched = new Events();
        final Converter c = new Converter();
        for (SmartsTrapDef def : defToEventConfs.keySet()) {
            final List<Event> existingEventConfs = defToEventConfs.get(def);
            final Event firstExistingEvent = Iterables.getFirst(existingEventConfs, null);
            if (firstExistingEvent == null) {
                // No existing event conf
                continue;
            }
            final List<Event> generatedEventConfs = c.convert(def);
            final Event firstGeneratedEvent = Iterables.getFirst(generatedEventConfs, null);
            if (generatedEventConfs.size() == 1 && existingEventConfs.size() >= 1) {
                // Use data from the first existing event
                firstGeneratedEvent.setDescr(firstExistingEvent.getDescr());
                firstGeneratedEvent.setEventLabel(firstExistingEvent.getEventLabel());
                firstGeneratedEvent.setUei(firstExistingEvent.getUei());
                if (!firstExistingEvent.getVarbindsdecodes().isEmpty()) {
                    firstGeneratedEvent.setVarbindsdecodes(firstExistingEvent.getVarbindsdecodes());
                }
                // There's a single event, so it must be of type 3
                firstGeneratedEvent.getAlarmData().setAlarmType(3);
                existingEventsEnriched.addEvent(firstGeneratedEvent);
            } else if (generatedEventConfs.size() == 2 && existingEventConfs.size() == 1) {
                for (Event generatedEventConf : generatedEventConfs) {
                    generatedEventConf.setDescr(firstExistingEvent.getDescr());
                    generatedEventConf.setEventLabel(firstExistingEvent.getEventLabel());
                    if (!firstExistingEvent.getVarbindsdecodes().isEmpty()) {
                        generatedEventConf.setVarbindsdecodes(firstExistingEvent.getVarbindsdecodes());
                    }
                    existingEventsEnriched.addEvent(firstGeneratedEvent);
                }
            } else {
                for (Event existingEventConf : existingEventConfs) {
                    // Use the generate log message and parameters
                    existingEventConf.setLogmsg(firstGeneratedEvent.getLogmsg());
                    existingEventConf.setParameters(firstGeneratedEvent.getParameters());
                    existingEventsEnriched.addEvent(existingEventConf);
                }
            }
        }
        return existingEventsEnriched;
    }

    private Set<SmartsTrapDef> getMatchingDefinitions(MibMappingConfig mappingConfig, Set<SmartsTrapDef> defs) {
        return defs.stream()
                .filter(def -> def.getEnterprise() != null)
                .filter(def -> {
                    if (mappingConfig.getEnterprisePrefix().isEmpty()) {
                        return true;
                    }
                    for (String prefix : mappingConfig.getEnterprisePrefix()) {
                        if (def.getEnterprise().startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                })
                /*.filter(def -> {
                    String key = String.format("%s/%s/%s", def.getEnterprise(), def.getTrapNumber(), def.getSpecific());
                    for (String specific : specificExcludes) {
                        if (key.equals(specific)) {
                            return false;
                        }
                    }
                    return true;
                })*/
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Events generateEventsFromDefs(Set<SmartsTrapDef> defs, Events eventsFromMibs, String ueiPrefix) throws Exception {
        DefaultEventConfDao eventConfDao = getDefaultEventConfDaoWithEvents(eventsFromMibs);
        DefAndEventMatcher matcher = new DefAndEventMatcher(eventConfDao);
        final Converter c = new Converter();
        if (ueiPrefix != null) {
            c.setUeiPrefix(ueiPrefix);
        }

        final  Events eventToSave = new Events();
        for (SmartsTrapDef def : defs) {
            final List<Event> matchingEvents = matcher.getMatchingEvents(def);
            final Event matchingEvent = Iterables.getFirst(matchingEvents, null);
            if (matchingEvent == null) {
                throw new Exception("No matching event definition found in MIB for: " + def);
            }
            for (Event e : c.convert(def)) {
                // Keep some stuff from the MIB compiled version:
                e.setVarbindsdecodes(matchingEvent.getVarbindsdecodes());
                e.setDescr(matchingEvent.getDescr());
                e.setEventLabel(matchingEvent.getEventLabel());
                eventToSave.addEvent(e);
            }
        }
        return eventToSave;
    }


    private Events generateEventsFromDefs(Set<SmartsTrapDef> defsWithoutMibs) {
        Converter c = new Converter();
        Events events = new Events();
        defsWithoutMibs.stream()
                .map(def -> c.convert(def))
                .forEach(e -> {
                    for (Event ee : e) {
                        events.addEvent(ee);
                    }
                });
        return events;
    }


    private Events generateEventsFromMibs(MibMappingConfig mappingConfig) {
        Objects.requireNonNull(config.getMibs().getDir());
        mibParser.setMibDirectory(new File(config.getMibs().getDir()));

        final Events eventsFromMibs = new Events();
        for (String mibFileName : mappingConfig.getMibs()) {
            final File mibFile = Paths.get(config.getMibs().getDir(), mibFileName).toFile();
            if (!mibParser.parseMib(mibFile)) {
                if (mibParser.getMissingDependencies().size() > 0) {
                    throw new RuntimeException(String.format("Error parsing %s. Missing dependencies: %s", mibFile, mibParser.getMissingDependencies()));
                } else {
                    throw new RuntimeException(String.format("Error parsing %s: %s", mibFile, mibParser.getFormattedErrors()));
                }
            }
            Objects.requireNonNull(mappingConfig.getUeiPrefix());
            for (Event e : mibParser.getEvents(mappingConfig.getUeiPrefix()).getEvents()) {
                LOG.info("Generated {}: {}/{}/{}", e.getUei(), getFirstOrNull(e, "id"), getFirstOrNull(e, "generic"), getFirstOrNull(e, "specific"));
                eventsFromMibs.addEvent(e);
            }
        }

        return eventsFromMibs;
    }

    private static DefaultEventConfDao getDefaultEventConfDaoWithEvents(Events events) throws IOException {
        final File eventconf = Files.createTempFile("events", ".xml").toFile();
        eventconf.deleteOnExit();
        JaxbUtils.marshal(events, eventconf);

        final DefaultEventConfDao eventConfDao = new DefaultEventConfDao();
        eventConfDao.setConfigResource(new FileSystemResource(eventconf));
        eventConfDao.afterPropertiesSet();

        return eventConfDao;
    }
}
