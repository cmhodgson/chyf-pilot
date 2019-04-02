/*
 * Copyright 2019 Government of Canada
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package net.refractions.chyf;

import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import net.refractions.chyf.enumTypes.CatchmentType;
import net.refractions.chyf.enumTypes.FlowpathType;
import net.refractions.chyf.enumTypes.Rank;
import net.refractions.chyf.hygraph.ECatchment;
import net.refractions.chyf.hygraph.HyGraphBuilder;
import net.refractions.chyf.rest.GeotoolsGeometryReprojector;
import net.refractions.util.UuidUtil;
import nrcan.cccmeo.chyf.db.Catchment;
import nrcan.cccmeo.chyf.db.CatchmentDAO;
import nrcan.cccmeo.chyf.db.Flowpath;
import nrcan.cccmeo.chyf.db.FlowpathDAO;
import nrcan.cccmeo.chyf.db.SpringJdbcConfiguration;
import nrcan.cccmeo.chyf.db.Waterbody;
import nrcan.cccmeo.chyf.db.WaterbodyDAO;

/**
 * Reads source data from database.
 * 
 * @author Emily
 * @author Mark Lague
 *
 */
//TODO: this reader needs to be updated to read the boundaries from the
//postgresql database - see the end of the read function.
public class ChyfPostgresqlReader extends ChyfDataReader{

	public ChyfPostgresqlReader() {
		
	}
	
	public void read(HyGraphBuilder gb ) throws Exception{
	
		try {
			// SRID should be the same as the database's data
			//GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(1000), 3978);
			GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(100_000_00.0), 4617);
			
			@SuppressWarnings("resource")
			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringJdbcConfiguration.class); //ML
			WKTReader wktreader = new WKTReader();

			// read and add Waterbodies
			logger.info("Reading waterbodies");
		    WaterbodyDAO waterbodyDAO = (WaterbodyDAO) context.getBean(WaterbodyDAO.class);
			    
		    for(Waterbody wb : waterbodyDAO.getWaterbodies()) {
		    	Geometry waterCatchment = GEOMETRY_FACTORY.createGeometry(wktreader.read(wb.getLinestring()));
		    	Polygon wCatchment = null;
		    	if (waterCatchment instanceof Polygon) {
					wCatchment = (Polygon)waterCatchment;
				}else if (waterCatchment instanceof MultiPolygon) {
					wCatchment = (Polygon) ((MultiPolygon)waterCatchment).getGeometryN(0);
				}
		    	CoordinateReferenceSystem wCatchmentCRS = GeotoolsGeometryReprojector.srsCodeToCRS(waterCatchment.getSRID());
		    	wCatchment = (Polygon) GeotoolsGeometryReprojector.reproject(wCatchment, wCatchmentCRS, ChyfDatastore.BASE_CRS);
			    CatchmentType type = CatchmentType.UNKNOWN;
			    Object def = wb.getDefinition();
			    Integer intValue = -1;
				if (def instanceof Integer) {
					intValue = (Integer) def;
				}else if (def instanceof Long) {
					intValue = ((Long)def).intValue();
				}
			    switch(intValue) {
			        case 1:
			            type = CatchmentType.WATER_CANAL;
			            break;
			        case 4:
			           	type = CatchmentType.WATER_LAKE;
			           	break;
			        case 6: 
			           	type = CatchmentType.WATER_RIVER;
			           	break;
			        case 9:
			           	type = CatchmentType.WATER_POND;
			           	break;
			    }
			    double area = (double) wb.getArea();
			    
			    wCatchment = (Polygon) GeometryPrecisionReducer.reduce(wCatchment, ChyfDatastore.PRECISION_MODEL);
			    gb.addECatchment(type, area, wCatchment);
			    }

			// read and add Catchments
			logger.info("Reading catchments");
			CatchmentDAO catchmentDAO = (CatchmentDAO) context.getBean(CatchmentDAO.class);
			
