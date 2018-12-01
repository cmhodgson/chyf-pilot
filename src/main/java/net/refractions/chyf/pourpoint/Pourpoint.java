package net.refractions.chyf.pourpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

import net.refractions.chyf.enumTypes.FlowpathType;
import net.refractions.chyf.enumTypes.NexusType;
import net.refractions.chyf.hygraph.ECatchment;
import net.refractions.chyf.hygraph.EFlowpath;
import net.refractions.chyf.hygraph.HyGraph;
import net.refractions.chyf.hygraph.Nexus;

public class Pourpoint {
	
	public enum CType{
		NEAREST_INCATCHMENT(-2),
		NEAREST_FLOWPATH(-1),
		
		NEAREST_NEXUS_ALL(0),
		NEAREST_NEXUS_SINGLE(1);
		
		public int ccode;
		
		CType(int ccode){
			this.ccode = ccode;
		}
	}
	
	private Point location;
	private int ccode;
	private CType type;
	
	private List<EFlowpath> downstreamFlowpaths = null;
	
	private Set<Pourpoint> downstreamPoints = null;
	private Set<Pourpoint> upstreamPoints = null;
	
	private Set<ECatchment> uniqueCatchments;
	private Set<ECatchment> sharedCatchments;
	
	private String id;
	
	
	/**
	 * 
	 * @param location the point is assumed to be in the hygraph working projection
	 * @param ccode
	 */
	public Pourpoint(Point location, int ccode, String id) {
		this.id = id;
		this.location = location;
		this.ccode = ccode;
		this.downstreamPoints = new HashSet<>();
		this.upstreamPoints = new HashSet<>();
		if (ccode == -2) {
			type = CType.NEAREST_INCATCHMENT;
		}else if (ccode == -1) {
			type = CType.NEAREST_FLOWPATH;
		}else if (ccode == 0) {
			type = CType.NEAREST_NEXUS_ALL;
		}else if (ccode > 0) {
			type = CType.NEAREST_NEXUS_SINGLE;
		}else {
			throw new IllegalStateException("Invalid ccode value");
		}
		
		uniqueCatchments = new HashSet<>();
		sharedCatchments = new HashSet<>();
	}
	
	public void addUpstreamCatchments(Collection<ECatchment> uniqueCatchments, Collection<ECatchment> sharedCatchments) {
		this.uniqueCatchments.addAll(uniqueCatchments);
		this.sharedCatchments.addAll(sharedCatchments);
	}
	
	public Set<ECatchment> getUniqueCatchments(){
		return this.uniqueCatchments;
	}
	
	public Set<ECatchment> getSharedCatchments(){
		return this.sharedCatchments;
	}
	
	public String getId() {
		return id;
	}
	public Set<Pourpoint> getDownstreamPourpoints(){
		return this.downstreamPoints;
	}
	public Set<Pourpoint> getUpstreamPourpoints(){
		return this.upstreamPoints;
	}
	
	public Point getProjectedPoint() {
		return this.downstreamFlowpaths.get(0).getToNode().getPoint();
	}
	
	public Point getPoint() {
		return this.location;
	}
	
	public List<EFlowpath> getDownstreamFlowpaths(){
		return this.downstreamFlowpaths;
	}
	
