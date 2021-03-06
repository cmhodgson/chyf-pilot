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
package net.refractions.chyf.flowpathconstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.directionalize.DirectionalizeEngine;
import net.refractions.chyf.flowpathconstructor.rank.RankEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PointEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonEngine;

/**
 * Main class for running all flowpath constructor components
 * 
 * @author Emily
 *
 */
public class FlowpathConstructor {

	static final Logger logger = LoggerFactory.getLogger(FlowpathConstructor.class.getCanonicalName());

	
	public static void main(String[] args) throws Exception {		
		FlowpathArgs runtime = new FlowpathArgs("FlowpathConstructor");
		if (!runtime.parseArguments(args)) return;
		
		runtime.prepareOutput();
		
		long now = System.nanoTime();
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(runtime.getOutput())){
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			logger.info("Generating Constructions Points");
			PointEngine.doWork(dataSource, prop);
			logger.info("Generating Skeletons");
			SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
			logger.info("Directionalizing Dataset");
			DirectionalizeEngine.doWork(dataSource, prop);
			logger.info("Computing Rank");
			RankEngine.doWork(dataSource, prop);
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
	
}
