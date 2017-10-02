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

import com.google.common.base.Strings;
import org.opennms.netmgt.xml.eventconf.AlarmData;
import org.opennms.netmgt.xml.eventconf.Decode;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.LogDestType;
import org.opennms.netmgt.xml.eventconf.Logmsg;
import org.opennms.netmgt.xml.eventconf.Mask;
import org.opennms.netmgt.xml.eventconf.Maskelement;

import com.google.common.collect.Lists;
import org.opennms.netmgt.xml.eventconf.Parameter;
import org.opennms.netmgt.xml.eventconf.Varbind;
import org.opennms.netmgt.xml.eventconf.Varbindsdecode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a {@link SmartsTrapDef} to a {@link org.opennms.netmgt.xml.eventconf.Event}.
 */
public class Converter {
    private static final Logger LOG = LoggerFactory.getLogger(Converter.class);
    private static final Pattern s_variableSubsitutionPattern = Pattern.compile("\\$(.*?)\\$");
    private static final Pattern s_variablePattern = Pattern.compile("^V(\\d+|\\*+)$");
    private static final Pattern s_oidPattern = Pattern.compile("^OID(\\d+|\\*)$");

    private static final String DEFAULT_UEI_PREFIX = "uei.opennms.org/smarts";
    private String ueiPrefix = DEFAULT_UEI_PREFIX;

    public void setUeiPrefix(String ueiPrefix) {
        this.ueiPrefix = ueiPrefix;
    }

    public List<Event> convert(SmartsTrapDef def) {
        if (def.getState() == null || def.getState().equals(replaceVariables(def.getState()))) {
            // The state hasn't changed after the variable substitution, so it must be static
            return Collections.singletonList(buildEvent(def, def.getState(), null, null));
        } else {
            // There are 1+ variables reference in the state
            return handleDefWithDynamicState(def);
        }
    }

    private List<Event> handleDefWithDynamicState(SmartsTrapDef def) {
        final List<Event> events = new ArrayList<>();

        final Matcher m = s_variableSubsitutionPattern.matcher(def.getState());
        if (m.find()) {
            String token = m.group(1);
            Integer varbindIndex = null;

            Matcher mm = s_variablePattern.matcher(token);
            if (mm.matches()) {
                varbindIndex = Integer.parseInt(mm.group(1));
            }

            Map<String, String> tokenMappings = def.getMap().get(token);
            if (tokenMappings == null) {
                throw new IllegalStateException(String.format("No map found for %s in def: %s", token, def.getEventName()));
            }

            for (Map.Entry<String, String> tokenMapping : tokenMappings.entrySet()) {
                // The key is the variable value
                String varbindValue = tokenMapping.getKey();
                // The value is the actual state
                String state = tokenMapping.getValue();

                events.add(buildEvent(def, state, varbindIndex, varbindValue));
            }
        }

        return events;
    }

    private Event buildEvent(SmartsTrapDef def, String state, Integer varbindIndex, String varbindValue) {
        Integer alarmType = null;
        if (state != null) {
            switch(state.toUpperCase()) {
                case "NOTIFY":
                    alarmType = 1;
                    break;
                case "CLEAR":
                    alarmType = 2;
                    break;
            }
        }

        boolean isDurable = true;
        if ("MOMENTARY".equals(def.getEventType()) &&
                alarmType != null && alarmType == 1) {
            // If the event is marked as momentary, it normally doesn't have any clear
            alarmType = 3;
        }

        String eventName = def.getEventName();
        String uei = String.format("%s/%s", ueiPrefix, eventName);
        String ueiToClear = null;
        if (alarmType != null && alarmType == 2) {
            eventName = def.getEventName() + "Resolved";
            ueiToClear = String.format("%s/%s", ueiPrefix, def.getEventName());
        }

        Event e = new Event();
        Mask mask = new Mask();
        e.setMask(mask);
        addMaskElementWithValue(mask, "id", formatEnterprise(def.getEnterprise()));
        addMaskElementWithValue(mask, "generic", def.getTrapNumber());
        addMaskElementWithValue(mask, "specific", def.getSpecific());

        e.setUei(String.format("%s/%s", ueiPrefix, eventName));
        e.setEventLabel("SMARTS: " + eventName);
        e.setDescr(replaceVariables(def.getEventText()));

        Logmsg logmsg = new Logmsg();
        logmsg.setDest(LogDestType.LOGNDISPLAY);
        logmsg.setContent(replaceVariables(def.getEventText()));
        if (Strings.isNullOrEmpty(logmsg.getContent())) {
            LOG.warn("No log message for {}.", def.getEventName());
        }
        e.setLogmsg(logmsg);

        e.setSeverity(getSeverity(def));

        if (alarmType != null) {
            AlarmData alarmData = new AlarmData();
            alarmData.setAlarmType(alarmType);
            alarmData.setReductionKey("%uei%:%dpname%:%nodeid%:" + toReductionKeyPart(def.getInstanceName()));
            alarmData.setAutoClean(false);
            if (alarmType == 2) {
                e.setSeverity("Normal");
                alarmData.setClearKey(ueiToClear + ":%dpname%:%nodeid%:" + toReductionKeyPart(def.getInstanceName()));
            }
            e.setAlarmData(alarmData);
        }

        if (def.getClassName() != null) {
            Parameter classNameParm = new Parameter();
            classNameParm.setName("className");
            classNameParm.setValue(def.getClassName());
            classNameParm.setExpand(false);
            e.getParameters().add(classNameParm);
        }

        if (varbindIndex != null && varbindValue != null) {
            Varbind varbind = new Varbind();
            varbind.setVbnumber(varbindIndex);
            varbind.setVbvalues(Collections.singletonList(varbindValue));
            mask.getVarbinds().add(varbind);
        }

        for (Map.Entry<String, Map<String, String>> entry : def.getMap().entrySet()) {
            final String key = entry.getKey();
            final Map<String, String> mappings = entry.getValue();

            final String parmId = toParmId(key);
            Varbindsdecode varbindsDecode = new Varbindsdecode();
            varbindsDecode.setParmid(parmId);
            for (Map.Entry<String, String> decodeMapping : mappings.entrySet()) {
                Decode decode = new Decode();
                decode.setVarbindvalue(decodeMapping.getKey());
                decode.setVarbinddecodedstring(decodeMapping.getValue());
                varbindsDecode.getDecodes().add(decode);
            }
            e.getVarbindsdecodes().add(varbindsDecode);
        }

        return e;
    }

