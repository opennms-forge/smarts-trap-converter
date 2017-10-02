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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.fusesource.jansi.AnsiConsole;
import org.kohsuke.args4j.Option;
import org.opennms.netmgt.xml.eventconf.AlarmData;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.tools.SmartsTools;
import org.opennms.tools.SmartsTrapDef;
import org.opennms.tools.smarts.config.SmartsToolsConfig;
import org.opennms.tools.smarts.config.SmartsToolsConfigDao;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import static org.opennms.tools.OpenNMSEvents.getFirstOrNull;

public class AuditCommand implements Command {
    @Option(name = "-c", usage = "yaml configuration", metaVar = "CONFIG")
    private File configFile = new File("smarts-tools.yaml");

    @Option(name = "-o", usage = "csv output", metaVar = "OUTPUT")
    private File out;

    @Override
    public void execute() throws Exception {
        final SmartsToolsConfigDao configDao = new SmartsToolsConfigDao(configFile);
        final SmartsToolsConfig config = configDao.getConfig();
        final SmartsTools smartsTools = new SmartsTools(config);

        AnsiConsole.systemInstall();

        int missingEvents = 0;
        final Map<SmartsTrapDef, List<Event>> defToEventMap = smartsTools.getDefinitionToEventMappings();
        DEFS: for (Map.Entry<SmartsTrapDef, List<Event>> entry : defToEventMap.entrySet()) {
            final SmartsTrapDef def = entry.getKey();
            final List<Event> events = entry.getValue();

            if (events.isEmpty()) {
                System.out.println( ansi().fg(RED).a(String.format("No matching event for %s", getDefinitionKey(def))).reset() );
                missingEvents++;
            }
        }

        if (missingEvents > 0) {
            System.out.println( ansi().fg(RED).a(String.format("%s missing events.", missingEvents)).reset() );
        } else {
            System.out.println( ansi().fg(GREEN).a(String.format("No missing events.", missingEvents)).reset() );
        }

        if (out != null) {
            doCsvOutput(defToEventMap);
        }
    }

    private void doCsvOutput(final Map<SmartsTrapDef, List<Event>> defToEventMap) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.withHeader(
                "(S)Enterprise", "(S)Generic", "(S)Specific",
                "(S)Name", "(S)ClassName", "(S)State", "(S)EventType", "(S)InstanceName",
                "(O)UEI", "(O)Label",
                "(O)Enterprise", "(O)Generic", "(O)Specific",
                "(O)Alarm Type", "(O)Reduction Key", "(O)Clear Key");
        try(CSVPrinter printer = format.print(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<SmartsTrapDef, List<Event>> entry : defToEventMap.entrySet()) {
                final SmartsTrapDef def = entry.getKey();
                final List<Event> events = entry.getValue();

                // Smarts information
                final String smartsEnterprise = def.getEnterprise();
                final String smartsGeneric = def.getTrapNumber();
                final String smartsSpecific = def.getSpecific();
                final String name = def.getEventName();
                final String className = def.getClassName();
                final String state = def.getState();
                final String eventType = def.getEventType();
                final String instanceName = def.getInstanceName();

                if (events.isEmpty()) {
                    String eventUei = null;
                    String eventLabel = null;
                    String enterpriseId = null;
                    String genericId = null;
                    String specificId = null;
                    String alarmType = null;
                    String reductionKey = null;
                    String clearKey = null;

                    // Print!
                    printer.printRecord(smartsEnterprise, smartsGeneric, smartsSpecific,
                            name, className, state, eventType, instanceName,
                            eventUei, eventLabel,
                            enterpriseId, genericId, specificId,
                            alarmType, reductionKey, clearKey);
                }
                for (Event event : events) {
                    String eventUei = null;
                    String eventLabel = null;
                    String enterpriseId = null;
                    String genericId = null;
                    String specificId = null;
                    String alarmType = null;
                    String reductionKey = null;
                    String clearKey = null;


                    eventUei = event.getUei();
                    eventLabel = event.getEventLabel();

                    enterpriseId = getFirstOrNull(event, "id");
                    genericId = getFirstOrNull(event, "generic");
                    specificId = getFirstOrNull(event, "specific");

                    final AlarmData alarmData = event.getAlarmData();
                    if (alarmData != null) {
                        alarmType = Integer.toString(alarmData.getAlarmType());
                        reductionKey = alarmData.getReductionKey();
                        clearKey = alarmData.getClearKey();
                    }

                    // Print!
                    printer.printRecord(smartsEnterprise, smartsGeneric, smartsSpecific,
                            name, className, state, eventType, instanceName,
                            eventUei, eventLabel,
                            enterpriseId, genericId, specificId,
                            alarmType, reductionKey, clearKey);
                }
            }
        }
    }

    private static String getDefinitionKey(SmartsTrapDef def) {
        return String.format("%s (%s,%s,%s)", def.getEventName(),
                def.getEnterprise(), def.getTrapNumber(), def.getSpecific());
    }
}
