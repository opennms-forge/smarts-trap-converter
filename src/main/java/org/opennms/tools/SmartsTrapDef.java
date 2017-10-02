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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A trap definition as defined in Smarts.
 */
public class SmartsTrapDef {

    private String enterprise;
    private String trapNumber;
    private String specific;
    
    private String className;
    private String elementName;
    private String instanceName;
    private String eventName;
    private int severity;
    private String eventText;
    private String state;
    private String eventType;
    private String unknownAgent;
    private String clearOnAcknowledge;
    private String expiration;
    private String logFile;
    private Map<String, Map<String, String>> map = new LinkedHashMap<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getEventText() {
        return eventText;
    }

    public void setEventText(String eventText) {
        this.eventText = eventText;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUnknownAgent() {
        return unknownAgent;
    }

    public void setUnknownAgent(String unknownAgent) {
        this.unknownAgent = unknownAgent;
    }

    public String getClearOnAcknowledge() {
        return clearOnAcknowledge;
    }

    public void setClearOnAcknowledge(String clearOnAcknowledge) {
        this.clearOnAcknowledge = clearOnAcknowledge;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(String enterprise) {
        this.enterprise = enterprise;
    }

    public String getTrapNumber() {
        return trapNumber;
    }

    public void setTrapNumber(String trapNumber) {
        this.trapNumber = trapNumber;
    }

    public String getSpecific() {
        return specific;
    }

    public void setSpecific(String specific) {
        this.specific = specific;
    }

    public Map<String, Map<String, String>> getMap() {
        return map;
    }

    public void setMap(Map<String, Map<String, String>> map) {
        this.map = map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartsTrapDef that = (SmartsTrapDef) o;
        return severity == that.severity &&
                Objects.equals(enterprise, that.enterprise) &&
                Objects.equals(trapNumber, that.trapNumber) &&
                Objects.equals(specific, that.specific) &&
                Objects.equals(className, that.className) &&
                Objects.equals(elementName, that.elementName) &&
                Objects.equals(instanceName, that.instanceName) &&
                Objects.equals(eventName, that.eventName) &&
                Objects.equals(eventText, that.eventText) &&
                Objects.equals(state, that.state) &&
                Objects.equals(eventType, that.eventType) &&
                Objects.equals(unknownAgent, that.unknownAgent) &&
                Objects.equals(clearOnAcknowledge, that.clearOnAcknowledge) &&
                Objects.equals(expiration, that.expiration) &&
                Objects.equals(logFile, that.logFile) &&
                Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enterprise, trapNumber, specific, className, elementName, instanceName, eventName, severity, eventText, state, eventType, unknownAgent, clearOnAcknowledge, expiration, logFile, map);
    }

    @Override
    public String toString() {
        return "SmartsTrapDef{" +
                "enterprise='" + enterprise + '\'' +
                ", trapNumber='" + trapNumber + '\'' +
                ", specific='" + specific + '\'' +
                ", className='" + className + '\'' +
                ", elementName='" + elementName + '\'' +
                ", instanceName='" + instanceName + '\'' +
                ", eventName='" + eventName + '\'' +
                ", severity=" + severity +
                ", eventText='" + eventText + '\'' +
                ", state='" + state + '\'' +
                ", eventType='" + eventType + '\'' +
                ", unknownAgent='" + unknownAgent + '\'' +
                ", clearOnAcknowledge='" + clearOnAcknowledge + '\'' +
                ", expiration='" + expiration + '\'' +
                ", logFile='" + logFile + '\'' +
                ", map=" + map +
                '}';
    }
}