			boolean[] hasAttribute = new boolean[ECatchment.ECatchmentStat.values().length];
			for (int i = 0; i < hasAttribute.length; i ++) hasAttribute[i] = false;
			
			List<Catchment> catchments = catchmentDAO.getCatchments(); 
			for (Catchment c : catchments) {
				Geometry catchment = GEOMETRY_FACTORY.createGeometry(wktreader.read(c.getLinestring()));
				Polygon cCatchment = null;
				if (catchment instanceof Polygon) {
					cCatchment = (Polygon)catchment;
				}else if (catchment instanceof MultiPolygon) {
					cCatchment = (Polygon) ((MultiPolygon)catchment).getGeometryN(0);
				}
				double area = c.getArea();
				CoordinateReferenceSystem cCatchmentCRS = GeotoolsGeometryReprojector.srsCodeToCRS(catchment.getSRID());
				cCatchment = GeotoolsGeometryReprojector.reproject(cCatchment, cCatchmentCRS, ChyfDatastore.BASE_CRS);
				cCatchment = (Polygon) GeometryPrecisionReducer.reduce(cCatchment, ChyfDatastore.PRECISION_MODEL);
				
			    ECatchment newCatchment = gb.addECatchment(CatchmentType.UNKNOWN, area, cCatchment);
			    if (newCatchment != null) {
					//statistic attributes if applicable
					for (int i = 0; i < hasAttribute.length; i ++) {
						ECatchment.ECatchmentStat stat = ECatchment.ECatchmentStat.values()[i];
						Double x = (Double)c.getAttribute(stat.getFieldName().toLowerCase(), c);
						if (x != null || !(Double.isNaN(x))) {
							stat.updateCatchment(newCatchment, x);
						}
					}
				}
			}

			// read and add Flowpaths
			logger.info("Reading flowpaths");
			FlowpathDAO flow = (FlowpathDAO) context.getBean(FlowpathDAO.class);
			
			List<Flowpath> flowpaths = flow.getFlowpaths();
			for (Flowpath fp : flowpaths){
				Geometry flowPath = GEOMETRY_FACTORY.createGeometry(wktreader.read(fp.getLinestring()));
				LineString flowP = null;
				if (flowPath instanceof LineString) {
					flowP = (LineString)flowPath;
				}else if (flowPath instanceof MultiLineString) {
					flowP = (LineString) ((MultiLineString)flowPath).getGeometryN(0);
				}
				CoordinateReferenceSystem flowPathCRS = GeotoolsGeometryReprojector.srsCodeToCRS(flowPath.getSRID());
				flowP = GeotoolsGeometryReprojector.reproject(flowP, flowPathCRS, ChyfDatastore.BASE_CRS);
				FlowpathType type = FlowpathType.convert(fp.getType());
				String rankString = fp.getRank();
			    Rank rank = Rank.convert(rankString);
			    String name = fp.getName().intern();
			    double length = fp.getLength();
			    UUID nameId = null;
			    try {
			    	nameId = UuidUtil.UuidFromString(fp.getNameId());
			    } catch(IllegalArgumentException iae) {
			    	logger.warn("Exception reading UUID: " + iae.getMessage());
			    }
			    CoordinateReferenceSystem flowpathCRS = GeotoolsGeometryReprojector.srsCodeToCRS(flowPath.getSRID());
			    flowP = (LineString) GeometryPrecisionReducer.reduce(flowP, ChyfDatastore.PRECISION_MODEL);
			    gb.addEFlowpath(type, rank, name, nameId, length, (LineString)flowP);
				
			}
			
			//TODO: the boundaries arraylist here should be updated to read the
			//boundary information from the database.  This is necessary to 
			//correctly assign hack order to the stream edges.  Without this
			//the hack order will be incorrect.  This array list
			//should be a list of geometries representing the boundary
			//of the dataset.  The assumption is that the boundary geometries
			//have exact same coordinate as the flowline where
			//the boundary meets the flowline
			
			//example: this.boundaries.add(e)
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
