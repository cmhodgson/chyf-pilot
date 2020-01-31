package net.refractions.chyf.directionalize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.geotools.data.simple.SimpleFeatureReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.Args;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.Attribute;
import net.refractions.chyf.datasource.ChyfDataSource.DirectionType;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfDataSource.IoType;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.directionalize.graph.DEdge;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.DNode;
import net.refractions.chyf.directionalize.graph.EdgeInfo;

public class DirectionalizeEngine {
	
	static final Logger logger = LoggerFactory.getLogger(DirectionalizeEngine.class.getCanonicalName());

	public static void doWork(Path output) throws Exception {

		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			List<EdgeInfo> edges = new ArrayList<>();
			List<EdgeInfo> banks = new ArrayList<>();

			List<Polygon> aois = dataSource.getAoi();

			logger.info("loading flowpaths");
			try(SimpleFeatureReader reader = dataSource.getFlowpaths(null)){
				
				Name eftypeatt = ChyfDataSource.findAttribute(reader.getFeatureType(), Attribute.EFTYPE);
				Name direatt = ChyfDataSource.findAttribute(reader.getFeatureType(), Attribute.DIRECTION);
				
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					LineString ls = ChyfDataSource.getLineString(sf);
					
					for (Polygon p : aois) {
						if (p.relate(ls, "1********")) {
							EfType eftype = EfType.parseType((Integer)sf.getAttribute(eftypeatt));
							DirectionType dtype = DirectionType.parseType((Integer)sf.getAttribute(direatt));
							EdgeInfo ei = new EdgeInfo(ls.getCoordinateN(0), 
									ls.getCoordinateN(ls.getCoordinates().length - 1),
									eftype,
									sf.getIdentifier(), ls.getLength(), 
									dtype);

							if (eftype == EfType.BANK) {
								banks.add(ei); //exclude these from the main graph
							}else {
								edges.add(ei);	
							}		
							break;
						}
					}
				}
			}
			
			//create graph
			logger.info("build graph");
			DGraph graph = DGraph.buildGraphLines(edges);
			
			//directionalized bank edges
			logger.info("processing bank edges");
			HashSet<Coordinate> nodec = new HashSet<>();
			for(DNode d : graph.nodes) nodec.add(d.getCoordinate());
			Set<FeatureId> bankstoflip = new HashSet<>();
			for (EdgeInfo b : banks) {
				if (nodec.contains(b.getEnd())) {
				}else if (nodec.contains(b.getStart())) {
					//flip
					bankstoflip.add(b.getFeatureId());
				}else {
					throw new Exception("Bank flowpath does not intersect flow network: " + b.getStart());
				}
			}
			
			dataSource.flipFlowEdges(bankstoflip, banks.stream().map(e->e.getFeatureId()).collect(Collectors.toList()));
			
			//find sink nodes
			logger.info("locating sink nodes");
			List<Coordinate> sinks = getSinkPoints(dataSource, graph);

			//directionalize dataset
			logger.info("directionalizing network");
			Directionalizer dd = new Directionalizer();
			dd.directionalize(graph, sinks);
			
			//flip edges
			logger.info("saving results");
			dataSource.flipFlowEdges(dd.getFeaturesToFlip(), dd.getProcessedFeatures());
		}
			
		logger.info("checking output from cycles");
		CycleChecker checker = new CycleChecker();
		if (checker.checkCycles(output)) {
			logger.error("Output network contains cycles");
		}
		

	}
	

	/*
	 * Compute sinks points as :
	 * - any boundary points with flowdirection of out
	 * - any known direction edges that form a sink
	 * - and flow edge that intersects the coastline
	 * 
	 */
	private static List<Coordinate> getSinkPoints(ChyfGeoPackageDataSource source, DGraph graph) throws Exception {
		List<Coordinate> sinks = new ArrayList<>();
		for (Point p : source.getBoundaries()) {
			if ( ((IoType)p.getUserData()) == IoType.OUTPUT ) {
				if (!sinks.contains(p.getCoordinate())) sinks.add(p.getCoordinate());
			}
		}
		
		//add coastline sinks
		Set<Coordinate> clc = new HashSet<>();
		for (LineString ls : source.getCoastline()) {
			for (Coordinate c : ls.getCoordinates()) clc.add(c);
		
		}
		
		//now lets search the graph for sink points based on known direction
		for (DNode node : graph.getNodes()) {
			boolean issink = true;
			if (clc.contains(node.getCoordinate())) {
				//sink node
			}else {
				//if all in edges are directionalized && all sink at this node
				for (DEdge e : node.getEdges()) {
					if (e.getDType() == DirectionType.UNKNOWN) {
						issink = false;
						break;
					}else if (e.getNodeB() != node) {
						issink = false;
						break;
					}
				}
			}
			if (issink && !sinks.contains(node.getCoordinate())) sinks.add(node.getCoordinate());
		}
		
		return sinks;
	}
	
	public static void main(String[] args) throws Exception {		
		Args runtime = Args.parseArguments(args);
		if (runtime == null) {
			Args.printUsage("DirectionalizeEngine");
			return;
		}
		runtime.prepareOutput();
		
		
		long now = System.nanoTime();
		DirectionalizeEngine.doWork(runtime.getOutput());
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}