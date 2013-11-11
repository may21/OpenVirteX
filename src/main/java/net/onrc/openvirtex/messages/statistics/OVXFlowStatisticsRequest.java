/*******************************************************************************
 * Copyright (c) 2013 Open Networking Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 ******************************************************************************/


package net.onrc.openvirtex.messages.statistics;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.onrc.openvirtex.elements.datapath.OVXSingleSwitch;
import net.onrc.openvirtex.elements.datapath.OVXSwitch;
import net.onrc.openvirtex.elements.datapath.PhysicalSwitch;
import net.onrc.openvirtex.elements.port.OVXPort;
import net.onrc.openvirtex.exceptions.SwitchMappingException;
import net.onrc.openvirtex.messages.OVXFlowMod;
import net.onrc.openvirtex.messages.OVXStatisticsReply;
import net.onrc.openvirtex.messages.OVXStatisticsRequest;
import net.onrc.openvirtex.messages.actions.OVXActionOutput;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.U16;

public class OVXFlowStatisticsRequest extends OFFlowStatisticsRequest implements
		DevirtualizableStatistic {

	Logger log = LogManager.getLogger(OVXFlowStatisticsRequest.class.getName());
	
	@Override
	public void devirtualizeStatistic(final OVXSwitch sw,
			final OVXStatisticsRequest msg) {
		List<OVXFlowStatisticsReply> replies = new LinkedList<OVXFlowStatisticsReply>();
		HashSet<Long> uniqueCookies = new HashSet<Long>();
		int tid = sw.getTenantId();
		int length = 0;
	
		if ((this.match.getWildcardObj().isFull() || this.match.getWildcards() == -1) // the -1 is for beacon...
				&& this.outPort == OFPort.OFPP_NONE.getValue()) {
			for (PhysicalSwitch psw : getPhysicalSwitches(sw)) {
				List<OVXFlowStatisticsReply> reps = psw.getFlowStats(tid);
				if (reps != null) {
					for (OVXFlowStatisticsReply stat : reps) {
						
						if (!uniqueCookies.contains(stat.getCookie())) {
							OVXFlowMod origFM = sw.getFlowMod(stat.getCookie());
							stat.setCookie(origFM.getCookie());
							stat.setMatch(origFM.getMatch());
							stat.setActions(origFM.getActions());
							uniqueCookies.add(stat.getCookie());
							replies.add(stat);
							stat.setLength(U16.t(OVXFlowStatisticsReply.MINIMUM_LENGTH));
							for (OFAction act : stat.getActions()) {
								stat.setLength(U16.t(stat.getLength() + act.getLength()));
							}
							length += stat.getLength();
						}
					}
					
				}
			}
			
			if (replies.size() > 0) {
				OVXStatisticsReply reply = new OVXStatisticsReply();
				reply.setXid(msg.getXid());
				reply.setStatisticType(OFStatisticsType.FLOW);
				reply.setStatistics(replies);
				
				reply.setLengthU(OVXStatisticsReply.MINIMUM_LENGTH + length);
				
				sw.sendMsg(reply, sw);
			}
		}
	}

	private List<PhysicalSwitch> getPhysicalSwitches(OVXSwitch sw) {
		if (sw instanceof OVXSingleSwitch)
			try {
				return sw.getMap().getPhysicalSwitches(sw);
			} catch (SwitchMappingException e) {
				log.debug("OVXSwitch {} does not map to any physical switches", sw.getSwitchName());
				return new LinkedList<>();
			}
		LinkedList<PhysicalSwitch> sws = new LinkedList<PhysicalSwitch>();
		for (OVXPort p : sw.getPorts().values())
			if (!sws.contains(p.getPhysicalPort().getParentSwitch()))
				sws.add(p.getPhysicalPort().getParentSwitch());
		return sws;
	}

}
