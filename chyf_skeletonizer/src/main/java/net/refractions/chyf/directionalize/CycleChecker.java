package net.refractions.chyf.directionalize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureReader;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.directionalize.graph.DEdge;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.EdgeInfo;

/**
 * Simple cycle checker for chyf  flowpath dataset
 * 
 * @author Emily
 *
 */
public class CycleChecker {

	
	public boolean checkCycles(Path output) throws Exception{
		List<EdgeInfo> edges = new ArrayList<>();

		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			
			try(SimpleFeatureReader reader = dataSource.getFlowpaths(null)){
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					
					LineString ls = ChyfDataSource.getLineString(sf);
					
					EfType eftype = EfType.parseType((Integer)sf.getAttribute("ef_type"));					
					DirectionType dtype = DirectionType.KNOWN;
					
					EdgeInfo ei = new EdgeInfo(ls.getCoordinateN(0), ls.getCoordinateN(ls.getCoordinates().length - 1),
							eftype,
							sf.getIdentifier(), ls.getLength(), 
							dtype);
					
					edges.add(ei);
				}
			}
		}
					
		DGraph graph = DGraph.buildGraphLines(edges);
		return findCycles(graph);
	}
	
	
	public boolean findCycles(DGraph graph) {
		graph.getEdges().forEach(e->e.setVisited(false));
		for(DEdge f : graph.getEdges()) {
			if(findCycles(f, new HashSet<DEdge>())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean findCycles(DEdge f, HashSet<DEdge> visited) {
		if (f.isVisited()) return false;
		
		if(visited.contains(f)) {
			System.out.println("Cycle found @" + f.toString());
			return true;
		}
		visited.add(f);
		for(DEdge u: f.getNodeA().getEdges()) {
			if (u.getNodeB() == f.getNodeA())
			if(findCycles(u, visited)) {
				return true;
			}
		}
		f.setVisited(true);
		visited.remove(f);
		return false;
	}
}