    protected static String toReductionKeyPart(String val) {
        final String replacedVal = replaceVariables(val);
        if (replacedVal == null) {
            return "";
        } else {
            return replacedVal.replaceAll("<->", ":")
                    .replaceAll("/", ":");
        }
    }

    protected static String replaceVariables(String val) {
        if (val == null) {
            return null;
        }

        /*
        # SRC           Source address of device sending the trap
        # A             Address of agent sending the trap
        # C             Community string of the trap (NOTE: trap adapter must be
        #               started with the --community switch in order to use this
        #               variable.)
        # SYS           Name of the system sending the trap
        # T             Time the trap was sent
        # E             Enterprise OID
        # N             Trap number
        # S             Specific number
        # Vn            Variable n, where n is a number, e.g. $V1$ for the first
        #               varbind. $V*$ matches all varbinds.
        # OIDn          Variable n, where n is a number, e.g. $OID1$ for the first
        #               Oid. $OID*$ matches all Oids.
        */
        final StringBuffer sb = new StringBuffer();
        final Matcher m = s_variableSubsitutionPattern.matcher(val);
        while (m.find()) {
            final String token = m.group(1);
            final String parmIdFromToken = toParmId(token);
            final String replacement;
            if (!Objects.equals(token, parmIdFromToken)) {
                replacement = "%" + parmIdFromToken + "%";
            } else {
                replacement = token;
            }
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    protected static String toParmId(String val) {
        Matcher mm = s_variablePattern.matcher(val);
        if (mm.matches()) {
            final String index = "*".equals(mm.group(1)) ? "all" : "#" + mm.group(1);
            return "parm[" + index + "]";
        }

        mm = s_oidPattern.matcher(val);
        if (mm.matches()) {
            final String index = "*".equals(mm.group(1)) ?  "s-all" : "-#" + mm.group(1);
            return "parm[name" + index + "]";
        }

        switch(val) {
            case "SRC":
            case "A":
            case "SYS":
                return "interface";
            case "C":
                return "community";
            case "T":
                return "time";
            case "S":
                return "specific";
            case "N":
                return "generic";
            case "E":
                return "id";
        }
        return val;
    }

    protected static String formatEnterprise(String enterprise) {
        if (enterprise.endsWith(".*")) {
            return enterprise.substring(0, enterprise.length() - 2) + "%";
        } else if (enterprise.endsWith("*")) {
            return enterprise.substring(0, enterprise.length() - 1) + "%";
        } else {
            return enterprise;
        }
    }

    private static String getSeverity(SmartsTrapDef def) {
        switch (def.getSeverity()) {
        case 1:
            return "Critical";
        case 2:
            return "Major";
        case 3:
            return "Minor";
        case 4:
            return "Warning";
        case 5:
            return "Normal";
        default:
            return "Indeterminate";
        }
    }

    private static void addMaskElementWithValue(Mask mask, String mename, String... values) {
        Maskelement eid = new Maskelement();
        eid.setMename(mename);
        eid.setMevalues(Lists.newArrayList(values));
        mask.addMaskelement(eid);
    }

}