	/**
	 * Finds the most downstream flowpath edges
	 * based code on of the point
	 * 
	 * @param graph
	 */
	public void findDownstreamFlowpaths(HyGraph graph) {
		downstreamFlowpaths = new ArrayList<>();
		if (this.type == CType.NEAREST_INCATCHMENT) {
			//find the elementary catchment and 
			//then find the most downstream
			//edge in that catchment
			
			List<ECatchment> catchments = graph.findECatchments(location, 1, 0, null);
			if (catchments.isEmpty() || catchments.size() > 1) {
				//TODO: some sort of error
				return;
			}
			ECatchment c = catchments.get(0);
			EFlowpath downstream = null;
			//find the flowpath "nearest" to 
			double distance = Double.MAX_VALUE;
			
			EFlowpath closest = null;
			for (EFlowpath p : c.getFlowpaths()) {
				double tdistance = p.getToNode().getPoint().getCoordinate().distance(location.getCoordinate());
				if (tdistance < distance) {
					closest = p;
				}
			}
			if (closest != null) {
				for (EFlowpath p : closest.getToNode().getUpFlows()) {
					if (p.getCatchment() == c) {
						downstreamFlowpaths.add(p);
					}
				}
				
			}
		}else if (this.type == CType.NEAREST_FLOWPATH) {
			//The intended pourpoint is the nearest point on the nearest flowpath 
			//(using Euclidean distance in both cases) to the input pourpoint.
			//find the nearest flowpath
			List<EFlowpath> nearestPaths = graph.findEFlowpaths(location, 1, null, e->e.getType() != FlowpathType.BANK);
			if (nearestPaths.isEmpty()) {
				//TODO: error
			}else {
				downstreamFlowpaths.add(nearestPaths.get(0));
			}
			
		}else if (this.type == CType.NEAREST_NEXUS_ALL) {
			//the closest hydro nexus is the intended pourpoint, and 
			//the intended catchment includes all
			//areas that drain into that nexus. If two streams drain into that nexus, 
			//then the catchment of interest includes the catchments of both streams.
			
			List<Nexus> nearestNexus = graph.findNexuses(location, 1, null, e->e.getType() != NexusType.BANK);
			if (nearestNexus.isEmpty()) {
				//TODO: error
			}else {
				nearestNexus.get(0).getUpFlows()
					.stream()
					.filter(f->f.getType() != FlowpathType.BANK)
					.forEach(f->downstreamFlowpaths.add(f));				
			}
		}else if (this.type == CType.NEAREST_NEXUS_SINGLE) {
			//The closest hydro nexus is the intended pourpoint, and 
			//which of the inflowing catchments is of interest. 
			//1 means that the catchment of interest is the catchment for the first 
			//inflowing flowpath, 2 means that the intended catchment is the 
			//second inflowing flowpath, and so on. Values greater than 2 are possible but rare. 
			//The positive c-code value corresponds to the order in which 
			//the flowpath is encountered, when rotating around the nexus in a 
			//clockwise direction from the outflowing flowpath.
			List<Nexus> nearestNexus = graph.findNexuses(location, 1, null, e->e.getType() != NexusType.BANK);
			if (nearestNexus.isEmpty()) {
				//TODO: error
			}else {
				//first filter out bank flows
				List<EFlowpath> temp = new ArrayList<>();
				nearestNexus.get(0).getUpFlows()
					.stream()
					.filter(f->f.getType() != FlowpathType.BANK)
					.forEach(f->temp.add(f));
				
				if (this.ccode > temp.size()) {
					//TODO: error
				}
				if (temp.size() == 1 && this.ccode == 1) {
					//only one to return so we are 
					downstreamFlowpaths.add(temp.get(0));
				}else {
					if (nearestNexus.get(0).getDownFlows().isEmpty()) {
						//TODO: error
					}
					
					//sort outputs by type
					EFlowpath primaryOutput = nearestNexus.get(0).getDownFlows().get(0);
					if (nearestNexus.get(0).getDownFlows().size() > 1) {
						List<EFlowpath> sortedOuts = new ArrayList<>(nearestNexus.get(0).getDownFlows());
						sortedOuts.sort((a,b)->Integer.compare(a.getRank().ordinal(), b.getRank().ordinal()));
						primaryOutput = sortedOuts.get(0);
					}
					
					Coordinate cOutflow = primaryOutput.getLineString().getCoordinateN(1);
					Coordinate cNexus = nearestNexus.get(0).getPoint().getCoordinate();
					
					//sort angles on value
					SortedMap<Double,EFlowpath> angles = new TreeMap<Double, EFlowpath>();
					for (EFlowpath path : temp) {
						Coordinate cInflow = path.getLineString().getCoordinateN(1);
						double angle = Angle.angleBetweenOriented(cOutflow, cNexus, cInflow);
						if (angle > 0)  angle = angle - Angle.PI_TIMES_2;
						angles.put(-angle, path);
					}
				
					Iterator<EFlowpath> it = angles.values().iterator();
					EFlowpath item = it.next();
					int i = 1;
					while(i < ccode) { i++; item = it.next(); }
					downstreamFlowpaths.add(item);
					
				}
			}
		}
		
	}
}