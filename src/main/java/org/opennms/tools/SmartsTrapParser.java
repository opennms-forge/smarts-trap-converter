/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

/**
 * Mishmash of code used to parse the Smarts trap definitions.
 */
public class SmartsTrapParser {

    private final File file;

    public SmartsTrapParser(File file) {
        this.file = Objects.requireNonNull(file);
    }

    public List<SmartsTrapDef> getTrapDefinitions() throws IOException {
        String conf = Files.asCharSource(file, Charsets.UTF_8).readLines(new LineProcessor<String>() {
            List<String> result = Lists.newArrayList();

            Pattern commentRegex = Pattern.compile("\\s*#.*");
            Pattern whiteSpaceRegex = Pattern.compile("^\\s*$");
            public boolean processLine(String line) {
                if (!commentRegex.matcher(line).matches() && !whiteSpaceRegex.matcher(line).matches()) {
                    result.add(line.trim());
                }
                return true;
            }

            public String getResult() {
                Joiner joiner = Joiner.on("\n").skipNulls();
                return joiner.join(result);
            }
          });

        List<SmartsTrapDef> trapDefs = parseTrapDefinitions(conf);
        Collections.sort(trapDefs, new Comparator<SmartsTrapDef>() {
            public int compare(SmartsTrapDef d1, SmartsTrapDef d2) {
                return d1.getEnterprise().compareTo(d2.getEnterprise());
            }
        });
        
        return trapDefs;
    }

    private List<SmartsTrapDef> parseTrapDefinitions(String conf) {
        final List<SmartsTrapDef> trapDefs = Lists.newArrayList();

        Pattern regex = Pattern.compile("BEGIN_TRAP (?<enterprise>.*?) (?<trapnumber>.*?) (?<specific>.*?)"
                + "\\s*\\n(?<definition>.*?)END_TRAP", Pattern.DOTALL);
        Matcher regexMatcher = regex.matcher(conf);
        while(regexMatcher.find()) {
            final String enterprise = regexMatcher.group("enterprise");
            final String trapNumber = regexMatcher.group("trapnumber");
            final String specific = regexMatcher.group("specific");
            final String definition = regexMatcher.group("definition");

            SmartsTrapDef def = new SmartsTrapDef();
            def.setEnterprise(enterprise);
            def.setTrapNumber(trapNumber);
            def.setSpecific(specific);
            parseDefinition(def, definition);
            trapDefs.add(def);
        }

        return trapDefs;
    }

    protected static void parseDefinition(SmartsTrapDef def, String definition) {
        Pattern regex = Pattern.compile("^\\s*(?<tag>.*?):\\s*(?<value>.*)\\s*$");

        boolean hasMap = false;
        List<String> lines = Splitter.on("\n").splitToList(definition);
        for (String line : lines) {
            Matcher m = regex.matcher(line);
            if (m.matches()) {
                final String tag = m.group("tag");
                final String value = m.group("value");
                switch(tag) {
                case "ClassName":
                    def.setClassName(value);
                    break;
                case "ElementName":
                    def.setElementName(value);
                    break;
                case "InstanceName":
                    def.setInstanceName(value);
                    break;
                case "EventName":
                    def.setEventName(value);
                    break;
                case "Severity":
                    try {
                        def.setSeverity(Integer.parseInt(value));
                    } catch (NumberFormatException nfe) {
                        def.setSeverity(-1);
                    }
                    break;
                case "Expiration":
                    def.setExpiration(value);
                    break;
                case "State":
                    def.setState(value);
                    break;
                case "EventText":
                    def.setEventText(value);
                    break;
                case "EventType":
                    def.setEventType(value);
                    break;
                case "UnknownAgent":
                    def.setUnknownAgent(value);
                    break;
                case "ClearOnAcknowledge":
                    def.setClearOnAcknowledge(value);
                    break;
                case "LogFile":
                    def.setLogFile(value);
                    break;
                case "Map":
                    hasMap = true;
                    break;

                //default:
                //    throw new RuntimeException("unknown tag:" + tag);
                }
            }
        }

        if (hasMap) {
            Pattern mapRegex = Pattern.compile(".*Map:.*?(?<value>\\{.*\\}).*", Pattern.DOTALL);
            Matcher m = mapRegex.matcher(definition);
            if (m.matches()) {
                def.setMap(parseMap(m.group("value")));
            }
        }
    }

    protected static Map<String, Map<String, String>> parseMap(String value) {
        /*        Map:                {
                              V1
                              0 = CLEAR
                              1 = NOTIFY
                            }
        */
        Map<String, Map<String, String>> map = new LinkedHashMap<>();

        Pattern s_bracketsPattern = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher m = s_bracketsPattern.matcher(value);

        while (m.find()) {
            String innerBrackets = m.group(1);
            String lines[] = innerBrackets.split("\n");

            String var = null;
            boolean first = true;
            for (String line : lines) {
                if (Strings.isNullOrEmpty(line.trim())) {
                    continue;
                }

                if (first) {
                    var = line.trim();
                    first = false;
                    map.put(var, new LinkedHashMap<>());
                    continue;
                }

                String parts[] = line.split("=");
                String lhs = parts[0].trim();
                String rhs = parts[1].trim();
                map.get(var).put(lhs, rhs);
            }
        }

        return map;
    }

    public SmartsTrapDef getTrapDefinition(String enterprise, String trapNumber, String specific) throws IOException {
        return getTrapDefinitions().stream()
                .filter(d -> enterprise.equals(d.getEnterprise()))
                .filter(d -> trapNumber.equals(d.getTrapNumber()))
                .filter(d -> specific.equals(d.getSpecific()))
                .findFirst()
                .get();
    }
}
